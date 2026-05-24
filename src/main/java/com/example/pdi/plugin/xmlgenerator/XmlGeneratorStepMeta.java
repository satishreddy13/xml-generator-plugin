package com.example.pdi.plugin.xmlgenerator;

import java.util.ArrayList;
import java.util.List;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

@Step(
  id          = "XmlGeneratorStep",
  name        = "XML Generator",
  description = "Generates an XML document per row from mapped fields, with optional XSD or DTD support",
  categoryDescription = "i18n:org.pentaho.di.trans.step:BaseStep.Category.Transform",
  image       = "ui/images/XML.svg"
)
public class XmlGeneratorStepMeta extends BaseStepMeta implements StepMetaInterface {

    public enum SchemaType { NONE, XSD, DTD }

    // ---- configurable fields ----
    private String     outputFieldName = "xml_output";
    private String     encoding        = "UTF-8";
    private String     rootElement     = "root";
    private String     rowElement      = "row";
    private SchemaType schemaType      = SchemaType.NONE;
    private String     schemaPath      = "";
    private boolean    validateOutput  = false;
    private String     dtdPublicId     = "";
    private String     dtdSystemId     = "";
    private List<FieldMapping> fieldMappings = new ArrayList<>();

    // ---- accessors ----
    public String     getOutputFieldName()              { return outputFieldName; }
    public void       setOutputFieldName(String v)      { outputFieldName = v; }
    public String     getEncoding()                     { return encoding; }
    public void       setEncoding(String v)             { encoding = v; }
    public String     getRootElement()                  { return rootElement; }
    public void       setRootElement(String v)          { rootElement = v; }
    public String     getRowElement()                   { return rowElement; }
    public void       setRowElement(String v)           { rowElement = v; }
    public SchemaType getSchemaType()                   { return schemaType; }
    public void       setSchemaType(SchemaType v)       { schemaType = v; }
    public String     getSchemaPath()                   { return schemaPath; }
    public void       setSchemaPath(String v)           { schemaPath = v; }
    public boolean    isValidateOutput()                { return validateOutput; }
    public void       setValidateOutput(boolean v)      { validateOutput = v; }
    public String     getDtdPublicId()                  { return dtdPublicId; }
    public void       setDtdPublicId(String v)          { dtdPublicId = v; }
    public String     getDtdSystemId()                  { return dtdSystemId; }
    public void       setDtdSystemId(String v)          { dtdSystemId = v; }
    public List<FieldMapping> getFieldMappings()        { return fieldMappings; }
    public void       setFieldMappings(List<FieldMapping> v) { fieldMappings = v; }

    // ---- lifecycle ----

    @Override
    public void setDefault() {
        outputFieldName = "xml_output";
        encoding        = "UTF-8";
        rootElement     = "root";
        rowElement      = "row";
        schemaType      = SchemaType.NONE;
        schemaPath      = "";
        validateOutput  = false;
        dtdPublicId     = "";
        dtdSystemId     = "";
        fieldMappings   = new ArrayList<>();
    }

    @Override
    public Object clone() {
        XmlGeneratorStepMeta clone = (XmlGeneratorStepMeta) super.clone();
        clone.fieldMappings = new ArrayList<>();
        for (FieldMapping fm : fieldMappings) clone.fieldMappings.add(fm.clone());
        return clone;
    }

    @Override
    public void getFields(RowMetaInterface rowMeta, String origin,
            RowMetaInterface[] info, StepMeta nextStep,
            VariableSpace space, Repository repository, IMetaStore metaStore)
            throws KettleStepException {
        ValueMetaString v = new ValueMetaString(outputFieldName);
        v.setOrigin(origin);
        rowMeta.addValueMeta(v);
    }

    // ---- XML serialisation ----

    @Override
    public String getXML() throws KettleException {
        StringBuilder sb = new StringBuilder();
        sb.append(XMLHandler.addTagValue("outputFieldName", outputFieldName));
        sb.append(XMLHandler.addTagValue("encoding",        encoding));
        sb.append(XMLHandler.addTagValue("rootElement",     rootElement));
        sb.append(XMLHandler.addTagValue("rowElement",      rowElement));
        sb.append(XMLHandler.addTagValue("schemaType",      schemaType.name()));
        sb.append(XMLHandler.addTagValue("schemaPath",      schemaPath));
        sb.append(XMLHandler.addTagValue("validateOutput",  String.valueOf(validateOutput)));
        sb.append(XMLHandler.addTagValue("dtdPublicId",     dtdPublicId));
        sb.append(XMLHandler.addTagValue("dtdSystemId",     dtdSystemId));
        sb.append("  <fieldMappings>").append(System.lineSeparator());
        for (FieldMapping fm : fieldMappings) {
            sb.append("    <mapping>").append(System.lineSeparator());
            sb.append(XMLHandler.addTagValue("sourceField", fm.getSourceField()));
            sb.append(XMLHandler.addTagValue("xmlName",     fm.getXmlName()));
            sb.append(XMLHandler.addTagValue("nodeType",    fm.getNodeType().name()));
            sb.append(XMLHandler.addTagValue("parentPath",  fm.getParentPath()));
            sb.append("    </mapping>").append(System.lineSeparator());
        }
        sb.append("  </fieldMappings>").append(System.lineSeparator());
        return sb.toString();
    }

