package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.w3c.dom.Node;

/**
 * Tests for XmlGeneratorStepMeta: defaults, XML round-trip, clone, getFields, check().
 */
@DisplayName("XmlGeneratorStepMeta")
class XmlGeneratorStepMetaTest {

    private XmlGeneratorStepMeta meta;

    @BeforeEach
    void setUp() {
        meta = new XmlGeneratorStepMeta();
        meta.setDefault();
    }

    // -----------------------------------------------------------------------
    // Default values
    // -----------------------------------------------------------------------

    @Test @DisplayName("setDefault sets outputFieldName to xml_output")
    void setDefault_outputFieldName() { assertEquals("xml_output", meta.getOutputFieldName()); }

    @Test @DisplayName("setDefault sets encoding to UTF-8")
    void setDefault_encoding() { assertEquals("UTF-8", meta.getEncoding()); }

    @Test @DisplayName("setDefault sets rootElement to root")
    void setDefault_rootElement() { assertEquals("root", meta.getRootElement()); }

    @Test @DisplayName("setDefault sets rowElement to row")
    void setDefault_rowElement() { assertEquals("row", meta.getRowElement()); }

    @Test @DisplayName("setDefault sets schemaType to NONE")
    void setDefault_schemaType() { assertEquals(XmlGeneratorStepMeta.SchemaType.NONE, meta.getSchemaType()); }

    @Test @DisplayName("setDefault sets validateOutput to false")
    void setDefault_validateOutput() { assertFalse(meta.isValidateOutput()); }

    @Test @DisplayName("setDefault initialises fieldMappings to an empty list")
    void setDefault_fieldMappings_isEmpty() {
        assertNotNull(meta.getFieldMappings());
        assertTrue(meta.getFieldMappings().isEmpty());
    }

    // -----------------------------------------------------------------------
    // XML round-trip – scalar fields
    // -----------------------------------------------------------------------

    @Test @DisplayName("round-trip preserves outputFieldName")
    void xmlRoundtrip_outputFieldName() throws Exception {
        meta.setOutputFieldName("my_xml");
        assertEquals("my_xml", roundTrip(meta).getOutputFieldName());
    }

    @Test @DisplayName("round-trip preserves encoding")
    void xmlRoundtrip_encoding() throws Exception {
        meta.setEncoding("UTF-16");
        assertEquals("UTF-16", roundTrip(meta).getEncoding());
    }

    @Test @DisplayName("round-trip preserves rootElement")
    void xmlRoundtrip_rootElement() throws Exception {
        meta.setRootElement("document");
        assertEquals("document", roundTrip(meta).getRootElement());
    }

    @Test @DisplayName("round-trip preserves rowElement")
    void xmlRoundtrip_rowElement() throws Exception {
        meta.setRowElement("record");
        assertEquals("record", roundTrip(meta).getRowElement());
    }

