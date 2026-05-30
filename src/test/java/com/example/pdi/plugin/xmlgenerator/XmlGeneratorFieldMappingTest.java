package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for all three field mapping node types (ELEMENT, ATTRIBUTE, CDATA),
 * multiple simultaneous mappings, unmapped fields, and null/missing values.
 */
@DisplayName("Field Mapping — Node Types")
class XmlGeneratorFieldMappingTest {

    // -----------------------------------------------------------------------
    // ELEMENT
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ELEMENT mapping creates a child element with the field value as text content")
    void element_createsChildWithTextContent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("city");
        step.addInputRow("Ottawa");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("city", "city", FieldMapping.NodeType.ELEMENT, "")
        ));

        assertEquals("Ottawa", TestableXmlStep.text(doc, "city"));
    }

    @Test
    @DisplayName("ELEMENT mapping uses xmlName not sourceField as the element tag")
    void element_usesXmlNameAsTag() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("cust_id");
        step.addInputRow("C001");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("cust_id", "customerId", FieldMapping.NodeType.ELEMENT, "")
        ));

        assertNull(doc.getElementsByTagName("cust_id").item(0),
            "Original field name should not appear as element tag");
        assertNotNull(doc.getElementsByTagName("customerId").item(0),
            "xmlName should be used as element tag");
    }

    @Test
    @DisplayName("ELEMENT: empty string value produces an empty element")
    void element_emptyValue_producesEmptyElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("desc");
        step.addInputRow("");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("desc", "desc", FieldMapping.NodeType.ELEMENT, "")
        ));

        Element el = (Element) doc.getElementsByTagName("desc").item(0);
        assertNotNull(el);
        assertEquals("", el.getTextContent());
    }

    // -----------------------------------------------------------------------
    // ATTRIBUTE
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ATTRIBUTE mapping sets an attribute on the row element (no parent path)")
    void attribute_setsAttributeOnRowElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("id");
        step.addInputRow("A1");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("id", "id", FieldMapping.NodeType.ATTRIBUTE, "")
        ));

        Element rowEl = (Element) doc.getElementsByTagName("row").item(0);
        assertEquals("A1", rowEl.getAttribute("id"));
    }

    @Test
    @DisplayName("ATTRIBUTE mapping with a parent path sets the attribute on the parent element")
    void attribute_withParentPath_setsAttributeOnParent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("type");
        step.addInputRow("PREMIUM");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("type", "type", FieldMapping.NodeType.ATTRIBUTE, "order")
        ));

        Element orderEl = (Element) doc.getElementsByTagName("order").item(0);
        assertNotNull(orderEl, "Parent element 'order' should exist");
        assertEquals("PREMIUM", orderEl.getAttribute("type"));
    }

    @Test
    @DisplayName("multiple ATTRIBUTE mappings produce multiple attributes on the same element")
    void attribute_multiple_onSameElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("firstName");
        step.addInputField("lastName");
        step.addInputRow("Alice", "Smith");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("firstName", "firstName", FieldMapping.NodeType.ATTRIBUTE, ""),
            new FieldMapping("lastName",  "lastName",  FieldMapping.NodeType.ATTRIBUTE, "")
        ));

        Element rowEl = (Element) doc.getElementsByTagName("row").item(0);
        assertEquals("Alice", rowEl.getAttribute("firstName"));
        assertEquals("Smith", rowEl.getAttribute("lastName"));
    }

    // -----------------------------------------------------------------------
    // CDATA
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CDATA mapping creates an element with a CDATA section in the raw XML")
    void cdata_rawXmlContainsCdataMarker() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("notes");
        step.addInputRow("some & <special> text");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("notes", "notes", FieldMapping.NodeType.CDATA, "")
        ));

        String xml = step.run(meta).get(0);
        assertTrue(xml.contains("<![CDATA["),
            "Expected CDATA section in output XML; got: " + xml);
    }

    @Test
    @DisplayName("CDATA: text content is preserved exactly (including special characters)")
    void cdata_preservesSpecialCharacters() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("raw");
        step.addInputRow("<tag> & \"quotes\"");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("raw", "raw", FieldMapping.NodeType.CDATA, "")
        ));

        // DOM textContent strips the CDATA wrapper; the value should be the raw string
        assertEquals("<tag> & \"quotes\"", TestableXmlStep.text(doc, "raw"));
    }

    // -----------------------------------------------------------------------
    // Multiple mappings
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("multiple field mappings all appear in the output")
    void multipleMappings_allFieldsPresent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("a");
        step.addInputField("b");
        step.addInputField("c");
        step.addInputRow("alpha", "bravo", "charlie");

        Document doc = runAndParse(step, List.of(
            new FieldMapping("a", "a", FieldMapping.NodeType.ELEMENT, ""),
            new FieldMapping("b", "b", FieldMapping.NodeType.ELEMENT, ""),
            new FieldMapping("c", "c", FieldMapping.NodeType.ELEMENT, "")
        ));

        assertEquals("alpha",   TestableXmlStep.text(doc, "a"));
        assertEquals("bravo",   TestableXmlStep.text(doc, "b"));
        assertEquals("charlie", TestableXmlStep.text(doc, "c"));
    }

    @Test
    @DisplayName("mixed node types (ELEMENT, ATTRIBUTE, CDATA) coexist in the same document")
    void mixedNodeTypes_allCoexist() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("id");
        step.addInputField("name");
        step.addInputField("bio");
        step.addInputRow("1", "Bob", "Works at <Corp>");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("id",   "id",   FieldMapping.NodeType.ATTRIBUTE, ""),
            new FieldMapping("name", "name", FieldMapping.NodeType.ELEMENT,   ""),
            new FieldMapping("bio",  "bio",  FieldMapping.NodeType.CDATA,     "")
        ));

        String raw = step.run(meta).get(0);
        Document doc = TestableXmlStep.parse(raw);

        Element rowEl = (Element) doc.getElementsByTagName("row").item(0);
        assertEquals("1",              rowEl.getAttribute("id"));
        assertEquals("Bob",            TestableXmlStep.text(doc, "name"));
        assertEquals("Works at <Corp>", TestableXmlStep.text(doc, "bio"));
    }

    // -----------------------------------------------------------------------
    // Unmapped / missing source fields
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("mapping for a source field that does not exist produces an empty element")
    void unmappedSourceField_producesEmptyElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        // No input fields registered, but a row must be queued to trigger processRow
        step.addInputRow(new Object[0]);

        Document doc = runAndParse(step, List.of(
            new FieldMapping("nonExistent", "result", FieldMapping.NodeType.ELEMENT, "")
        ));

        Element el = (Element) doc.getElementsByTagName("result").item(0);
        assertNotNull(el, "Element should still be created even if source field is absent");
        assertEquals("", el.getTextContent());
    }

    @Test
    @DisplayName("null value in input row is treated as empty string")
    void nullInputValue_treatedAsEmpty() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("optional");
        step.addInputRow(new Object[]{null});  // explicit null

        Document doc = runAndParse(step, List.of(
            new FieldMapping("optional", "optional", FieldMapping.NodeType.ELEMENT, "")
        ));

        Element el = (Element) doc.getElementsByTagName("optional").item(0);
        assertNotNull(el);
        assertEquals("", el.getTextContent());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static Document runAndParse(TestableXmlStep step, List<FieldMapping> mappings)
            throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(mappings);
        List<String> xmls = step.run(meta);
        assertFalse(xmls.isEmpty(), "No XML was generated");
        return TestableXmlStep.parse(xmls.get(0));
    }
}
