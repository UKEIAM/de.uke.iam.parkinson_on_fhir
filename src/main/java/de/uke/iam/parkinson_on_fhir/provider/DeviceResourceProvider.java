package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;

import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Device.DeviceDeviceNameComponent;
import org.hl7.fhir.r4.model.Device.DeviceNameType;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
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
    public Device readDevice(@IdParam IdType theId) {
        // We do not support versions
        if (theId.hasVersionIdPart()) {
            throw new ResourceNotFoundException("Versions are not supported");
        }

        // Query the groups
        var groups = this.loadDevices(DEVICES.DEVICE.eq(theId.getIdPart()));
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
            // Add the description as friendly name
            var names = new ArrayList<DeviceDeviceNameComponent>();
            names.add(
                    new DeviceDeviceNameComponent().setName(record.get(DEVICES.DESCRIPTION))
                            .setType(DeviceNameType.USERFRIENDLYNAME));

            device.setId(record.get(DEVICES.DEVICE));
            device.setDistinctIdentifier(record.get(DEVICES.DEVICE));
            device.setDeviceName(names);
            devices.add(device);
        }
        return devices;
    }

    @Create
    public MethodOutcome createDevice(@ResourceParam Device device) {
        var deviceDescription = device.getDistinctIdentifier();
        if (deviceDescription == null) {
            throw new UnprocessableEntityException(
                    Msg.code(639) + "An distinct identifier is required for description");
        }

        // Search the user-friendly device name or fall back to the identifier
        String deviceName = null;
        var deviceNames = device.getDeviceName();
        for (var deviceNameRaw : (deviceNames != null ? deviceNames
                : new java.util.ArrayList<DeviceDeviceNameComponent>())) {
            if (deviceNameRaw.getType() == DeviceNameType.USERFRIENDLYNAME) {
                deviceName = deviceNameRaw.getName();
                break;
            }
        }
        if (deviceName == null || deviceName.isBlank()) {
            deviceName = deviceDescription;
        }

        // Try to insert the device into the database
        try {
            if (this.connection.insertInto(DEVICES).set(DEVICES.DEVICE, deviceDescription)
                    .set(DEVICES.DESCRIPTION, deviceName).execute() != 1) {
                throw new DataAccessException("Insert failed");
            }
        } catch (DataAccessException e) {
            throw new UnprocessableEntityException(String
                    .format("%sUnable to create the device '%s'. Is the identifier already used?",
                            Msg.code(639),
                            deviceDescription));
        }

        // Generate the result
        MethodOutcome result = new MethodOutcome();
        result.setId(new IdType("Device", deviceDescription));
        result.setOperationOutcome(new OperationOutcome());
        return result;
    }

    @Delete
    public void deleteDevice(@IdParam IdType id) {
        var identifier = id.getIdPart();

        // Try to delete the device from the database
        try {
            // Check if anything was deleted
            if (this.connection.deleteFrom(DEVICES).where(DEVICES.DEVICE.eq(identifier)).execute() == 0) {
                throw new ResourceNotFoundException(String.format("Device '%s' not found.", Msg.code(634), identifier));
            }
        } catch (DataAccessException e) {
            throw new ResourceVersionConflictException(
                    String.format("%sUnable to delete device '%s' as it is in use", Msg.code(635), identifier));
        }
    }
}
