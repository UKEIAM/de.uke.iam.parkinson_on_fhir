package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.OperationOutcome;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.Condition;

import static de.uke.iam.parkinson_on_fhir.database.Tables.*;

public class PatientResourceProvider implements IResourceProvider {

    private DSLContext connection;

    /**
     * Create a new PatientResourceProvider.
     */
    public PatientResourceProvider(DSLContext connection) {
        this.connection = connection;
    }

    @Override
    public Class<Patient> getResourceType() {
        return Patient.class;
    }

    @Search
    /**
     * Query all patiens.
     * 
     * @return Returns all available patients.
     */
    public List<Patient> findPatients() {
        return loadPatients(DSL.trueCondition());
    }

    /**
     * Find a patient given its ID.
     *
     * @param theId The ID of the patient.
     * @return Returns a resource matching this identifier, or null if none exists.
     */
    @Read(version = false)
    public Patient readPatient(@IdParam IdType theId) {
        // Try to parse the ID
        int id;
        try {
            id = theId.getIdPartAsLong().intValue();
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException(theId);
        }

        // We do not support versions
        if (theId.hasVersionIdPart()) {
            throw new ResourceNotFoundException("Versions are not supported");
        }

        // Query the groups
        var groups = this.loadPatients(SUBJECTS.SUBJECT_ID.eq(id));
        if (!groups.isEmpty()) {
            return groups.get(0);
        } else {
            return null;
        }
    }

    private List<Patient> loadPatients(Condition where) {
        ArrayList<Patient> patients = new ArrayList<Patient>();

        for (var record : this.connection.select(SUBJECTS.SUBJECT_ID, SUBJECTS.DESCRIPTION)
                .from(SUBJECTS)
                .where(where)
                .fetch()) {
            Patient patient = new Patient();
            patient.setId(record.get(SUBJECTS.SUBJECT_ID).toString());
            patient.setActive(true);

            // Add description if additionally given
            var description = record.get(SUBJECTS.DESCRIPTION);
            if (description != null) {
                patient.getText().setStatus(Narrative.NarrativeStatus.ADDITIONAL);
                patient.getText().setDivAsString(description);
            }

            patients.add(patient);
        }

        return patients;
    }

    @Create
    public MethodOutcome createPatient(@ResourceParam Patient patient) {
        if (patient.getActive()) {
            throw new UnprocessableEntityException(
                    Msg.code(639) + "Patient must be active");
        }

        Record1<Integer> subjectId;
        try {
            // Check if additional descriptions need to be added
            var content = patient.getText().getDiv().getContent();
            if (content != null && !content.isEmpty()) {
                subjectId = this.connection.insertInto(SUBJECTS).set(SUBJECTS.DESCRIPTION, content)
                        .returningResult(SUBJECTS.SUBJECT_ID).fetchOne();
            } else {
                subjectId = this.connection.insertInto(SUBJECTS).values().returningResult(SUBJECTS.SUBJECT_ID)
                        .fetchOne();
            }
        } catch (DataAccessException e) {
            throw new UnprocessableEntityException(
                    Msg.code(639) + "Generating patient ID failed");
        }

        MethodOutcome result = new MethodOutcome();
        result.setId(new IdType("Patient", subjectId.get(SUBJECTS.SUBJECT_ID).toString()));
        result.setOperationOutcome(new OperationOutcome());
        return result;
    }
}
