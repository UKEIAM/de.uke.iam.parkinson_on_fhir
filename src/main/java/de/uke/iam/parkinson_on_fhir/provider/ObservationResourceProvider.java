package de.uke.iam.parkinson_on_fhir.provider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.MarkdownType;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record7;
import org.jooq.Record16;
import org.jooq.TableField;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.Condition;
import org.jooq.Cursor;

import static de.uke.iam.parkinson_on_fhir.database.Tables.*;
import de.uke.iam.parkinson_on_fhir.database.tables.records.MeasurementsRecord;

/**
 * A provider for observations both related to accelometer data and rating by
 * doctors.
 */
public class ObservationResourceProvider implements IResourceProvider {

    private DSLContext connection;

    /**
     * An abstract base class for fetched observations of a specific category.
     */
    private static abstract class FetchedObservations implements IBundleProvider {
        public final static int FETCH_SIZE = 256;
        public final static TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

        protected final List<CodeableConcept> category;

        private final int numMeasurements;
        private final InstantType searchTime;
        private int lastIndex;

        /**
         * Initialize the underlying constants.
         */
        protected FetchedObservations(int numMeasurements, Coding category) {
            this.searchTime = InstantType.withCurrentTime();
            this.numMeasurements = numMeasurements;
            this.category = Arrays.asList(new CodeableConcept(category));

            this.lastIndex = 0;
        }

        /**
         * Read the next samples from the underlying database connection.
         * 
         * @param numSamples The number of samples to be read.
         * @return The samples read.
         */
        protected abstract List<IBaseResource> fetchNext(int numSamples);

        @Override
        public List<IBaseResource> getResources(int theFromIndex, int theToIndex) {
            var numSamples = theToIndex - theFromIndex;
            if (numSamples <= 0) {
                return new ArrayList<IBaseResource>();
            }

            // By now, we do not support random access into the observations.
            if (lastIndex != theFromIndex) {
                throw new NotImplementedOperationException("Random access querying is currently unsupported");
            } else {
                this.lastIndex = theToIndex;
            }

            return this.fetchNext(numSamples);
        }

        @Override
        public IPrimitiveType<Date> getPublished() {
            return this.searchTime;
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

        protected static Date castLocalDateTime(LocalDateTime localDateTime) {
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }

        /**
         * Try to parse a reference where one expect a specific identifier by ensuring
         * the reference is proper and a value is available.
         * 
         * @param reference          The existing reference to be checked.
         * @param expectedIdentifier The expected identifier.
         * @throws UnprocessableEntityException Thrown when the reference is not
         *                                      valid.
         */
        public static String parseExpectedReference(Reference reference, String expectedIdentifier)
                throws UnprocessableEntityException {
            if (!reference.isEmpty() && reference.getIdentifier().getValue().compareTo(expectedIdentifier) == 0) {
                var value = reference.getIdentifier().getValue();
                if (value == null) {
                    throw new UnprocessableEntityException(
                            String.format("%sThe given value for identifier '%s' is invalid.",
                                    Msg.code(639),
                                    expectedIdentifier));
                }
                return value;
            } else {
                throw new UnprocessableEntityException(
                        String.format("%sThe given reference for expected identifier '%s' is invalid.", Msg.code(639),
                                expectedIdentifier));
            }
        }

        /**
         * Try to parse a list of codeable concepts if exactly one coding is required.
         * 
         * @param codeableConcepts The codable concepts.
         * @param name             The name used for understandable error messages.
         * @return The included Coding.
         * @throws UnprocessableEntityException Thrown if there exists not exactly one
         *                                      codable concept.
         */
        public static Coding parseCodeableConcept(List<CodeableConcept> codeableConcepts, String name)
                throws UnprocessableEntityException {
            if (codeableConcepts.size() == 1) {
                return parseCodeableConcept(codeableConcepts.get(0), name);
            } else {
                throw new UnprocessableEntityException(
                        String.format("%sExactly one concept is expected for '%s'", Msg.code(639), name));
            }
        }

        /**
         * Try to parse a list of codings if exactly one coding is required.
         * 
         * @param codeableConcept The codable concepts.
         * @param name            The name used for understandable error messages.
         * @return The included Coding.
         * @throws UnprocessableEntityException Thrown if there exists not exactly one
         *                                      coding.
         */
        public static Coding parseCodeableConcept(CodeableConcept codeableConcept, String name)
                throws UnprocessableEntityException {
            var codings = codeableConcept.getCoding();
            if (codings.size() != 1) {
                throw new UnprocessableEntityException(
                        String.format("%sExactly one coding is expected for '%s'", Msg.code(639), name));
            }
            return codings.get(0);
        }
    }

