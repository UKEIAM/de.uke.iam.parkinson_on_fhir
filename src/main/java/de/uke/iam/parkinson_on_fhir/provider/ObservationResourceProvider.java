package de.uke.iam.parkinson_on_fhir.provider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record7;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.jooq.Condition;
import org.jooq.Cursor;

import static de.uke.iam.parkinson_on_fhir.database.Tables.*;
import de.uke.iam.parkinson_on_fhir.database.tables.records.MeasurementsRecord;

public class ObservationResourceProvider implements IResourceProvider {

    private DSLContext connection;

    private static final List<CodeableConcept> ACCELERATION_CATEGORY;
    private static final AccelerationComponent[] ACCELERATION_COMPONENTS;
    static {
        ACCELERATION_CATEGORY = Arrays.asList(new CodeableConcept().setCoding(Arrays
                .asList(new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "procedure",
                        "Procedure"))));

        ACCELERATION_COMPONENTS = new AccelerationComponent[3];
        ACCELERATION_COMPONENTS[0] = new AccelerationComponent(MEASUREMENTS.X, "X42", "Acceleration on the X axis");
        ACCELERATION_COMPONENTS[1] = new AccelerationComponent(MEASUREMENTS.Y, "X43", "Acceleration on the Y axis");
        ACCELERATION_COMPONENTS[2] = new AccelerationComponent(MEASUREMENTS.Z, "X44", "Acceleration on the Z axis");
    }

    /**
     * A utility class allowing easy creation of ObservationComponentComponents for
     * acceleration data.
     */
    private static class AccelerationComponent {
        private CodeableConcept concept;
        private TableField<MeasurementsRecord, Float> tableEntry;

        public AccelerationComponent(TableField<MeasurementsRecord, Float> tableEntry, String loinc_code,
                String description) {
            this.concept = new CodeableConcept();
            this.concept.addCoding(new Coding("LOINC", loinc_code, description));
            this.tableEntry = tableEntry;
        }

        public ObservationComponentComponent createObservationComponent(Record record) {
            var value = new ObservationComponentComponent(this.concept);
            value.setValue(new Quantity(record.get(this.tableEntry)));
            return value;
        }
    }

    /**
     * An fetched set of acceleration measurements.
     */
    private static class FetchedObservations implements IBundleProvider {
        private final int numMeasurements;
        private final InstantType searchTime;

        private Cursor<Record7<LocalDateTime, Integer, Float, Float, Float, String, String>> measurements;
        private int lastIndex;

        private final static TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
        private final static int FETCH_SIZE = 256;

        public FetchedObservations(DSLContext connection, Condition where) {
            this.searchTime = InstantType.withCurrentTime();

            this.numMeasurements = connection.selectCount().from(MEASUREMENTS).where(where).fetchOne(0,
                    int.class);
            this.measurements = connection
                    .select(MEASUREMENTS.TIMESTAMP, MEASUREMENTS.SUBJECT, MEASUREMENTS.X, MEASUREMENTS.Y,
                            MEASUREMENTS.Z, SENSORS.BODY_PART, SENSORS.DEVICE)
                    .from(MEASUREMENTS).join(SENSORS)
                    .on(MEASUREMENTS.SENSOR.eq(SENSORS.SENSOR_ID)).where(where).fetchSize(FETCH_SIZE).fetchLazy();
        }

        @Override
        public IPrimitiveType<Date> getPublished() {
            return this.searchTime;
        }

        @Override
        public List<IBaseResource> getResources(int theFromIndex, int theToIndex) {
            var num_samples = theToIndex - theFromIndex;
            if (num_samples <= 0) {
                return new ArrayList<IBaseResource>();
            }

            // By now, we do not support random access into the observations.
            if (lastIndex != theFromIndex) {
                throw new NotImplementedOperationException("Random access querying is currently unsupported");
            } else {
                this.lastIndex = theToIndex;
            }

            var loaded_measurements = new ArrayList<IBaseResource>(num_samples);
            for (var sample : measurements.fetchNext(num_samples)) {
                LocalDateTime database_timestamp = sample.get(MEASUREMENTS.TIMESTAMP);
                long subject = (long) sample.get(MEASUREMENTS.SUBJECT);
                String body_part = sample.get(SENSORS.BODY_PART);

                // Fill the observation with meaningful information
                var observation = new Observation();
                observation.setId(String.format("%s-%d", database_timestamp.toString(), subject));
                observation.setStatus(ObservationStatus.FINAL);
                observation.setCategory(ACCELERATION_CATEGORY);
                observation.setSubject(new Reference(new IdType("Patient", subject)));
                observation.setEffective(new InstantType(
                        Date.from(database_timestamp.atZone(ZoneId.systemDefault()).toInstant()),
                        TemporalPrecisionEnum.MILLI,
                        TIME_ZONE));
                observation.setComponent(Arrays.asList(
                        ACCELERATION_COMPONENTS[0].createObservationComponent(sample),
                        ACCELERATION_COMPONENTS[1].createObservationComponent(sample),
                        ACCELERATION_COMPONENTS[2].createObservationComponent(sample)));
                observation.setDevice(new Reference(new IdType("Device", sample.get(SENSORS.DEVICE))));
                observation.setBodySite(new CodeableConcept(new Coding("custom", body_part, body_part)));
                loaded_measurements.add(observation);
            }

            return loaded_measurements;
        }

        @Override
        public String getUuid() {
            return null;
        }

        @Override
        public Integer preferredPageSize() {
            return null;
        }

        @Override
        public Integer size() {
            return this.numMeasurements;
        }
    }

    /**
     * Create a new PatientResourceProvider.
     */
    public ObservationResourceProvider(DSLContext connection) {
        this.connection = connection;
    }

    @Override
    public Class<Observation> getResourceType() {
        return Observation.class;
    }

    /*
     * Search for all observations.
     */
    @Search
    public IBundleProvider search(
            @OptionalParam(name = Observation.SP_SUBJECT) TokenParam subject,
            @OptionalParam(name = Observation.SP_DATE) DateRangeParam range) {

        Condition condition = DSL.trueCondition();

        // Allow searching for subject
        if (subject != null) {
            var raw_value = subject.getValue();
            int subject_id;
            try {
                subject_id = Integer.parseInt(raw_value);
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Unknown Subject ID");
            }
            condition = condition.and(MEASUREMENTS.SUBJECT.eq(subject_id));
        }

        // Allow searching for ranges
        if (range != null) {
            Date start = range.getLowerBoundAsInstant();
            if (start != null) {
                LocalDateTime casted_start = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                condition = condition.and(MEASUREMENTS.TIMESTAMP.ge(casted_start));
            }

            Date end = range.getUpperBoundAsInstant();
            if (end != null) {
                LocalDateTime casted_end = start.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                condition = condition.and(MEASUREMENTS.TIMESTAMP.le(casted_end));
            }
        }

        return new FetchedObservations(this.connection, condition);
    }
}
