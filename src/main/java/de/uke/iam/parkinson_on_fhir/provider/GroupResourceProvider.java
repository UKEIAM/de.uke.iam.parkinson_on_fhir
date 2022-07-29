package de.uke.iam.parkinson_on_fhir.provider;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;

import org.hl7.fhir.r4.model.Group.GroupMemberComponent;
import org.hl7.fhir.r4.model.Group.GroupType;
import org.hl7.fhir.r4.model.*;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.DataAccessException;
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

   @Create
   public MethodOutcome createGroup(@ResourceParam Group group) {
      if (!group.getActive()) {
         throw new UnprocessableEntityException(
               Msg.code(639) + "Group must be active");
      }

      if (!group.getActual()) {
         throw new UnprocessableEntityException(
               Msg.code(639) + "Group must be actual");
      }

      if (group.getType() != GroupType.PERSON) {
         throw new UnprocessableEntityException(
               Msg.code(639) + "Group must consist out of individuals");
      }

      ArrayList<Integer> subjectIds = new ArrayList<Integer>(group.getMember().size());
      for (var raw_member : group.getMember()) {
         try {
            // Ensure the proper type is given
            var raw_member_identifier = raw_member.getEntity().getIdentifier();
            if (raw_member_identifier.getSystem().compareTo("Patient") != 0) {
               throw new UnprocessableEntityException(
                     Msg.code(639) + "Only patients are supported");
            }

            // Try to parse the ID
            var raw_value = raw_member_identifier.getValue();
            subjectIds.add(Integer.parseInt(raw_value));
         } catch (NumberFormatException e) {
            throw new UnprocessableEntityException(
                  Msg.code(639) + "The given ID is not a valid patient identifier");
         }
      }

      // Create a transaction: If anything fails, everything failes
      AtomicInteger sourceId = new AtomicInteger(0);
      this.connection.transaction(config -> {
         try {
            // Generate the new ID
            Record1<Integer> rawSourceId;
            var name = group.getName();
            if (name != null) {
               rawSourceId = this.connection.insertInto(SOURCES).set(SOURCES.DESCRIPTION, name)
                     .returningResult(SOURCES.SOURCE_ID)
                     .fetchOne();
            } else {
               rawSourceId = this.connection.insertInto(SOURCES).values().returningResult(SOURCES.SOURCE_ID).fetchOne();
            }
            sourceId.set(rawSourceId.value1());
         } catch (DataAccessException e) {
            throw new UnprocessableEntityException(
                  Msg.code(639) + "Unable to create the ID for the new group");
         }

         // Update all subjects to belong to this group
         for (var subjectId : subjectIds) {
            if (this.connection.update(SUBJECTS).set(SUBJECTS.SOURCE, sourceId.get())
                  .where(SUBJECTS.SUBJECT_ID.eq(subjectId))
                  .execute() != 1) {
               throw new UnprocessableEntityException(
                     String.format("%sUnable to find patient with the ID '%d'", Msg.code(639), subjectId));
            }
         }
      });

      MethodOutcome result = new MethodOutcome();
      result.setId(new IdType("Group", (long) sourceId.get()));
      result.setOperationOutcome(new OperationOutcome());
      return result;
   }
}