    /**
     * An fetched set of acceleration measurements.
     */
    private static class FetchedAccelerationObservations extends FetchedObservations {

        /**
         * A utility class allowing easy creation of ObservationComponentComponents for
         * acceleration data.
         */
        private static class AccelerationComponent {
            private final CodeableConcept concept;
            private final TableField<MeasurementsRecord, Float> tableEntry;

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

            /**
             * Try to parse a ObservationComponentComponent as an instance of this
             * AccelerationComponent. If the concept does not match, ignore it and return
             * null.
             * 
             * @param component The potential resource.
             * @return The value included in the resource or NULL if it does not match.
             * @throws UnprocessableEntityException The resource matches but is ill-formed.
             */
            public Float tryParse(ObservationComponentComponent component) throws UnprocessableEntityException {
                if (component.getCode().equals(this.concept)) {
                    try {
                        return (float) component.getValueQuantity().getValue().doubleValue();
                    } catch (FHIRException | NullPointerException e) {
                        throw new UnprocessableEntityException(
                                "The observation component does not contain a valid quantity.");
                    }
                } else {
                    return null;
                }
            }
        }

        private Cursor<Record7<LocalDateTime, Integer, Float, Float, Float, String, String>> measurements;

        private static final AccelerationComponent[] ACCELERATION_COMPONENTS;
        public static final Coding CATEGORY;
        public static final String RESOURCE_TYPE = "Patient";

        static {
            ACCELERATION_COMPONENTS = new AccelerationComponent[3];
            ACCELERATION_COMPONENTS[0] = new AccelerationComponent(MEASUREMENTS.X, "X42", "Acceleration on the X axis");
            ACCELERATION_COMPONENTS[1] = new AccelerationComponent(MEASUREMENTS.Y, "X43", "Acceleration on the Y axis");
            ACCELERATION_COMPONENTS[2] = new AccelerationComponent(MEASUREMENTS.Z, "X44", "Acceleration on the Z axis");

            CATEGORY = new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "procedure",
                    "Procedure");
        }

        public FetchedAccelerationObservations(DSLContext connection, Integer subject, LocalDateTime start,
                LocalDateTime end) {
            super(connection.selectCount().from(MEASUREMENTS).where(buildWhere(subject, start, end)).fetchOne(0,
                    int.class), CATEGORY);

            this.measurements = connection
                    .select(MEASUREMENTS.TIMESTAMP, MEASUREMENTS.SUBJECT, MEASUREMENTS.X, MEASUREMENTS.Y,
                            MEASUREMENTS.Z, SENSORS.BODY_PART, SENSORS.DEVICE)
                    .from(MEASUREMENTS).join(SENSORS)
                    .on(MEASUREMENTS.SENSOR.eq(SENSORS.SENSOR_ID)).where(buildWhere(subject, start, end))
                    .fetchSize(FETCH_SIZE).fetchLazy();
        }

