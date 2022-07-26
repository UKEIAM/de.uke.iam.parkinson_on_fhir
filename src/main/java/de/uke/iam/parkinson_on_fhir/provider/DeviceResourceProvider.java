package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;

import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.*;
import org.jooq.DSLContext;
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
}
