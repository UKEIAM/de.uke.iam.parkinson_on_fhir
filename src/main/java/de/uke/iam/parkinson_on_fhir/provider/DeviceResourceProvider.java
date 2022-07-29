package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;

import org.hl7.fhir.r4.model.*;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.Condition;

import static de.uke.iam.parkinson_on_fhir.database.Tables.*;

public class DeviceResourceProvider implements IResourceProvider {

    private DSLContext connection;

    /**
     * Create a new DeviceResourceProvider.
     */
    public DeviceResourceProvider(DSLContext connection) {
        this.connection = connection;
    }

    @Override
    public Class<Device> getResourceType() {
        return Device.class;
    }

    @Search
    /**
     * Query all patiens.
     * 
     * @return Returns all available patients.
     */
    public List<Device> findDevices() {
        return loadDevices(DSL.trueCondition());
    }

    /**
     * Find a device given its ID.
     *
     * @param theId The ID of the device.
     * @return Returns a resource matching this identifier, or null if none exists.
     */
    @Read(version = false)
    public Device readPatient(@IdParam IdType theId) {
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
        var groups = this.loadDevices(SUBJECTS.SUBJECT_ID.eq(id));
        if (!groups.isEmpty()) {
            return groups.get(0);
        } else {
            return null;
        }
    }

    private List<Device> loadDevices(Condition where) {
        var devices = new ArrayList<Device>();
        for (var record : this.connection.select()
                .from(DEVICES)
                .where(where)
                .fetch()) {
            var device = new Device();
            device.setId(record.get(DEVICES.DEVICE));
            device.setDistinctIdentifier(record.get(DEVICES.DESCRIPTION));
            devices.add(device);
        }
        return devices;
    }

    @Create
    public MethodOutcome createDevice(@ResourceParam Device device) {

        /*
         * // We have to investigate how to access the ID as String.
         * String deviceName;
         * var raw_member_identifier = device.getIdentifier();
         * 
         * if (raw_member_identifier.size() != 1) {
         * var identifier = raw_member_identifier.get(0);
         * if (identifier.getSystem().compareTo("Device") != 0) {
         * throw new UnprocessableEntityException(
         * Msg.code(639) + "Only devices are supported");
         * }
         * deviceName = identifier.getValue();
         * if (deviceName != null) {
         * throw new UnprocessableEntityException(
         * Msg.code(639) + "An ID must be specified");
         * }
         * } else {
         * throw new UnprocessableEntityException(
         * Msg.code(639) + "Exactly one identifier is require");
         * }
         */

        // Ensure a description is given
        var deviceDescription = device.getDistinctIdentifier();
        if (deviceDescription == null) {
            throw new UnprocessableEntityException(
                    Msg.code(639) + "An distinct identifier is required for description");
        }

        // Try to insert the device into the database
        try {
            if (this.connection.insertInto(DEVICES).set(DEVICES.DEVICE, deviceDescription)
                    .set(DEVICES.DESCRIPTION, deviceDescription).execute() != 1) {
                throw new DataAccessException("Insert failed");
            }
        } catch (DataAccessException e) {
            throw new UnprocessableEntityException(
                    Msg.code(639) +
                            "Unable to create the device. Is the identifier already used?");
        }

        // Generate the result
        MethodOutcome result = new MethodOutcome();
        result.setId(new IdType("Device", deviceDescription));
        result.setOperationOutcome(new OperationOutcome());
        return result;

    }
}
