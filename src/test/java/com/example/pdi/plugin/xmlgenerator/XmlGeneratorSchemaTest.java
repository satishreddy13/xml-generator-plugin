package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pentaho.di.core.exception.KettleException;
import org.w3c.dom.Document;

/**
 * Tests for the Schema tab: XSD validation (pass and fail), DTD DOCTYPE
 * declaration generation, and NONE mode (no schema reference).
 *
 * The XSD tests use src/test/resources/test-schema.xsd which requires:
 *   &lt;root&gt;&lt;row&gt;&lt;id&gt;...&lt;/id&gt;&lt;name&gt;...&lt;/name&gt;&lt;/row&gt;&lt;/root&gt;
 */
@DisplayName("Schema — XSD and DTD")
class XmlGeneratorSchemaTest {

    // -----------------------------------------------------------------------
    // NONE mode
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("NONE mode: output contains no DOCTYPE declaration")
    void none_noDoctype() throws Exception {
        String xml = singleXml(TestableXmlStep.defaultMeta());
        assertFalse(xml.contains("<!DOCTYPE"),
            "NONE schema mode should not produce a DOCTYPE declaration");
    }

    // -----------------------------------------------------------------------
    // XSD mode — validation disabled (path set but validateOutput = false)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("XSD mode with validateOutput=false: output is produced without validation")
    void xsd_validateFalse_outputProduced() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.XSD);
        meta.setSchemaPath(xsdPath());
        meta.setValidateOutput(false);  // do not actually validate

        assertDoesNotThrow(() -> new TestableXmlStep().generateXmls(meta, 1),
            "Should produce output without throwing when validation is disabled");
    }

    // -----------------------------------------------------------------------
    // XSD mode — validation enabled, document is valid
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("XSD validation passes for a document that conforms to the schema")
    void xsd_validDocument_noException() throws Exception {
        TestableXmlStep step = withIdAndName("X001", "Alice");

        XmlGeneratorStepMeta meta = xsdMeta(true);
        List<String> xmls = step.run(meta);
        assertEquals(1, xmls.size(), "Expected one output row");

        // Additional sanity: the output is well-formed XML
        Document doc = TestableXmlStep.parse(xmls.get(0));
        assertNotNull(doc.getElementsByTagName("id").item(0));
        assertNotNull(doc.getElementsByTagName("name").item(0));
    }

    // -----------------------------------------------------------------------
    // XSD mode — validation enabled, document is INVALID
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("XSD validation fails for a document that violates the schema → KettleException")
    void xsd_invalidDocument_throwsKettleException() throws Exception {
        // Schema requires both <id> and <name>; we only map <id>
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("id");
        step.addInputRow("MISSING_NAME");

        XmlGeneratorStepMeta meta = xsdMeta(true);
        meta.setFieldMappings(List.of(
            new FieldMapping("id", "id", FieldMapping.NodeType.ELEMENT, "")
            // "name" deliberately omitted → invalid against the XSD
        ));

        assertThrows(KettleException.class, () -> step.run(meta),
            "Validation against XSD should throw KettleException for invalid document");
    }

    @Test
    @DisplayName("XSD validation: schema path pointing to a non-existent file → KettleException")
    void xsd_missingSchemaFile_throwsKettleException() throws Exception {
        TestableXmlStep step = withIdAndName("X001", "Bob");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.XSD);
        meta.setSchemaPath("/no/such/file.xsd");
        meta.setValidateOutput(true);
        meta.setFieldMappings(List.of(
            new FieldMapping("id",   "id",   FieldMapping.NodeType.ELEMENT, ""),
            new FieldMapping("name", "name", FieldMapping.NodeType.ELEMENT, "")
        ));

        assertThrows(KettleException.class, () -> step.run(meta));
    }

    // -----------------------------------------------------------------------
    // DTD mode — DOCTYPE declaration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("DTD mode with SYSTEM ID produces a DOCTYPE declaration in the output")
    void dtd_systemId_docTypePresent() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        meta.setDtdSystemId("http://example.com/test.dtd");

        String xml = singleXml(meta);
        assertTrue(xml.contains("<!DOCTYPE"),
            "Expected DOCTYPE declaration in DTD mode; got: " + xml.substring(0, Math.min(120, xml.length())));
        assertTrue(xml.contains("test.dtd"),
            "Expected system ID in DOCTYPE declaration");
    }

    @Test
    @DisplayName("DTD mode with PUBLIC + SYSTEM ID both appear in the DOCTYPE declaration")
    void dtd_publicAndSystemId_bothInDocType() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        meta.setDtdPublicId("-//EXAMPLE//DTD Test//EN");
        meta.setDtdSystemId("http://example.com/test.dtd");

        String xml = singleXml(meta);
        assertTrue(xml.contains("-//EXAMPLE//DTD Test//EN"),
            "PUBLIC identifier should appear in DOCTYPE");
        assertTrue(xml.contains("test.dtd"),
            "SYSTEM identifier should appear in DOCTYPE");
    }

    @Test
    @DisplayName("DTD mode with neither Public nor System ID produces no DOCTYPE")
    void dtd_noIds_noDocType() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        meta.setDtdPublicId("");
        meta.setDtdSystemId("");

        String xml = singleXml(meta);
        assertFalse(xml.contains("<!DOCTYPE"),
            "No DOCTYPE expected when both Public and System IDs are blank");
    }

    @Test
    @DisplayName("DTD mode DOCTYPE uses the configured root element name")
    void dtd_docTypeUsesRootElementName() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setRootElement("catalog");
        meta.setRowElement("item");
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        meta.setDtdSystemId("catalog.dtd");

        String xml = singleXml(meta);
        assertTrue(xml.contains("<!DOCTYPE catalog"),
            "DOCTYPE should reference the root element name 'catalog'; got: " + xml.substring(0, Math.min(120, xml.length())));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String singleXml(XmlGeneratorStepMeta meta) throws Exception {
        List<String> xmls = new TestableXmlStep().generateXmls(meta, 1);
        assertFalse(xmls.isEmpty());
        return xmls.get(0);
    }

    /** Step pre-loaded with "id" and "name" input fields and one row. */
    private static TestableXmlStep withIdAndName(String id, String name) throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("id");
        step.addInputField("name");
        step.addInputRow(id, name);
        return step;
    }

    /** Meta configured for XSD validation using the bundled test schema. */
    private static XmlGeneratorStepMeta xsdMeta(boolean validate) {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setSchemaType(XmlGeneratorStepMeta.SchemaType.XSD);
        meta.setSchemaPath(xsdPath());
        meta.setValidateOutput(validate);
        meta.setFieldMappings(List.of(
            new FieldMapping("id",   "id",   FieldMapping.NodeType.ELEMENT, ""),
            new FieldMapping("name", "name", FieldMapping.NodeType.ELEMENT, "")
        ));
        return meta;
    }

    private static String xsdPath() {
        URL url = XmlGeneratorSchemaTest.class.getClassLoader().getResource("test-schema.xsd");
        assertNotNull(url, "test-schema.xsd must be on the test classpath");
        return new File(url.getPath()).getAbsolutePath();
    }
}
