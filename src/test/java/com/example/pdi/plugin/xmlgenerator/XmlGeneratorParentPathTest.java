package com.example.pdi.plugin.xmlgenerator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Tests for the parent-path nesting feature: fields placed at single-level and
 * multi-level paths, path reuse (shared parent element), and attributes on
 * nested elements.
 */
@DisplayName("Parent Path Nesting")
class XmlGeneratorParentPathTest {

    @Test
    @DisplayName("empty parent path places field directly under the row element")
    void emptyPath_fieldUnderRowElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("val");
        step.addInputRow("direct");

        Document doc = run(step, List.of(
            new FieldMapping("val", "val", FieldMapping.NodeType.ELEMENT, "")
        ));

        Element rowEl = (Element) doc.getElementsByTagName("row").item(0);
        NodeList children = rowEl.getChildNodes();
        boolean found = false;
        for (int i = 0; i < children.getLength(); i++) {
            if ("val".equals(children.item(i).getNodeName())) { found = true; break; }
        }
        assertTrue(found, "<val> should be a direct child of <row>");
    }

    @Test
    @DisplayName("single-level path creates one intermediate element")
    void singleLevel_createsIntermediateElement() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("street");
        step.addInputRow("123 Main St");

        Document doc = run(step, List.of(
            new FieldMapping("street", "street", FieldMapping.NodeType.ELEMENT, "address")
        ));

        Element address = (Element) doc.getElementsByTagName("address").item(0);
        assertNotNull(address, "<address> intermediate element should exist");
        assertEquals("123 Main St", TestableXmlStep.text(doc, "street"));
    }

    @Test
    @DisplayName("two-level path creates the full path of intermediate elements")
    void twoLevel_createsBothIntermediateElements() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("city");
        step.addInputRow("Ottawa");

        Document doc = run(step, List.of(
            new FieldMapping("city", "city", FieldMapping.NodeType.ELEMENT, "address/geo")
        ));

        assertNotNull(doc.getElementsByTagName("address").item(0), "<address> should exist");
        assertNotNull(doc.getElementsByTagName("geo").item(0),     "<geo> should exist");
        assertEquals("Ottawa", TestableXmlStep.text(doc, "city"));
    }

    @Test
    @DisplayName("two fields sharing the same parent path reuse the same parent element")
    void sharedParentPath_sameElementInstance() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("first");
        step.addInputField("last");
        step.addInputRow("Alice", "Smith");

        Document doc = run(step, List.of(
            new FieldMapping("first", "first", FieldMapping.NodeType.ELEMENT, "name"),
            new FieldMapping("last",  "last",  FieldMapping.NodeType.ELEMENT, "name")
        ));

        // Should be exactly one <name> element containing both children
        NodeList nameNodes = doc.getElementsByTagName("name");
        assertEquals(1, nameNodes.getLength(),
            "Two fields sharing path 'name' should produce exactly one <name> element");

        Element nameEl = (Element) nameNodes.item(0);
        assertEquals("Alice", nameEl.getElementsByTagName("first").item(0).getTextContent());
        assertEquals("Smith", nameEl.getElementsByTagName("last").item(0).getTextContent());
    }

    @Test
    @DisplayName("attribute placed on a nested parent via parent path")
    void attribute_onNestedParent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("currency");
        step.addInputRow("CAD");

        Document doc = run(step, List.of(
            new FieldMapping("currency", "currency", FieldMapping.NodeType.ATTRIBUTE, "price")
        ));

        Element priceEl = (Element) doc.getElementsByTagName("price").item(0);
        assertNotNull(priceEl, "<price> parent element should exist");
        assertEquals("CAD", priceEl.getAttribute("currency"));
    }

    @Test
    @DisplayName("fields at different nesting depths coexist correctly")
    void mixedDepths_allElementsPresent() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("orderId");
        step.addInputField("custName");
        step.addInputField("itemDesc");
        step.addInputRow("ORD-1", "Bob", "Widget");

        Document doc = run(step, List.of(
            new FieldMapping("orderId",  "orderId",     FieldMapping.NodeType.ELEMENT, ""),
            new FieldMapping("custName", "name",        FieldMapping.NodeType.ELEMENT, "customer"),
            new FieldMapping("itemDesc", "description", FieldMapping.NodeType.ELEMENT, "lines/line")
        ));

        assertEquals("ORD-1",  TestableXmlStep.text(doc, "orderId"));
        assertEquals("Bob",    TestableXmlStep.text(doc, "name"));
        assertEquals("Widget", TestableXmlStep.text(doc, "description"));
        assertNotNull(doc.getElementsByTagName("customer").item(0));
        assertNotNull(doc.getElementsByTagName("lines").item(0));
        assertNotNull(doc.getElementsByTagName("line").item(0));
    }

    @Test
    @DisplayName("leading/trailing slashes in parent path are handled without creating empty elements")
    void path_withLeadingSlash_handledGracefully() throws Exception {
        TestableXmlStep step = new TestableXmlStep();
        step.addInputField("x");
        step.addInputRow("value");

        // Leading slash produces an empty segment which resolveParent skips
        Document doc = run(step, List.of(
            new FieldMapping("x", "x", FieldMapping.NodeType.ELEMENT, "/parent")
        ));

        assertNotNull(doc.getElementsByTagName("parent").item(0),
            "Valid 'parent' segment should still be created");
        assertEquals("value", TestableXmlStep.text(doc, "x"));
    }

    // ---- helper ----

    private static Document run(TestableXmlStep step, List<FieldMapping> mappings) throws Exception {
        XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
        meta.setFieldMappings(mappings);
        List<String> xmls = step.run(meta);
        assertFalse(xmls.isEmpty());
        return TestableXmlStep.parse(xmls.get(0));
    }
}