        @Override
        protected List<IBaseResource> fetchNext(int numSamples) {
            var loaded_measurements = new ArrayList<IBaseResource>(numSamples);
            for (var sample : measurements.fetchNext(numSamples)) {
                LocalDateTime database_timestamp = sample.get(MEASUREMENTS.TIMESTAMP);
                long subject = (long) sample.get(MEASUREMENTS.SUBJECT);
                String body_part = sample.get(SENSORS.BODY_PART);

                // Fill the observation with meaningful information
                var observation = new Observation();
                observation.setId(String.format("A-%s-%d", database_timestamp.toString(), subject));
                observation.setStatus(ObservationStatus.FINAL);
                observation.setCategory(this.category);
                observation.setSubject(new Reference(new IdType(RESOURCE_TYPE, subject)));
                observation.setEffective(new InstantType(
                        castLocalDateTime(database_timestamp),
                        TemporalPrecisionEnum.MILLI,
                        TIME_ZONE));
                observation.setComponent(Arrays.asList(
                        ACCELERATION_COMPONENTS[0].createObservationComponent(sample),
                        ACCELERATION_COMPONENTS[1].createObservationComponent(sample),
                        ACCELERATION_COMPONENTS[2].createObservationComponent(sample)));
                observation.setDevice(new Reference(new IdType("Device", sample.get(SENSORS.DEVICE))));
                observation.setBodySite(new CodeableConcept(new Coding("Custom", body_part, body_part)));
                loaded_measurements.add(observation);
            }

            return loaded_measurements;
        }

        /**
         * Try to insert the given observation. If dry-runned, this method could be used
         * to check for validity. The "category" MUST be correct as it is not checked!
         * 
         * @param observation The given observation.
         * @throws UnprocessableEntityException Thrown when the observation is not
         *                                      valid.
         */
        public static String insertObservation(DSLContext connection, Observation observation)
                throws UnprocessableEntityException {

            if (observation.getStatus() != ObservationStatus.FINAL) {
                throw new UnprocessableEntityException(Msg.code(639) + "The observation must be FINAL.");
            }

            LocalDateTime timestamp;
            try {
                timestamp = LocalDateTime.ofInstant(observation.getEffectiveInstantType().getValue().toInstant(),
                        TIME_ZONE.toZoneId());
            } catch (FHIRException e) {
                throw new UnprocessableEntityException(Msg.code(639) + "An instant timestamp is required");
            }

            // Check the subject
            int subject_id;
            try {
                subject_id = Integer.parseInt(parseExpectedReference(observation.getSubject(), RESOURCE_TYPE));
            } catch (NumberFormatException e) {
                throw new UnprocessableEntityException(Msg.code(639) + "The given subject ID is malformed");
            }

            // Extract device and body side and try to identify the sensor ID from them.
            var device = parseExpectedReference(observation.getDevice(), "Device");
            var bodySide = parseCodeableConcept(observation.getBodySite(), "BodySide").getCode();
            int sensor_id;
            try {
                sensor_id = connection.selectOne().from(SENSORS)
                        .where(SENSORS.DEVICE.eq(device), SENSORS.BODY_PART.eq(bodySide))
                        .fetchOne(SENSORS.SENSOR_ID, Integer.class).intValue();
            } catch (DataAccessException | NullPointerException e) {
                throw new UnprocessableEntityException(
                        Msg.code(639) + "Unable to identify the proper sensor for the measurement");
            }

            // Parse the accelerometer values
            Float[] parsedValues = new Float[3];
            int currentIndex = 0;
            for (var knownComponent : ACCELERATION_COMPONENTS) {
                // Search through all components ...
                INNER_LOOP: for (var component : observation.getComponent()) {
                    // ... and try to match to a known one.
                    if ((parsedValues[currentIndex] = knownComponent.tryParse(component)) != null) {
                        break INNER_LOOP;
                    }
                }
                ++currentIndex;
            }

            // Try to insert the values if they are unique
            try {
                connection.insertInto(MEASUREMENTS)
                        .set(MEASUREMENTS.TIMESTAMP, timestamp)
                        .set(MEASUREMENTS.SUBJECT, subject_id)
                        .set(MEASUREMENTS.SENSOR, sensor_id)
                        .set(MEASUREMENTS.X, parsedValues[0])
                        .set(MEASUREMENTS.Y, parsedValues[1])
                        .set(MEASUREMENTS.Z, parsedValues[2])
                        .execute();
            } catch (DataAccessException e) {
                throw new UnprocessableEntityException(
                        Msg.code(639) + "The sample already exists");
            }

            // Create the ID
            return String.format("A-%s-%d", timestamp.toString(), subject_id);
        }