    @Override
    public void loadXML(Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore)
            throws KettleXMLException {
        try {
            outputFieldName = nvl(XMLHandler.getTagValue(stepnode, "outputFieldName"), "xml_output");
            encoding        = nvl(XMLHandler.getTagValue(stepnode, "encoding"), "UTF-8");
            rootElement     = nvl(XMLHandler.getTagValue(stepnode, "rootElement"), "root");
            rowElement      = nvl(XMLHandler.getTagValue(stepnode, "rowElement"), "row");
            String st       = XMLHandler.getTagValue(stepnode, "schemaType");
            schemaType      = (st != null) ? SchemaType.valueOf(st) : SchemaType.NONE;
            schemaPath      = nvl(XMLHandler.getTagValue(stepnode, "schemaPath"), "");
            validateOutput  = "true".equalsIgnoreCase(XMLHandler.getTagValue(stepnode, "validateOutput"));
            dtdPublicId     = nvl(XMLHandler.getTagValue(stepnode, "dtdPublicId"), "");
            dtdSystemId     = nvl(XMLHandler.getTagValue(stepnode, "dtdSystemId"), "");
            fieldMappings   = new ArrayList<>();
            Node mappingsNode = XMLHandler.getSubNode(stepnode, "fieldMappings");
            if (mappingsNode != null) {
                int count = XMLHandler.countNodes(mappingsNode, "mapping");
                for (int i = 0; i < count; i++) {
                    Node mNode = XMLHandler.getSubNodeByNr(mappingsNode, "mapping", i);
                    FieldMapping fm = new FieldMapping();
                    fm.setSourceField(nvl(XMLHandler.getTagValue(mNode, "sourceField"), ""));
                    fm.setXmlName(nvl(XMLHandler.getTagValue(mNode, "xmlName"), ""));
                    String nt = XMLHandler.getTagValue(mNode, "nodeType");
                    fm.setNodeType(nt != null ? FieldMapping.NodeType.valueOf(nt) : FieldMapping.NodeType.ELEMENT);
                    fm.setParentPath(nvl(XMLHandler.getTagValue(mNode, "parentPath"), ""));
                    fieldMappings.add(fm);
                }
            }
        } catch (Exception e) {
            throw new KettleXMLException("Unable to load XmlGeneratorStep metadata from XML", e);
        }
    }

    @Override
    public void readRep(Repository rep, IMetaStore metaStore,
            ObjectId id_step, List<DatabaseMeta> databases) throws KettleException {
        // Repository persistence not implemented — transformations saved as XML files
    }

    @Override
    public void saveRep(Repository rep, IMetaStore metaStore,
            ObjectId id_transformation, ObjectId id_step) throws KettleException {
        // Repository persistence not implemented
    }

    @Override
    public void check(List<CheckResultInterface> remarks, TransMeta transMeta,
            StepMeta stepMeta, RowMetaInterface prev, String[] input, String[] output,
            RowMetaInterface info, VariableSpace space, Repository repository, IMetaStore metaStore) {
        if (outputFieldName == null || outputFieldName.trim().isEmpty()) {
            remarks.add(new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR,
                "Output field name must not be empty.", stepMeta));
        } else {
            remarks.add(new CheckResult(CheckResultInterface.TYPE_RESULT_OK,
                "Configuration looks good.", stepMeta));
        }
    }

    @Override
    public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface,
            int copyNr, TransMeta transMeta, Trans trans) {
        return new XmlGeneratorStep(stepMeta, stepDataInterface, copyNr, transMeta, trans);
    }

    @Override
    public StepDataInterface getStepData() {
        return new XmlGeneratorStepData();
    }

    private static String nvl(String s, String fallback) {
        return (s != null && !s.isEmpty()) ? s : fallback;
    }
}
