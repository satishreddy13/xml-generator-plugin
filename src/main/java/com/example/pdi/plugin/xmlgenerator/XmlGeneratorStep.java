package com.example.pdi.plugin.xmlgenerator;

import java.io.File;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Row-processing step that appends a generated XML string to each incoming row.
 *
 * <p>XML structure per row:
 * <pre>
 *   &lt;{rootElement}&gt;
 *     &lt;{rowElement}&gt;
 *       ... mapped fields ...
 *     &lt;/{rowElement}&gt;
 *   &lt;/{rootElement}&gt;
 * </pre>
 *
 * <p>Fields can be placed as elements, CDATA-wrapped elements, or attributes.
 * An optional parent path (e.g. {@code "order/customer"}) nests the field
 * under intermediate elements created on demand.
 */
public class XmlGeneratorStep extends BaseStep implements StepInterface {

    private XmlGeneratorStepMeta meta;
    private XmlGeneratorStepData data;

    public XmlGeneratorStep(StepMeta stepMeta, StepDataInterface stepData,
            int copyNr, TransMeta transMeta, Trans trans) {
        super(stepMeta, stepData, copyNr, transMeta, trans);
    }

    @Override
    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {
        meta = (XmlGeneratorStepMeta) smi;
        data = (XmlGeneratorStepData) sdi;

        Object[] r = getRow();
        if (r == null) {
            setOutputDone();
            return false;
        }

        if (first) {
            first = false;
            data.outputRowMeta = getInputRowMeta().clone();
            meta.getFields(data.outputRowMeta, getStepname(), null, null, this, null, null);
        }

        // Append one null slot for the new XML field, then fill it
        Object[] outputRow = RowDataUtil.addValueData(r, data.outputRowMeta.size() - 1, null);

        try {
            outputRow[data.outputRowMeta.size() - 1] = generateXml(r, getInputRowMeta());
        } catch (Exception e) {
            throw new KettleException("Error generating XML for row: " + e.getMessage(), e);
        }

        putRow(data.outputRowMeta, outputRow);

        if (checkFeedback(getLinesWritten())) {
            logBasic("XML Generator processed {0} rows", getLinesWritten());
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // XML generation
    // -----------------------------------------------------------------------

    private String generateXml(Object[] row, RowMetaInterface rowMeta) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();

        // Root element
        Element rootEl = doc.createElement(meta.getRootElement());
        doc.appendChild(rootEl);

        // Row wrapper element (may be the same tag as root; if so, reuse root)
        Element rowEl;
        if (meta.getRowElement().equals(meta.getRootElement())) {
            rowEl = rootEl;
        } else {
            rowEl = doc.createElement(meta.getRowElement());
            rootEl.appendChild(rowEl);
        }

        // Map each configured field
        for (FieldMapping fm : meta.getFieldMappings()) {
            int idx = rowMeta.indexOfValue(fm.getSourceField());
            String value = (idx >= 0) ? nvl(rowMeta.getString(row, idx)) : "";
            Element parent = resolveParent(doc, rowEl, fm.getParentPath());

            switch (fm.getNodeType()) {
                case ATTRIBUTE:
                    parent.setAttribute(fm.getXmlName(), value);
                    break;
                case CDATA:
                    Element cdEl = doc.createElement(fm.getXmlName());
                    cdEl.appendChild(doc.createCDATASection(value));
                    parent.appendChild(cdEl);
                    break;
                case ELEMENT:
                default:
                    Element el = doc.createElement(fm.getXmlName());
                    el.setTextContent(value);
                    parent.appendChild(el);
                    break;
            }
        }

        // Optional XSD validation before serialising
        if (meta.getSchemaType() == XmlGeneratorStepMeta.SchemaType.XSD
                && meta.isValidateOutput()
                && meta.getSchemaPath() != null && !meta.getSchemaPath().isBlank()) {
            validateAgainstXsd(doc, meta.getSchemaPath());
        }

        String xml = serialize(doc);

        // Inject DOCTYPE declaration for DTD mode after the XML processing instruction.
        // Done as a string operation because javax.xml.transform.Transformer does not
        // reliably serialise DocumentType nodes across JDK versions.
        if (meta.getSchemaType() == XmlGeneratorStepMeta.SchemaType.DTD) {
            xml = injectDoctype(xml);
        }

        return xml;
    }

    /**
     * Injects a {@code <!DOCTYPE …>} declaration into an already-serialised XML
     * string immediately after the {@code <?xml … ?>} processing instruction.
     * If neither Public ID nor System ID is configured, the string is returned unchanged.
     */
    private String injectDoctype(String xml) {
        String pubId = blankToNull(meta.getDtdPublicId());
        String sysId = blankToNull(meta.getDtdSystemId());
        if (pubId == null && sysId == null) return xml;

        StringBuilder doctype = new StringBuilder("<!DOCTYPE ").append(meta.getRootElement());
        if (pubId != null) {
            doctype.append(" PUBLIC \"").append(pubId)
                   .append("\" \"").append(sysId != null ? sysId : "").append("\"");
        } else {
            doctype.append(" SYSTEM \"").append(sysId).append("\"");
        }
        doctype.append(">");

        int pos = xml.indexOf("?>") + 2;
        return xml.substring(0, pos) + System.lineSeparator() + doctype + xml.substring(pos);
    }

    /**
     * Walks (or creates) intermediate elements along {@code path} under {@code base}.
     * Path segments are separated by {@code /}; empty path returns {@code base}.
     */
    private Element resolveParent(Document doc, Element base, String path) {
        if (path == null || path.isBlank()) return base;
        Element current = base;
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) continue;
            NodeList children = current.getChildNodes();
            Element found = null;
            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child instanceof Element && segment.equals(child.getNodeName())) {
                    found = (Element) child;
                    break;
                }
            }
            if (found == null) {
                found = doc.createElement(segment);
                current.appendChild(found);
            }
            current = found;
        }
        return current;
    }

    private void validateAgainstXsd(Document doc, String xsdPath) throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(new File(xsdPath));
        Validator validator = schema.newValidator();
        validator.validate(new DOMSource(doc));
    }

    private String serialize(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, meta.getEncoding());
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        // Tell the serialiser which element names carry CDATA sections so it
        // emits <![CDATA[...]]> instead of escaping the text content.
        String cdataElements = buildCdataElementList();
        if (!cdataElements.isEmpty()) {
            t.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, cdataElements);
        }

        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    /** Space-separated list of element names used with CDATA node type. */
    private String buildCdataElementList() {
        StringBuilder sb = new StringBuilder();
        for (FieldMapping fm : meta.getFieldMappings()) {
            if (fm.getNodeType() == FieldMapping.NodeType.CDATA) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(fm.getXmlName());
            }
        }
        return sb.toString();
    }

    private static String nvl(String s) { return s != null ? s : ""; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s; }
}