        private static Condition buildWhere(Integer subject, LocalDateTime start, LocalDateTime end) {
            Condition where = DSL.trueCondition();
            if (subject != null) {
                where = where.and(MEASUREMENTS.SUBJECT.eq(subject));
            }
            if (start != null) {
                where = where.and(MEASUREMENTS.TIMESTAMP.ge(start));
            }
            if (end != null) {
                where = where.and(MEASUREMENTS.TIMESTAMP.le(end));
            }
            return where;
        }
    }

    /**
     * An fetched set of acceleration measurements.
     */
    private static class FetchedRatings extends FetchedObservations {

        private Cursor<Record16<Float, String, Integer, String, Float, Float, String, Integer, LocalDateTime, LocalDateTime, Integer, String, String, String, String, String>> measurements;

        public FetchedRatings(DSLContext connection, Integer subject, LocalDateTime start, LocalDateTime end) {
            super(connection.selectCount().from(RATINGS).join(TASKS).on(RATINGS.TASK.eq(TASKS.TASK_ID))
                    .where(buildWhere(subject, start, end))
                    .fetchOne(0, int.class),
                    new Coding("http://terminology.hl7.org/CodeSystem/observation-category", "exam", "Exam"));
            this.measurements = connection
                    .select(RATINGS.RATING, RATINGS.COMMENT, RATINGS.SENSOR,
                            ASSESSMENTS.NAME, ASSESSMENTS.MINIMAL_SEVERENESS, ASSESSMENTS.MAXIMAL_SEVERENESS,
                            ASSESSMENTS.DESCRIPTION,
                            TASKS.TASK_ID, TASKS.TASK_START, TASKS.TASK_END, TASKS.SUBJECT,
                            TASKTYPES.NAME, TASKTYPES.DESCRIPTION, TASKTYPES.UPDRS_CODE,
                            BODYPARTS.NAME, BODYPARTS.DESCRIPTION)
                    .from(RATINGS)
                    .join(ASSESSMENTS).on(RATINGS.ASSESSMENT.eq(ASSESSMENTS.NAME))
                    .join(TASKS).on(RATINGS.TASK.eq(TASKS.TASK_ID))
                    .join(TASKTYPES).on(TASKS.TASK_TYPE.eq(TASKTYPES.NAME))
                    .join(SENSORS).on(RATINGS.SENSOR.eq(SENSORS.SENSOR_ID))
                    .join(BODYPARTS).on(SENSORS.BODY_PART.eq(BODYPARTS.NAME))
                    .where(buildWhere(subject, start, end)).fetchSize(FETCH_SIZE).fetchLazy();
        }

