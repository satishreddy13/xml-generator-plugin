package com.example.pdi.plugin.xmlgenerator;

/**
 * Describes how a single PDI row field maps to an XML node in the generated document.
 */
public class FieldMapping implements Cloneable {

    /** How the field value is placed in the XML tree. */
    public enum NodeType {
        /** Value becomes an element's text content: {@code <name>value</name>} */
        ELEMENT,
        /** Value becomes an attribute on the parent element: {@code parent attr="value"} */
        ATTRIBUTE,
        /** Value is wrapped in a CDATA section: {@code <name><![CDATA[value]]></name>} */
        CDATA
    }

    private String   sourceField = "";   // PDI row field name
    private String   xmlName     = "";   // XML element or attribute name
    private NodeType nodeType    = NodeType.ELEMENT;
    private String   parentPath  = "";   // slash-delimited path under the row element, e.g. "order/customer"

    public FieldMapping() {}

    public FieldMapping(String sourceField, String xmlName, NodeType nodeType, String parentPath) {
        this.sourceField = sourceField;
        this.xmlName     = xmlName;
        this.nodeType    = nodeType;
        this.parentPath  = parentPath;
    }

    public String   getSourceField()                    { return sourceField; }
    public void     setSourceField(String sourceField)  { this.sourceField = sourceField; }
    public String   getXmlName()                        { return xmlName; }
    public void     setXmlName(String xmlName)          { this.xmlName = xmlName; }
    public NodeType getNodeType()                       { return nodeType; }
    public void     setNodeType(NodeType nodeType)      { this.nodeType = nodeType; }
    public String   getParentPath()                     { return parentPath; }
    public void     setParentPath(String parentPath)    { this.parentPath = parentPath; }

    @Override
    public FieldMapping clone() {
        try {
            return (FieldMapping) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}
