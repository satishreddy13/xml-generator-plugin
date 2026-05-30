package com.example.pdi.plugin.xmlgenerator;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import javax.xml.parsers.DocumentBuilderFactory;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

/**
 * Test harness: a minimal subclass of XmlGeneratorStep that
 * supplies controlled input rows and captures output rows —
 * no PDI runtime installation required.
 *
 * Usage:
 * <pre>
 *   TestableXmlStep step = new TestableXmlStep();
 *   step.addInputField("firstName");
 *   step.addInputField("remarks");
 *   step.addInputRow("Alice", "some &amp; special text");
 *
 *   XmlGeneratorStepMeta meta = TestableXmlStep.defaultMeta();
 *   meta.setFieldMappings(List.of(
 *       new FieldMapping("firstName", "firstName", FieldMapping.NodeType.ELEMENT, ""),
 *       new FieldMapping("remarks",   "remarks",   FieldMapping.NodeType.CDATA,   "")
 *   ));
 *
 *   List&lt;String&gt; xmls = step.run(meta);
 *   Document doc = TestableXmlStep.parse(xmls.get(0));
 * </pre>
 */
class TestableXmlStep extends XmlGeneratorStep {

    private final Queue<Object[]> inputQueue = new LinkedList<>();
    private final List<Object[]>  outputRows = new ArrayList<>();
    private final RowMeta         inputMeta  = new RowMeta();

    TestableXmlStep() {
        super(stepMeta(), new XmlGeneratorStepData(), 0, new TransMeta(), new Trans());
    }

    // ---- input setup ----

    /** Register an input field name (must be called before addInputRow). */
    void addInputField(String fieldName) throws KettleStepException {
        inputMeta.addValueMeta(new ValueMetaString(fieldName));
    }

    /** Queue a row whose values correspond positionally to the registered input fields. */
    void addInputRow(Object... values) {
        inputQueue.add(values);
    }

    // ---- run ----

    /**
     * Run the full lifecycle (init → processRow loop → dispose) consuming whatever
     * rows are currently in the queue.  Returns the generated XML strings.
     */
    List<String> run(XmlGeneratorStepMeta meta) throws KettleException {
        return lifecycle(meta);
    }

    /**
     * Queue {@code count} empty input rows, run the full lifecycle, and return
     * the generated XML strings — convenient for structure / schema tests where
     * no field values are needed.
     */
    List<String> generateXmls(XmlGeneratorStepMeta meta, int count) throws KettleException {
        for (int i = 0; i < count; i++) inputQueue.add(new Object[0]);
        return lifecycle(meta);
    }

    private List<String> lifecycle(XmlGeneratorStepMeta meta) throws KettleException {
        XmlGeneratorStepData data = new XmlGeneratorStepData();
        init(meta, data);
        while (processRow(meta, data)) { /* process */ }
        dispose(meta, data);
        List<String> results = new ArrayList<>(outputRows.size());
        for (Object[] row : outputRows) {
            results.add((String) row[row.length - 1]);
        }
        return results;
    }

    // ---- BaseStep overrides ----

    @Override public Object[]          getRow()            { return inputQueue.poll(); }
    @Override public RowMetaInterface  getInputRowMeta()   { return inputMeta; }
    @Override public void              putRow(RowMetaInterface rm, Object[] row) { outputRows.add(row); }

    List<Object[]> getOutputRows() { return outputRows; }

    // ---- static factories ----

    static XmlGeneratorStepMeta defaultMeta() {
        XmlGeneratorStepMeta m = new XmlGeneratorStepMeta();
        m.setDefault();
        return m;
    }

    private static StepMeta stepMeta() {
        StepMeta sm = new StepMeta();
        sm.setName("TestXmlStep");
        return sm;
    }

    // ---- XML parsing helper ----

    /** Parse an XML string into a DOM Document for assertions. */
    static Document parse(String xml) throws Exception {
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));
    }

    /** Return the text content of the first element matching {@code tagName}. */
    static String text(Document doc, String tagName) {
        var nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }
}
