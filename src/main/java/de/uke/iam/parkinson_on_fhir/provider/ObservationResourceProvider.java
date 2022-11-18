package de.uke.iam.parkinson_on_fhir.provider;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Observation.ObservationComponentComponent;
import org.hl7.fhir.r4.model.Observation.ObservationStatus;
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
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record8;
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
        protected static String parseExpectedReference(Reference reference, String expectedIdentifier)
                throws UnprocessableEntityException {

            if (!reference.isEmpty()) {
                var referenceString = reference.getReference();
                if (referenceString != null) {
                    // Check if the reference is relative
                    if (!referenceString.startsWith(expectedIdentifier + "/")) {
                        throw new UnprocessableEntityException(
                                String.format(
                                        "%sThe given reference '%s' is invalid as only relative references are supported",
                                        Msg.code(639),
                                        expectedIdentifier));
                    }
                    return referenceString.substring(expectedIdentifier.length() + 1);
                }
            }

            throw new UnprocessableEntityException(
                    String.format("%sThe given reference for expected identifier '%s' is invalid", Msg.code(639),
                            expectedIdentifier));
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
        protected static Coding parseCodeableConcept(List<CodeableConcept> codeableConcepts, String name)
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
        protected static Coding parseCodeableConcept(CodeableConcept codeableConcept, String name)
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
                this.concept.addCoding(new Coding("http://loinc.org", loinc_code, description));
                this.tableEntry = tableEntry;
            }

            public ObservationComponentComponent createObservationComponent(Record record) {
                var value = new ObservationComponentComponent(this.concept);
                value.setValue(new Quantity(record.get(this.tableEntry)).setUnit("m/s^2"));
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
                var code = component.getCode().getCodingFirstRep().getCode();
                if (code != null && code.compareTo(this.concept.getCodingFirstRep().getCode()) == 0) {
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

            @Override
            public String toString() {
                return this.concept.getCoding().get(0).getDisplay();
            }
        }

        /**
         * Assign a unique ID to the observation of acceleration.
         */
        private static class MeasurementId {
            private LocalDateTime timestamp;
            private int subjectId;
            private int sensorId;

            private static Pattern PATTERN = Pattern
                    .compile("A_(?<timestamp>[0-9\\-':\\.T]+)_(?<subject>[0-9]+)_(?<sensor>[0-9]+)");

            public MeasurementId(LocalDateTime timestamp, int subjectId, int sensorId) {
                this.timestamp = timestamp;
                this.subjectId = subjectId;
                this.sensorId = sensorId;
            }

            public MeasurementId(String serializedId) throws UnprocessableEntityException {
                var match = PATTERN.matcher(serializedId);
                if (!match.find()) {
                    throw new UnprocessableEntityException(String
                            .format("%sThe ID '%s' of the observation is malformed", Msg.code(639), serializedId));
                }

                try {
                    this.timestamp = LocalDateTime.parse(match.group("timestamp"));
                    this.subjectId = Integer.parseInt(match.group("subject"));
                    this.sensorId = Integer.parseInt(match.group("sensor"));
                } catch (DateTimeParseException | NumberFormatException e) {
                    throw new UnprocessableEntityException(
                            String.format("%sThe ID '%s' of the observation is malformed: %s", Msg.code(639),
                                    serializedId, e.toString()));
                }
            }

            /**
             * Try to insert a specific measurement into the database.
             * 
             * @param connection The connection with the database.
             * @param x          The x acceleration.
             * @param y          The y acceleration.
             * @param z          The z acceleration.
             * @throws UnprocessableEntityException When the inseration fails.
             */
            public void insert(DSLContext connection, float x, float y, float z) throws UnprocessableEntityException {
                try {
                    connection.insertInto(MEASUREMENTS)
                            .set(MEASUREMENTS.TIMESTAMP, this.timestamp)
                            .set(MEASUREMENTS.SUBJECT, this.subjectId)
                            .set(MEASUREMENTS.SENSOR, this.sensorId)
                            .set(MEASUREMENTS.X, x)
                            .set(MEASUREMENTS.Y, y)
                            .set(MEASUREMENTS.Z, z)
                            .execute();
                } catch (DataAccessException e) {
                    throw new UnprocessableEntityException(String
                            .format("%sUnable to create sample. Is there a subject '%d' already in the database?",
                                    Msg.code(639), subjectId));
                }
            }

            /**
             * Try to delete a measurement from the database.
             * 
             * @param connection The connection with the database.
             * @return True, if a measurement was deleted.
             */
            public boolean delete(DSLContext connection) {
                return connection.deleteFrom(MEASUREMENTS)
                        .where(MEASUREMENTS.TIMESTAMP.eq(this.timestamp).and(
                                MEASUREMENTS.SUBJECT.eq(this.subjectId).and(MEASUREMENTS.SENSOR.eq(this.sensorId))))
                        .execute() == 1;
            }

            @Override
            public String toString() {
                return String.format("A_%s_%d_%d", timestamp.toString(), subjectId, sensorId);
            }
        }

        private Cursor<Record8<LocalDateTime, Integer, Float, Float, Float, String, String, String>> measurements;

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
                            MEASUREMENTS.Z, SENSORS.BODY_PART, SENSORS.DEVICE, BODYPARTS.DESCRIPTION)
                    .from(MEASUREMENTS)
                    .join(SENSORS).on(MEASUREMENTS.SENSOR.eq(SENSORS.SENSOR_ID))
                    .join(BODYPARTS).on(BODYPARTS.NAME.eq(SENSORS.BODY_PART))
                    .where(buildWhere(subject, start, end))
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
                observation
                        .setBodySite(new CodeableConcept(
                                new Coding("Custom", body_part, sample.get(BODYPARTS.DESCRIPTION))));
                loaded_measurements.add(observation);
            }

            return loaded_measurements;
        }

        /**
         * Try to insert the given observation. The "category" MUST be correct as it is
         * not checked!
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
                throw new UnprocessableEntityException(
                        Msg.code(639) + "An instant timestamp is required but not provided");
            }

            // Check the subject
            int subjectId;
            try {
                subjectId = Integer.parseInt(parseExpectedReference(observation.getSubject(), RESOURCE_TYPE));
            } catch (NumberFormatException e) {
                throw new UnprocessableEntityException(Msg.code(639) + "The given subject ID is malformed");
            }

            // Extract (and create, if necessary) the sensor ID
            int sensorId = getSensorId(connection, observation);

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
            for (int i = 0; i < parsedValues.length; ++i) {
                if (parsedValues[i] == null) {
                    throw new UnprocessableEntityException(String
                            .format("%sUnable to parse acceleration value for component '%s'",
                                    Msg.code(639), ACCELERATION_COMPONENTS[i].toString()));
                }
            }

            // Create an virtual ID for the measurement, insert it and return it
            var measurement = new MeasurementId(timestamp, subjectId, sensorId);
            measurement.insert(connection, parsedValues[0], parsedValues[1], parsedValues[2]);
            return measurement.toString();
        }

        public static void delete(DSLContext connection, IdType theId) {
            var measurementId = new MeasurementId(theId.getIdPart());
            if (!measurementId.delete(connection)) {
                throw new ResourceNotFoundException(
                        String.format("%sAn observation with the ID '%d' not found.", Msg.code(634),
                                theId.getIdPart()));
            }
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

        private static int getSensorId(DSLContext connection, Observation observation)
                throws UnprocessableEntityException {
            // Extract device and body side and try to identify the sensor ID from them.
            var device = parseExpectedReference(observation.getDevice(), "Device");
            var bodyPart = getBodyPart(connection, observation);

            // Try to query the sensor if it already exists
            Integer sensorId;
            try {
                sensorId = connection.select(SENSORS.SENSOR_ID).from(SENSORS)
                        .where(SENSORS.DEVICE.eq(device), SENSORS.BODY_PART.eq(bodyPart))
                        .fetchOne(SENSORS.SENSOR_ID, Integer.class);
            } catch (DataAccessException e) {
                throw new UnprocessableEntityException(
                        Msg.code(639) + "Unable to identify the proper sensor for the measurement");
            }

            // If the sensor does not already exist, create it.
            if (sensorId == null) {
                try {
                    sensorId = connection.insertInto(SENSORS, SENSORS.BODY_PART, SENSORS.DEVICE)
                            .values(bodyPart, device).returningResult(SENSORS.SENSOR_ID).fetchOne().value1();
                } catch (DataAccessException e) {
                    throw new UnprocessableEntityException(String.format(
                            "%sUnable to create combination of device and body part. Is the device '%' available within the database?",
                            Msg.code(639), device));
                }
            }

            return sensorId.intValue();
        }

        /**
         * Parse the body side of an observation. If it does not already exists, it will
         * be created and stored within the database. In any case, the returned value is
         * safe to use within the SENSORS table.
         */
        private static String getBodyPart(DSLContext connection, Observation observation)
                throws UnprocessableEntityException {
            // Extract the usable information from the observation
            var bodySide = parseCodeableConcept(observation.getBodySite(), "BodySide");
            var name = bodySide.getCode();
            var description = bodySide.getDisplay();
            if (description == null) {
                description = name;
            }

            try {
                connection.insertInto(BODYPARTS, BODYPARTS.NAME, BODYPARTS.DESCRIPTION)
                        .values(name, description).onDuplicateKeyIgnore().execute();
            } catch (DataAccessException e) {
                throw new UnprocessableEntityException(String
                        .format("%sUnable to query or insert the body part '%s' into the database", Msg.code(639),
                                name));
            }

            return name;
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
            @OptionalParam(name = Observation.SP_SUBJECT) ReferenceParam subject,
            @OptionalParam(name = Observation.SP_DATE) DateRangeParam range) {

        // Allow searching for subject
        Integer subject_id = null;
        if (subject != null) {
            try {
                subject_id = subject.getIdPartAsLong().intValue();
            } catch (NumberFormatException | NullPointerException e) {
                throw new ResourceNotFoundException(String.format("Malformed subject ID: %s", subject.toString()));
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
            throw new ResourceNotFoundException("Please specify 'exam' or 'procedure' for category");
        }
    }

    @Create
    public MethodOutcome createObservation(@ResourceParam Observation observation) {
        var concept = FetchedObservations.parseCodeableConcept(observation.getCategory(), "Category");
        if (concept.getCode().compareTo(FetchedAccelerationObservations.CATEGORY.getCode()) != 0) {
            throw new UnprocessableEntityException("Unsupported observation");
        }

        MethodOutcome result = new MethodOutcome();
        result.setId(new IdType("Observation",
                FetchedAccelerationObservations.insertObservation(this.connection, observation)));
        result.setOperationOutcome(new OperationOutcome());
        return result;
    }

    @Delete
    public void deleteObservation(@IdParam IdType theId) {
        // ToDo: At some point in time, we might have to support ratings, too.
        FetchedAccelerationObservations.delete(connection, theId);
    }
}