        @Override
        protected List<IBaseResource> fetchNext(int numSamples) {
            var loaded_measurements = new ArrayList<IBaseResource>(numSamples);
            for (var sample : measurements.fetchNext(numSamples)) {
                var sensor = sample.get(RATINGS.SENSOR);
                var assessment_name = sample.get(ASSESSMENTS.NAME);
                var task = sample.get(TASKS.TASK_ID);

                // Fill the observation with meaningful information
                var observation = new Observation();
                observation.setId(String.format("R-%s-%d-%d", assessment_name, sensor, task));
                observation.setStatus(ObservationStatus.FINAL);
                observation.setCategory(this.category);
                observation.setSubject(new Reference(new IdType("Patient", (long) sample.get(TASKS.SUBJECT))));
                observation.setValue(new Quantity(sample.get(RATINGS.RATING)));
                observation.setReferenceRange(
                        Arrays.asList(new ObservationReferenceRangeComponent()
                                .setLow(new Quantity(sample.get(ASSESSMENTS.MINIMAL_SEVERENESS)))
                                .setHigh(new Quantity(sample.get(ASSESSMENTS.MAXIMAL_SEVERENESS)))));

                // Treat UPDRS and non-UPDRS assessments differently
                var updrs_code = sample.get(TASKTYPES.UPDRS_CODE);
                if (updrs_code == null) {
                    // If no UPDRS rating is given, set the code to specify WHAT symptom that
                    // assessed ...
                    observation.setCode(new CodeableConcept(
                            new Coding("custom", assessment_name, sample.get(ASSESSMENTS.DESCRIPTION))));

                    // ... , at which body part,
                    observation.setBodySite(new CodeableConcept(
                            new Coding("custom", sample.get(BODYPARTS.NAME), sample.get(BODYPARTS.DESCRIPTION))));

                    // ... and during which task.
                    observation.setMethod(new CodeableConcept(
                            new Coding("custom", sample.get(TASKTYPES.NAME), sample.get(TASKTYPES.DESCRIPTION))));
                } else {
                    // TODO: Set appropiate LOINC codes from https://loinc.org/77717-7/
                    observation.setCode(new CodeableConcept(
                            new Coding("http://loinc.org", "77717-7", updrs_code)));
                }

                // Encode optional comments
                var note = sample.get(RATINGS.COMMENT);
                if (note != null && !note.isEmpty()) {
                    observation.setNote(Arrays.asList(new Annotation(new MarkdownType(note))));
                }

                // Set the time of the test
                var duration = new Period().setStart(castLocalDateTime(sample.get(TASKS.TASK_START)));
                var end = sample.get(TASKS.TASK_END);
                if (end != null) {
                    duration = duration.setEnd(castLocalDateTime(end));
                }
                observation.setEffective(duration);

                loaded_measurements.add(observation);
            }

            return loaded_measurements;
        }

        private static Condition buildWhere(Integer subject, LocalDateTime start, LocalDateTime end) {
            Condition where = DSL.trueCondition();
            if (subject != null) {
                where = where.and(TASKS.SUBJECT.eq(subject));
            }
            if (start != null) {
                where = where.and(TASKS.TASK_START.ge(start));
            }
            if (end != null) {
                where = where.and(TASKS.TASK_END.le(end));
            }
            return where;
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
            @OptionalParam(name = Observation.SP_CATEGORY) TokenParam category,
            @OptionalParam(name = Observation.SP_SUBJECT) TokenParam subject,
            @OptionalParam(name = Observation.SP_DATE) DateRangeParam range) {

        // Allow searching for subject
        Integer subject_id = null;
        if (subject != null) {
            var raw_value = subject.getValue();
            try {
                subject_id = Integer.parseInt(raw_value);
            } catch (NumberFormatException e) {
                throw new ResourceNotFoundException("Unknown Subject ID");
            }
        }

        // Allow searching for ranges
        LocalDateTime start = null, end = null;
        if (range != null) {
            Date raw_start = range.getLowerBoundAsInstant();
            if (raw_start != null) {
                start = raw_start.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }

            Date raw_end = range.getUpperBoundAsInstant();
            if (raw_end != null) {
                end = raw_end.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
        }

        if (category != null && category.getValue().compareTo("exam") == 0) {
            return new FetchedRatings(this.connection, subject_id, start, end);
        } else if (category != null && category.getValue().compareTo("procedure") == 0) {
            return new FetchedAccelerationObservations(this.connection, subject_id, start, end);
        } else {
            throw new ResourceNotFoundException("Plase specify 'exam' or 'procedure' for category");
        }
    }

    @Create
    public MethodOutcome createObservation(@ResourceParam Observation observation) {
        var concept = FetchedObservations.parseCodeableConcept(observation.getCategory(), "Category");
        if (!concept.equals(FetchedAccelerationObservations.CATEGORY)) {
            throw new UnprocessableEntityException("Unsupported observation");
        }

        MethodOutcome result = new MethodOutcome();
        result.setId(new IdType("Observation",
                FetchedAccelerationObservations.insertObservation(this.connection, observation)));
        result.setOperationOutcome(new OperationOutcome());
        return result;
    }
}
