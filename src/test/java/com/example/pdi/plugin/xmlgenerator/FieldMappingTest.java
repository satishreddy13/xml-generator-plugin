package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the FieldMapping data class: construction, accessors, clone independence,
 * and NodeType enum completeness.
 */
@DisplayName("FieldMapping")
class FieldMappingTest {

    @Test
    @DisplayName("default constructor initialises to empty strings and ELEMENT type")
    void defaultConstructor_initialValues() {
        FieldMapping fm = new FieldMapping();
        assertEquals("",                         fm.getSourceField());
        assertEquals("",                         fm.getXmlName());
        assertEquals(FieldMapping.NodeType.ELEMENT, fm.getNodeType());
        assertEquals("",                         fm.getParentPath());
    }

    @Test
    @DisplayName("parameterized constructor stores all values")
    void parameterizedConstructor_storesAllValues() {
        FieldMapping fm = new FieldMapping("src", "xmlName", FieldMapping.NodeType.CDATA, "a/b");
        assertEquals("src",                    fm.getSourceField());
        assertEquals("xmlName",                fm.getXmlName());
        assertEquals(FieldMapping.NodeType.CDATA, fm.getNodeType());
        assertEquals("a/b",                    fm.getParentPath());
    }

    @Test
    @DisplayName("setters update each field independently")
    void setters_updateFields() {
        FieldMapping fm = new FieldMapping();
        fm.setSourceField("field");
        fm.setXmlName("elem");
        fm.setNodeType(FieldMapping.NodeType.ATTRIBUTE);
        fm.setParentPath("parent/child");

        assertEquals("field",                       fm.getSourceField());
        assertEquals("elem",                        fm.getXmlName());
        assertEquals(FieldMapping.NodeType.ATTRIBUTE, fm.getNodeType());
        assertEquals("parent/child",                fm.getParentPath());
    }

    @Test
    @DisplayName("all three NodeType enum values exist")
    void nodeType_allThreeValuesExist() {
        assertNotNull(FieldMapping.NodeType.ELEMENT);
        assertNotNull(FieldMapping.NodeType.ATTRIBUTE);
        assertNotNull(FieldMapping.NodeType.CDATA);
        assertEquals(3, FieldMapping.NodeType.values().length);
    }

    @Test
    @DisplayName("clone() returns a non-null distinct instance")
    void clone_isDistinctInstance() {
        FieldMapping fm = new FieldMapping("s", "x", FieldMapping.NodeType.ELEMENT, "p");
        FieldMapping clone = fm.clone();
        assertNotNull(clone);
        assertNotSame(fm, clone);
    }

    @Test
    @DisplayName("clone() copies all fields correctly")
    void clone_copiesAllFields() {
        FieldMapping fm = new FieldMapping("s", "x", FieldMapping.NodeType.CDATA, "a/b");
        FieldMapping clone = fm.clone();
        assertEquals(fm.getSourceField(), clone.getSourceField());
        assertEquals(fm.getXmlName(),     clone.getXmlName());
        assertEquals(fm.getNodeType(),    clone.getNodeType());
        assertEquals(fm.getParentPath(),  clone.getParentPath());
    }

    @Test
    @DisplayName("mutating clone does not affect original")
    void clone_mutatingClone_doesNotAffectOriginal() {
        FieldMapping original = new FieldMapping("orig", "elem", FieldMapping.NodeType.ELEMENT, "");
        FieldMapping clone    = original.clone();
        clone.setSourceField("changed");
        clone.setNodeType(FieldMapping.NodeType.ATTRIBUTE);
        assertEquals("orig",                        original.getSourceField());
        assertEquals(FieldMapping.NodeType.ELEMENT, original.getNodeType());
    }
}