    @Test @DisplayName("round-trip preserves schemaType XSD")
    void xmlRoundtrip_schemaType_xsd() throws Exception {
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.XSD);
        assertEquals(XmlGeneratorStepMeta.SchemaType.XSD, roundTrip(meta).getSchemaType());
    }

    @Test @DisplayName("round-trip preserves schemaType DTD")
    void xmlRoundtrip_schemaType_dtd() throws Exception {
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        assertEquals(XmlGeneratorStepMeta.SchemaType.DTD, roundTrip(meta).getSchemaType());
    }

    @Test @DisplayName("round-trip preserves schemaPath")
    void xmlRoundtrip_schemaPath() throws Exception {
        meta.setSchemaPath("/tmp/schema.xsd");
        assertEquals("/tmp/schema.xsd", roundTrip(meta).getSchemaPath());
    }

    @Test @DisplayName("round-trip preserves validateOutput flag")
    void xmlRoundtrip_validateOutput() throws Exception {
        meta.setValidateOutput(true);
        assertTrue(roundTrip(meta).isValidateOutput());
    }

    @Test @DisplayName("round-trip preserves dtdPublicId")
    void xmlRoundtrip_dtdPublicId() throws Exception {
        meta.setDtdPublicId("-//TEST//DTD");
        assertEquals("-//TEST//DTD", roundTrip(meta).getDtdPublicId());
    }

    @Test @DisplayName("round-trip preserves dtdSystemId")
    void xmlRoundtrip_dtdSystemId() throws Exception {
        meta.setDtdSystemId("http://example.com/test.dtd");
        assertEquals("http://example.com/test.dtd", roundTrip(meta).getDtdSystemId());
    }

    // -----------------------------------------------------------------------
    // XML round-trip – fieldMappings list
    // -----------------------------------------------------------------------

    @Test @DisplayName("round-trip preserves a field mapping's sourceField and xmlName")
    void xmlRoundtrip_fieldMapping_names() throws Exception {
        meta.setFieldMappings(List.of(new FieldMapping("src", "xml", FieldMapping.NodeType.ELEMENT, "")));
        XmlGeneratorStepMeta restored = roundTrip(meta);
        assertEquals(1,     restored.getFieldMappings().size());
        assertEquals("src", restored.getFieldMappings().get(0).getSourceField());
        assertEquals("xml", restored.getFieldMappings().get(0).getXmlName());
    }

    @Test @DisplayName("round-trip preserves field mapping node type")
    void xmlRoundtrip_fieldMapping_nodeType() throws Exception {
        meta.setFieldMappings(List.of(new FieldMapping("f", "e", FieldMapping.NodeType.CDATA, "")));
        assertEquals(FieldMapping.NodeType.CDATA,
            roundTrip(meta).getFieldMappings().get(0).getNodeType());
    }

    @Test @DisplayName("round-trip preserves field mapping parent path")
    void xmlRoundtrip_fieldMapping_parentPath() throws Exception {
        meta.setFieldMappings(List.of(new FieldMapping("f", "e", FieldMapping.NodeType.ELEMENT, "a/b")));
        assertEquals("a/b", roundTrip(meta).getFieldMappings().get(0).getParentPath());
    }

    @Test @DisplayName("round-trip preserves count of multiple field mappings")
    void xmlRoundtrip_multipleMappings_countPreserved() throws Exception {
        meta.setFieldMappings(List.of(
            new FieldMapping("a", "x", FieldMapping.NodeType.ELEMENT,   ""),
            new FieldMapping("b", "y", FieldMapping.NodeType.ATTRIBUTE, ""),
            new FieldMapping("c", "z", FieldMapping.NodeType.CDATA,     "p")
        ));
        assertEquals(3, roundTrip(meta).getFieldMappings().size());
    }

    // -----------------------------------------------------------------------
    // Clone
    // -----------------------------------------------------------------------

    @Test @DisplayName("clone() returns a distinct instance")
    void clone_isDistinctInstance() {
        assertNotSame(meta, meta.clone());
    }

    @Test @DisplayName("clone() copies scalar fields")
    void clone_copiesScalarFields() {
        meta.setOutputFieldName("cloned_out");
        meta.setRootElement("clonedRoot");
        XmlGeneratorStepMeta c = (XmlGeneratorStepMeta) meta.clone();
        assertEquals("cloned_out",  c.getOutputFieldName());
        assertEquals("clonedRoot",  c.getRootElement());
    }

    @Test @DisplayName("clone() fieldMappings list is an independent copy")
    void clone_fieldMappings_isIndependentList() {
        meta.setFieldMappings(new ArrayList<>(List.of(
            new FieldMapping("f", "e", FieldMapping.NodeType.ELEMENT, "")
        )));
        XmlGeneratorStepMeta c = (XmlGeneratorStepMeta) meta.clone();
        c.getFieldMappings().add(new FieldMapping("extra", "extra", FieldMapping.NodeType.ELEMENT, ""));
        assertEquals(1, meta.getFieldMappings().size(), "Original list should not grow");
    }

    // -----------------------------------------------------------------------
    // getFields
    // -----------------------------------------------------------------------

    @Test @DisplayName("getFields appends exactly one field")
    void getFields_appendsOneField() throws Exception {
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "origin", null, null, null, null, null);
        assertEquals(1, rowMeta.size());
    }

    @Test @DisplayName("getFields appended field name matches outputFieldName config")
    void getFields_fieldNameMatchesConfig() throws Exception {
        meta.setOutputFieldName("my_xml_out");
        RowMeta rowMeta = new RowMeta();
        meta.getFields(rowMeta, "origin", null, null, null, null, null);
        assertEquals("my_xml_out", rowMeta.getFieldNames()[0]);
    }

    // -----------------------------------------------------------------------
    // check()
    // -----------------------------------------------------------------------

    @Test @DisplayName("check() adds error when outputFieldName is null")
    void check_nullOutputFieldName_addsError() {
        meta.setOutputFieldName(null);
        List<CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_ERROR));
    }

    @Test @DisplayName("check() adds error when outputFieldName is blank")
    void check_blankOutputFieldName_addsError() {
        meta.setOutputFieldName("   ");
        List<CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_ERROR));
    }

    @Test @DisplayName("check() adds OK when outputFieldName is set")
    void check_validOutputFieldName_addsOk() {
        List<CheckResultInterface> remarks = new ArrayList<>();
        meta.check(remarks, null, new StepMeta(), null, null, null, null, null, null, null);
        assertTrue(remarks.stream().anyMatch(r -> r.getType() == CheckResultInterface.TYPE_RESULT_OK));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static XmlGeneratorStepMeta roundTrip(XmlGeneratorStepMeta src) throws Exception {
        String fragment = src.getXML();
        Node stepNode = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(
                ("<step>" + fragment + "</step>").getBytes(StandardCharsets.UTF_8)))
            .getDocumentElement();
        XmlGeneratorStepMeta restored = new XmlGeneratorStepMeta();
        restored.loadXML(stepNode, null, null);
        return restored;
    }
}
