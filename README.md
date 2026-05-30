# XML Generator — Pentaho PDI Step Plugin

A custom Pentaho Data Integration (PDI) step plugin that generates a structured XML document per row from mapped incoming fields, with optional XSD schema validation and DTD/CDATA support.

---

## What it does

For every row in the transformation, the step:

1. Builds an XML document from the fields you map in the dialog.
2. Optionally validates the document against an XSD schema.
3. Appends the XML string as a new field on the output row.

---

## XML Output Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<root>
  <row>
    <customerId>C001</customerId>
    <orderDate>2026-05-30</orderDate>
    <remarks><![CDATA[some & special <chars>]]></remarks>
  </row>
</root>
```

| Part | Configured by | Default |
|------|--------------|---------|
| Document root element | **Root Element** field | `root` |
| Per-row wrapper element | **Row Element** field | `row` |
| Field placement | **Fields** tab mapping table | — |
| Output field name | **Output Field Name** | `xml_output` |

---

## Step Configuration

Double-click the step on the Spoon canvas to open the three-tab dialog.

### Tab 1 — Output

| Setting | Description |
|---------|-------------|
| **Output Field Name** | Name of the new row field that receives the generated XML string |
| **Encoding** | XML declaration encoding (`UTF-8`, `UTF-16`, `ISO-8859-1`, `US-ASCII`) |
| **Root Element** | Name of the document's outermost XML element |
| **Row Element** | Name of the element that wraps each row's mapped fields |

### Tab 2 — Schema

Choose one schema mode:

| Mode | Behaviour |
|------|-----------|
| **None** | Plain XML — no schema reference is added |
| **XSD** | Adds an optional file path to an `.xsd` schema; tick **Validate output** to validate every generated document before emitting the row (validation errors abort that row with an exception) |
| **DTD** | Adds a `<!DOCTYPE …>` declaration using the configured **Public ID** and/or **System ID**; leave Public ID blank to emit a `SYSTEM`-only DOCTYPE |

### Tab 3 — Fields

An inline-editable table that maps incoming PDI row fields to XML nodes.

| Column | Description |
|--------|-------------|
| **#** | Row number (auto-assigned, read-only) |
| **Source Field** | Name of the incoming PDI row field |
| **XML Name** | Name of the XML element or attribute to create |
| **Type** | `ELEMENT`, `ATTRIBUTE`, or `CDATA` (see below) |
| **Parent Path** | Optional slash-delimited path for nesting, e.g. `order/customer` |

Click any cell to edit it inline. The **Type** column uses a drop-down; all other columns use a text editor.

**Toolbar buttons:**

| Button | Action |
|--------|--------|
| **Get Fields** | Auto-populates the table with all fields from the upstream step |
| **Add Row** | Appends a blank mapping row |
| **Delete Row** | Removes the selected row(s) |

---

## Node Types

### ELEMENT (default)
The field value becomes an element's text content:
```xml
<customerId>C001</customerId>
```

### ATTRIBUTE
The field value becomes an attribute on the **parent** element (or the row element if no parent path is set):
```xml
<row id="C001">
```
Mapping: Source Field = `id_field`, XML Name = `id`, Type = `ATTRIBUTE`

### CDATA
The field value is wrapped in a CDATA section — useful for values that contain XML-special characters (`<`, `>`, `&`):
```xml
<remarks><![CDATA[some & special <chars>]]></remarks>
```

---

## Parent Path (Nested XML)

Set **Parent Path** to create intermediate elements automatically.  
Path segments are separated by `/`.

**Example mappings:**

| Source Field | XML Name | Type | Parent Path |
|---|---|---|---|
| `order_id` | `orderId` | ELEMENT | *(empty)* |
| `cust_name` | `name` | ELEMENT | `customer` |
| `cust_email` | `email` | ELEMENT | `customer` |
| `line_desc` | `description` | CDATA | `lines/line` |

**Output:**
```xml
<root>
  <row>
    <orderId>ORD-001</orderId>
    <customer>
      <name>Alice</name>
      <email>alice@example.com</email>
    </customer>
    <lines>
      <line>
        <description><![CDATA[Widget & Gadget pack]]></description>
      </line>
    </lines>
  </row>
</root>
```

---

## Building

### Prerequisites
- Java 11+ (OpenJDK Adoptium 17 or 21 recommended)
- Maven 3.6+
- PDI compile-only stubs installed (see below)

### 1. Install the PDI compile-only stubs

PDI's Maven artifacts are not on a public repository. A set of minimal compile-only stubs is provided in a companion repository:

**https://github.com/satishreddy13/pdi-stubs**

```bash
git clone https://github.com/satishreddy13/pdi-stubs.git
cd pdi-stubs
mvn install -DskipTests
```

### 2. Build the plugin

```bash
cd xml-generator-plugin
mvn clean package -DskipTests
```

This produces:
```
target/
  xml-generator-plugin-1.0.0.jar          ← compiled plugin
  xml-generator-plugin-1.0.0-plugin.zip   ← deployable zip
```

---

## Deployment

Unzip the plugin into PDI's `plugins/` directory and restart Spoon:

```bash
unzip target/xml-generator-plugin-1.0.0-plugin.zip \
  -d <PDI_HOME>/plugins/
```

The **"XML Generator"** step will appear in the **Transform** category.

---

## Project Structure

```
xml-generator-plugin/
├── pom.xml
└── src/main/
    ├── java/com/example/pdi/plugin/xmlgenerator/
    │   ├── FieldMapping.java            ← field mapping data class (source→XML, node type, parent path)
    │   ├── XmlGeneratorStep.java        ← row processing & XML generation
    │   ├── XmlGeneratorStepMeta.java    ← plugin metadata & XML persistence
    │   ├── XmlGeneratorStepData.java    ← runtime data holder
    │   └── XmlGeneratorStepDialog.java  ← Spoon configuration dialog (3-tab GUI)
    └── resources/
        ├── plugin.xml                   ← plugin registration descriptor
        └── assembly.xml                 ← Maven assembly (builds deployable zip)
```

---

## Compatibility

| Component | Version |
|-----------|---------|
| Pentaho PDI | 11.0.0.0-237 (tested) |
| Java | 11+ (compiled at Java 11 bytecode) |
| OS | macOS (tested), Linux, Windows |

---

## Related

- [csps-doc-id-generator](https://github.com/satishreddy13/csps-doc-id-generator) — CSPS DOC_ID Generator PDI step plugin
- [pdi-stubs](https://github.com/satishreddy13/pdi-stubs) — Compile-only PDI API stubs used by both plugins
