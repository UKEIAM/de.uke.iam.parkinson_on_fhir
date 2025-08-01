/*
 * This file is generated by jOOQ.
 */
package de.uke.iam.parkinson_on_fhir.database.tables.records;


import de.uke.iam.parkinson_on_fhir.database.tables.Tasktypes;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record3;
import org.jooq.Row3;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class TasktypesRecord extends UpdatableRecordImpl<TasktypesRecord> implements Record3<String, String, String> {

    private static final long serialVersionUID = 2106129079;

    /**
     * Setter for <code>public.tasktypes.name</code>.
     */
    public void setName(String value) {
        set(0, value);
    }

    /**
     * Getter for <code>public.tasktypes.name</code>.
     */
    public String getName() {
        return (String) get(0);
    }

    /**
     * Setter for <code>public.tasktypes.description</code>.
     */
    public void setDescription(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>public.tasktypes.description</code>.
     */
    public String getDescription() {
        return (String) get(1);
    }

    /**
     * Setter for <code>public.tasktypes.updrs_code</code>.
     */
    public void setUpdrsCode(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>public.tasktypes.updrs_code</code>.
     */
    public String getUpdrsCode() {
        return (String) get(2);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<String> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record3 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row3<String, String, String> fieldsRow() {
        return (Row3) super.fieldsRow();
    }

    @Override
    public Row3<String, String, String> valuesRow() {
        return (Row3) super.valuesRow();
    }

    @Override
    public Field<String> field1() {
        return Tasktypes.TASKTYPES.NAME;
    }

    @Override
    public Field<String> field2() {
        return Tasktypes.TASKTYPES.DESCRIPTION;
    }

    @Override
    public Field<String> field3() {
        return Tasktypes.TASKTYPES.UPDRS_CODE;
    }

    @Override
    public String component1() {
        return getName();
    }

    @Override
    public String component2() {
        return getDescription();
    }

    @Override
    public String component3() {
        return getUpdrsCode();
    }

    @Override
    public String value1() {
        return getName();
    }

    @Override
    public String value2() {
        return getDescription();
    }

    @Override
    public String value3() {
        return getUpdrsCode();
    }

    @Override
    public TasktypesRecord value1(String value) {
        setName(value);
        return this;
    }

    @Override
    public TasktypesRecord value2(String value) {
        setDescription(value);
        return this;
    }

    @Override
    public TasktypesRecord value3(String value) {
        setUpdrsCode(value);
        return this;
    }

    @Override
    public TasktypesRecord values(String value1, String value2, String value3) {
        value1(value1);
        value2(value2);
        value3(value3);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached TasktypesRecord
     */
    public TasktypesRecord() {
        super(Tasktypes.TASKTYPES);
    }

    /**
     * Create a detached, initialised TasktypesRecord
     */
    public TasktypesRecord(String name, String description, String updrsCode) {
        super(Tasktypes.TASKTYPES);

        set(0, name);
        set(1, description);
        set(2, updrsCode);
    }
}
