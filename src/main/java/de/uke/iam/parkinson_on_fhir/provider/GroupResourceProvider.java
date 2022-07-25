package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.r4.model.Group.GroupMemberComponent;
import org.hl7.fhir.r4.model.Group.GroupType;
import org.hl7.fhir.r4.model.*;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.Condition;

import static de.uke.iam.parkinson_on_fhir.database.Tables.*;

/**
 * This provider grands access to all the subjects within the database.
 */
public class GroupResourceProvider implements IResourceProvider {

   private DSLContext connection;

   /**
    * Create a new GroupResourceProvider.
    */
   public GroupResourceProvider(DSLContext connection) {
      this.connection = connection;
   }

   /**
    * The getResourceType method comes from IResourceProvider, and must be
    * overridden to indicate what type of resource this provider supplies.
    */
   @Override
   public Class<Group> getResourceType() {
      return Group.class;
   }

   @Search
   /**
    * Query all groups.
    * 
    * @return Returns all available groups.
    */
   public List<Group> findGroups() {
      return loadGroups(DSL.trueCondition());
   }

   /**
    * Find a group given its ID.
    *
    * @param theId The ID of the group.
    * @return Returns a resource matching this identifier, or null if none exists.
    */
   @Read(version = false)
   public Group readGroup(@IdParam IdType theId) {
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
      var groups = this.loadGroups(SOURCES.SOURCE_ID.eq(id));
      if (!groups.isEmpty()) {
         return groups.get(0);
      } else {
         return null;
      }
   }

   private List<Group> loadGroups(Condition where) {
      ArrayList<Group> groups = new ArrayList<Group>();

      // Query all subjects with associated sources
      for (var record : this.connection.select(SOURCES.SOURCE_ID, SOURCES.DESCRIPTION, SUBJECTS.SUBJECT_ID)
            .from(SOURCES.join(SUBJECTS).on(SUBJECTS.SOURCE.eq(SOURCES.SOURCE_ID)))
            .where(where)
            .orderBy(SOURCES.SOURCE_ID)
            .fetch()) {

         var groupId = record.getValue(SOURCES.SOURCE_ID).toString();

         // Create group if it does not already exists
         if (groups.isEmpty() || groups.get(groups.size() - 1).getId().compareTo(groupId) != 0) {
            Group group = new Group();
            group.setId(groupId);
            group.setActual(true);
            group.setActive(true);
            group.setType(GroupType.PERSON);
            group.setName(record.getValue(SOURCES.DESCRIPTION).toString());
            groups.add(group);
         }

         // Add a specific subject as member
         var member = new GroupMemberComponent();
         member.setEntity(new Reference(new IdDt("Patient", (long) record.get(SUBJECTS.SUBJECT_ID))));
         groups.get(groups.size() - 1).addMember(member);
      }

      return groups;
   }
}
