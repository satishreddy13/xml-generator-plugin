package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Tests for row-level processing behaviour: input/output row counts, output field
 * placement, field name configuration, and independence of per-row documents.
 */
@DisplayName("Row Processing")
class XmlGeneratorRowProcessingTest {

    @Test
    @DisplayName("zero input rows produce zero output rows without error")
    void zeroRows_producesZeroOutput() throws Exception {
        List<String> xmls = new TestableXmlStep().generateXmls(TestableXmlStep.defaultMeta(), 0);
        assertTrue(xmls.isEmpty(), "Zero input rows must yield zero output rows");
    }

    @Test
    @DisplayName("one input row produces exactly one output row")
    void oneRow_producesOneOutput() throws Exception {
        List<String> xmls = new TestableXmlStep().generateXmls(TestableXmlStep.defaultMeta(), 1);
        assertEquals(1, xmls.size());
    }

    @Test
    @DisplayName("N input rows produce exactly N output rows")
    void nRows_producesExactlyNOutputs() throws Exception {
        int n = 50;
        List<String> xmls = new TestableXmlStep().generateXmls(TestableXmlStep.defaultMeta(), n);
        assertEquals(n, xmls.size());
    }

    @Test
    @DisplayName("generated XML field is appended as the last field in each output row")
    void generatedXml_isLastFieldInOutputRow() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("existing");
        step.addInputRow("original_value");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("existing", "existing", FieldMapping.NodeType.ELEMENT, "")
        ));
        step.run(meta);

        Object[] outputRow = step.getOutputRows().get(0);
        // Last element should be the XML string (starts with "<?xml")
        Object last = outputRow[outputRow.length - 1];
        assertInstanceOf(String.class, last);
        assertTrue(((String) last).startsWith("<?xml"),
            "Last output field should be the generated XML string");
    }

    @Test
    @DisplayName("original input fields are preserved in the output row (before the XML field)")
    void inputFields_preservedInOutputRow() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("col1");
        step.addInputField("col2");
        step.addInputRow("value1", "value2");

        step.run(TestableXmlStep.defaultMeta());

        Object[] outputRow = step.getOutputRows().get(0);
        // Two original fields + one XML field = 3 total
        assertEquals(3, outputRow.length);
        assertEquals("value1", outputRow[0]);
        assertEquals("value2", outputRow[1]);
    }

    @Test
    @DisplayName("output field name in the row meta matches the configured outputFieldName")
    void outputFieldName_matchesConfig() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setOutputFieldName("my_xml_doc");

        org.pentaho.di.core.row.RowMeta rowMeta = new org.pentaho.di.core.row.RowMeta();
        meta.getFields(rowMeta, "step", null, null, null, null, null);

        assertEquals("my_xml_doc", rowMeta.getFieldNames()[0]);
    }

    @Test
    @DisplayName("each output row contains a valid, independently parseable XML document")
    void eachRow_containsValidXml() throws Exception {
        List<String> xmls = new TestableXmlStep().generateXmls(TestableXmlStep.defaultMeta(), 5);
        for (String xml : xmls) {
            assertDoesNotThrow(() -> TestableXmlStep.parse(xml),
                "Each output XML string should be parseable: " + xml);
        }
    }

    @Test
    @DisplayName("documents from different rows have independent content")
    void multipleRows_documentsAreIndependent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("id");
        step.addInputRow("ROW1");
        step.addInputRow("ROW2");
        step.addInputRow("ROW3");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("id", "id", FieldMapping.NodeType.ELEMENT, "")
        ));

        List<String> xmls = step.run(meta);
        assertEquals(3, xmls.size());

        assertEquals("ROW1", TestableXmlStep.text(TestableXmlStep.parse(xmls.get(0)), "id"));
        assertEquals("ROW2", TestableXmlStep.text(TestableXmlStep.parse(xmls.get(1)), "id"));
        assertEquals("ROW3", TestableXmlStep.text(TestableXmlStep.parse(xmls.get(2)), "id"));
    }

    @Test
    @DisplayName("all generated XML strings are well-formed (parseable by a standard DOM parser)")
    void allOutputs_areWellFormedXml() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("data");
        for (int i = 0; i < 20; i++) step.addInputRow("value-" + i);

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("data", "data", FieldMapping.NodeType.ELEMENT, "")
        ));

        List<String> xmls = step.run(meta);
        assertEquals(20, xmls.size());
        for (String xml : xmls) {
            Document doc = TestableXmlStep.parse(xml);
            assertNotNull(doc.getDocumentElement());
        }
    }
}
