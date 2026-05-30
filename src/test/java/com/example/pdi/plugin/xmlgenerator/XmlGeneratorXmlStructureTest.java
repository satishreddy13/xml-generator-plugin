package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for the overall XML document structure produced by XmlGeneratorStep:
 * XML declaration, root/row element names, encoding, and empty mappings.
 */
@DisplayName("XML Document Structure")
class XmlGeneratorXmlStructureTest {

    @Test
    @DisplayName("output contains an XML declaration")
    void output_containsXmlDeclaration() throws Exception {
        String xml = single(TestableXmlStep.defaultMeta());
        assertTrue(xml.startsWith("<?xml"),
            "Expected XML declaration at start; got: " + xml.substring(0, Math.min(40, xml.length())));
    }

    @Test
    @DisplayName("root element name matches config")
    void rootElement_matchesConfig() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setRootElement("document");
        Document doc = TestableXmlStep.parse(single(meta));
        assertEquals("document", doc.getDocumentElement().getNodeName());
    }

    @Test
    @DisplayName("row element is a direct child of the root element")
    void rowElement_isChildOfRoot() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setRootElement("root");
        meta.setRowElement("record");
        Document doc = TestableXmlStep.parse(single(meta));
        Element root = doc.getDocumentElement();
        NodeList children = root.getElementsByTagName("record");
        assertTrue(children.getLength() >= 1,
            "Expected at least one <record> element under root");
    }

    @Test
    @DisplayName("row element name matches config")
    void rowElement_nameMatchesConfig() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setRowElement("item");
        Document doc = TestableXmlStep.parse(single(meta));
        assertNotNull(doc.getElementsByTagName("item").item(0),
            "Expected <item> element in output");
    }

    @Test
    @DisplayName("when root and row element names are the same, only one level is created")
    void sameRootAndRowElement_onlyOneLevel() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setRootElement("item");
        meta.setRowElement("item");
        Document doc = TestableXmlStep.parse(single(meta));
        // The document element itself is <item>; there should be no nested <item>
        assertEquals("item", doc.getDocumentElement().getNodeName());
        NodeList nested = doc.getDocumentElement().getElementsByTagName("item");
        assertEquals(0, nested.getLength(),
            "No nested <item> expected when root == row element");
    }

    @Test
    @DisplayName("encoding declared in XML declaration matches config")
    void encoding_appearsInXmlDeclaration() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setEncoding("UTF-16");
        String xml = single(meta);
        assertTrue(xml.contains("UTF-16"),
            "Expected UTF-16 in XML declaration; got: " + xml.substring(0, Math.min(80, xml.length())));
    }

    @Test
    @DisplayName("output with no field mappings produces an empty row element")
    void noMappings_producesEmptyRowElement() throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of());
        Document doc = TestableXmlStep.parse(single(meta));
        Element rowEl = (Element) doc.getElementsByTagName("row").item(0);
        assertNotNull(rowEl, "Row element must still be present");
        assertEquals(0, rowEl.getChildNodes().getLength(),
            "Row element should have no children when no mappings are configured");
    }

    @Test
    @DisplayName("each input row produces a separate, independent XML document")
    void multipleRows_eachHasOwnDocument() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("val");
        step.addInputRow("first");
        step.addInputRow("second");

        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(List.of(
            new FieldMapping("val", "val", FieldMapping.NodeType.ELEMENT, "")
        ));

        List<String> xmls = step.run(meta);
        assertEquals(2, xmls.size());

        Document d1 = TestableXmlStep.parse(xmls.get(0));
        Document d2 = TestableXmlStep.parse(xmls.get(1));
        assertEquals("first",  TestableXmlStep.text(d1, "val"));
        assertEquals("second", TestableXmlStep.text(d2, "val"));
    }

    // ---- helper ----

    private static String single(XmlGeneratorStepMeta meta) throws Exception {
        List<String> xmls = new TestableXmlStep().generateXmls(meta, 1);
        assertFalse(xmls.isEmpty());
        return xmls.get(0);
    }
}
