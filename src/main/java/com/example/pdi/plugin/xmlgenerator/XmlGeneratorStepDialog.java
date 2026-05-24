package com.example.pdi.plugin.xmlgenerator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.ui.trans.step.BaseStepDialog;

/**
 * Spoon dialog for the XML Generator step.
 *
 * Three tabs:
 *   Output  – output field name, encoding, root / row element names
 *   Schema  – None / XSD / DTD selection with relevant settings
 *   Fields  – inline-editable table mapping PDI row fields → XML nodes
 */
public class XmlGeneratorStepDialog extends BaseStepDialog implements StepDialogInterface {

    private static final String[] NODE_TYPES = { "ELEMENT", "ATTRIBUTE", "CDATA" };

    private final XmlGeneratorStepMeta input;
    private final TransMeta            transMeta;

    // ---- Output tab ----
    private Text wOutputFieldName;
    private Combo wEncoding;
    private Text wRootElement;
    private Text wRowElement;

    // ---- Schema tab ----
    private Button wRadioNone;
    private Button wRadioXsd;
    private Button wRadioDtd;
    private Group  gXsd;
    private Text   wSchemaPath;
    private Button wValidate;
    private Group  gDtd;
    private Text   wDtdPublicId;
    private Text   wDtdSystemId;

    // ---- Fields tab ----
    private Table       wMappingTable;
    private TableEditor tableEditor;

    // -----------------------------------------------------------------------

    public XmlGeneratorStepDialog(Shell parent, Object baseStepMeta,
            TransMeta transMeta, String stepname) {
        super(parent, (BaseStepMeta) baseStepMeta, transMeta, stepname);
        this.input     = (XmlGeneratorStepMeta) baseStepMeta;
        this.transMeta = transMeta;
    }

    // -----------------------------------------------------------------------
    // StepDialogInterface
    // -----------------------------------------------------------------------

    @Override
    public String open() {
        Shell   parent  = getParent();
        Display display = parent.getDisplay();

        shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX);
        props.setLook(shell);
        setShellImage(shell, input);

        ModifyListener lsMod = e -> input.setChanged();
        changed = input.hasChanged();

        FormLayout formLayout = new FormLayout();
        formLayout.marginWidth  = Const.FORM_MARGIN;
        formLayout.marginHeight = Const.FORM_MARGIN;
        shell.setLayout(formLayout);
        shell.setText("XML Generator");
        shell.setSize(720, 600);

        int middle = props.getMiddlePct();
        int margin  = Const.MARGIN;

        // ---- Step name (mandatory first widget) ----
        wlStepname = new Label(shell, SWT.RIGHT);
        wlStepname.setText("Step Name");
        props.setLook(wlStepname);
        fdlStepname = new FormData();
        fdlStepname.left  = new FormAttachment(0, 0);
        fdlStepname.right = new FormAttachment(middle, -margin);
        fdlStepname.top   = new FormAttachment(0, margin);
        wlStepname.setLayoutData(fdlStepname);

        wStepname = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        wStepname.setText(stepname);
        props.setLook(wStepname);
        wStepname.addModifyListener(lsMod);
        fdStepname = new FormData();
        fdStepname.left  = new FormAttachment(middle, 0);
        fdStepname.top   = new FormAttachment(0, margin);
        fdStepname.right = new FormAttachment(100, 0);
        wStepname.setLayoutData(fdStepname);

        // ---- OK / Cancel (created before the tab folder so the folder can
        //      attach its bottom edge to these buttons) ----
        wOK     = new Button(shell, SWT.PUSH);
        wCancel = new Button(shell, SWT.PUSH);
        wOK.setText("OK");
        wCancel.setText("Cancel");

        FormData fdOK = new FormData();
        fdOK.left   = new FormAttachment(33, 0);
        fdOK.bottom = new FormAttachment(100, -margin);
        wOK.setLayoutData(fdOK);

        FormData fdCancel = new FormData();
        fdCancel.left   = new FormAttachment(66, 0);
        fdCancel.bottom = new FormAttachment(100, -margin);
        wCancel.setLayoutData(fdCancel);

        // ---- Tab folder ----
        CTabFolder tabFolder = new CTabFolder(shell, SWT.BORDER);
        props.setLook(tabFolder);
        FormData fdTabFolder = new FormData();
        fdTabFolder.left   = new FormAttachment(0, 0);
        fdTabFolder.right  = new FormAttachment(100, 0);
        fdTabFolder.top    = new FormAttachment(wStepname, margin);
        fdTabFolder.bottom = new FormAttachment(wOK, -margin);
        tabFolder.setLayoutData(fdTabFolder);

        buildOutputTab(tabFolder, lsMod, middle, margin);
        buildSchemaTab(tabFolder, lsMod, middle, margin);
        buildFieldsTab(tabFolder, margin);

        tabFolder.setSelection(0);

        // ---- Listeners ----
        wOK.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { ok(); }
        });
        wCancel.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { cancel(); }
        });
        shell.addShellListener(new ShellAdapter() {
            @Override public void shellClosed(ShellEvent e) { cancel(); }
        });

        getData();
        input.setChanged(changed);

        shell.open();
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) display.sleep();
        }
        return stepname;
    }

    // -----------------------------------------------------------------------
    // Tab builders
    // -----------------------------------------------------------------------

    private void buildOutputTab(CTabFolder folder, ModifyListener lsMod, int middle, int margin) {
        CTabItem tab = new CTabItem(folder, SWT.NONE);
        tab.setText("Output");

        Composite c = new Composite(folder, SWT.NONE);
        props.setLook(c);
        c.setLayout(new FormLayout());

        // Output Field Name
        Label lFieldName = label(c, "Output Field Name");
        lFieldName.setToolTipText("Name of the row field that receives the generated XML string");
        formLeft(lFieldName, null, middle, margin);

        wOutputFieldName = text(c, lsMod);
        formRight(wOutputFieldName, null, lFieldName, middle, margin);

        // Encoding
        Label lEncoding = label(c, "Encoding");
        lEncoding.setToolTipText("XML declaration encoding, e.g. UTF-8 or UTF-16");
        formLeft(lEncoding, wOutputFieldName, middle, margin);

        wEncoding = new Combo(c, SWT.DROP_DOWN);
        props.setLook(wEncoding);
        wEncoding.setItems("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII");
        wEncoding.addModifyListener(lsMod);
        formRight(wEncoding, wOutputFieldName, lEncoding, middle, margin);

        // Root Element
        Label lRoot = label(c, "Root Element");
        lRoot.setToolTipText("Name of the document's root XML element");
        formLeft(lRoot, wEncoding, middle, margin);

        wRootElement = text(c, lsMod);
        formRight(wRootElement, wEncoding, lRoot, middle, margin);

        // Row Element
        Label lRow = label(c, "Row Element");
        lRow.setToolTipText("Name of the XML element that wraps each row's fields");
        formLeft(lRow, wRootElement, middle, margin);

        wRowElement = text(c, lsMod);
        formRight(wRowElement, wRootElement, lRow, middle, margin);

        tab.setControl(c);
    }

    private void buildSchemaTab(CTabFolder folder, ModifyListener lsMod, int middle, int margin) {
        CTabItem tab = new CTabItem(folder, SWT.NONE);
        tab.setText("Schema");

        Composite c = new Composite(folder, SWT.NONE);
        props.setLook(c);
        c.setLayout(new FormLayout());

        // Schema type radio buttons
        Label lType = label(c, "Schema Type");
        formLeft(lType, null, middle, margin);

        wRadioNone = new Button(c, SWT.RADIO);
        wRadioNone.setText("None");
        props.setLook(wRadioNone);
        FormData fdNone = new FormData();
        fdNone.left = new FormAttachment(middle, 0);
        fdNone.top  = new FormAttachment(0, margin);
        wRadioNone.setLayoutData(fdNone);

        wRadioXsd = new Button(c, SWT.RADIO);
        wRadioXsd.setText("XSD");
        props.setLook(wRadioXsd);
        FormData fdXsd = new FormData();
        fdXsd.left = new FormAttachment(wRadioNone, margin * 2);
        fdXsd.top  = new FormAttachment(0, margin);
        wRadioXsd.setLayoutData(fdXsd);

        wRadioDtd = new Button(c, SWT.RADIO);
        wRadioDtd.setText("DTD");
        props.setLook(wRadioDtd);
        FormData fdDtd = new FormData();
        fdDtd.left = new FormAttachment(wRadioXsd, margin * 2);
        fdDtd.top  = new FormAttachment(0, margin);
        wRadioDtd.setLayoutData(fdDtd);

        // XSD group
        gXsd = new Group(c, SWT.SHADOW_ETCHED_IN);
        gXsd.setText("XSD Settings");
        props.setLook(gXsd);
        gXsd.setLayout(new FormLayout());
        FormData fdGXsd = new FormData();
        fdGXsd.left  = new FormAttachment(0, margin);
        fdGXsd.right = new FormAttachment(100, -margin);
        fdGXsd.top   = new FormAttachment(wRadioNone, margin);
        gXsd.setLayoutData(fdGXsd);

        Label lSchema = new Label(gXsd, SWT.RIGHT);
        lSchema.setText("Schema File (.xsd)");
        props.setLook(lSchema);
        FormData fdlSchema = new FormData();
        fdlSchema.left  = new FormAttachment(0, margin);
        fdlSchema.right = new FormAttachment(middle, -margin);
        fdlSchema.top   = new FormAttachment(0, margin);
        lSchema.setLayoutData(fdlSchema);

        wSchemaPath = new Text(gXsd, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        props.setLook(wSchemaPath);
        wSchemaPath.addModifyListener(lsMod);
        FormData fdSchema = new FormData();
        fdSchema.left  = new FormAttachment(middle, 0);
        fdSchema.right = new FormAttachment(85, -margin);
        fdSchema.top   = new FormAttachment(0, margin);
        wSchemaPath.setLayoutData(fdSchema);

        Button wBrowse = new Button(gXsd, SWT.PUSH);
        wBrowse.setText("Browse...");
        props.setLook(wBrowse);
        FormData fdBrowse = new FormData();
        fdBrowse.left  = new FormAttachment(85, 0);
        fdBrowse.right = new FormAttachment(100, -margin);
        fdBrowse.top   = new FormAttachment(0, margin - 2);
        wBrowse.setLayoutData(fdBrowse);
        wBrowse.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                FileDialog fd = new FileDialog(shell, SWT.OPEN);
                fd.setFilterExtensions(new String[]{"*.xsd", "*.*"});
                fd.setFilterNames(new String[]{"XSD Schema Files", "All Files"});
                String path = fd.open();
                if (path != null) wSchemaPath.setText(path);
            }
        });

        Label lValidate = new Label(gXsd, SWT.RIGHT);
        lValidate.setText("Validate output");
        props.setLook(lValidate);
        FormData fdlValidate = new FormData();
        fdlValidate.left  = new FormAttachment(0, margin);
        fdlValidate.right = new FormAttachment(middle, -margin);
        fdlValidate.top   = new FormAttachment(wSchemaPath, margin);
        lValidate.setLayoutData(fdlValidate);

        wValidate = new Button(gXsd, SWT.CHECK);
        wValidate.setToolTipText("Validate each generated XML document against the XSD before emitting the row");
        props.setLook(wValidate);
        FormData fdValidate = new FormData();
        fdValidate.left = new FormAttachment(middle, 0);
        fdValidate.top  = new FormAttachment(wSchemaPath, margin);
        wValidate.setLayoutData(fdValidate);

        // DTD group
        gDtd = new Group(c, SWT.SHADOW_ETCHED_IN);
        gDtd.setText("DTD Settings");
        props.setLook(gDtd);
        gDtd.setLayout(new FormLayout());
        FormData fdGDtd = new FormData();
        fdGDtd.left  = new FormAttachment(0, margin);
        fdGDtd.right = new FormAttachment(100, -margin);
        fdGDtd.top   = new FormAttachment(gXsd, margin);
        gDtd.setLayoutData(fdGDtd);

        Label lPub = new Label(gDtd, SWT.RIGHT);
        lPub.setText("DOCTYPE Public ID");
        lPub.setToolTipText("Optional PUBLIC identifier, e.g. -//W3C//DTD HTML 4.01//EN (leave blank to omit)");
        props.setLook(lPub);
        FormData fdlPub = new FormData();
        fdlPub.left  = new FormAttachment(0, margin);
        fdlPub.right = new FormAttachment(middle, -margin);
        fdlPub.top   = new FormAttachment(0, margin);
        lPub.setLayoutData(fdlPub);

        wDtdPublicId = new Text(gDtd, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        props.setLook(wDtdPublicId);
        wDtdPublicId.addModifyListener(lsMod);
        FormData fdPub = new FormData();
        fdPub.left  = new FormAttachment(middle, 0);
        fdPub.right = new FormAttachment(100, -margin);
        fdPub.top   = new FormAttachment(0, margin);
        wDtdPublicId.setLayoutData(fdPub);

        Label lSys = new Label(gDtd, SWT.RIGHT);
        lSys.setText("DOCTYPE System ID");
        lSys.setToolTipText("SYSTEM identifier pointing to the DTD file or URL");
        props.setLook(lSys);
        FormData fdlSys = new FormData();
        fdlSys.left  = new FormAttachment(0, margin);
        fdlSys.right = new FormAttachment(middle, -margin);
        fdlSys.top   = new FormAttachment(wDtdPublicId, margin);
        lSys.setLayoutData(fdlSys);

        wDtdSystemId = new Text(gDtd, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        props.setLook(wDtdSystemId);
        wDtdSystemId.addModifyListener(lsMod);
        FormData fdSys = new FormData();
        fdSys.left  = new FormAttachment(middle, 0);
        fdSys.right = new FormAttachment(100, -margin);
        fdSys.top   = new FormAttachment(wDtdPublicId, margin);
        wDtdSystemId.setLayoutData(fdSys);

        // Tie radio buttons to group enable/disable
        SelectionAdapter radioListener = new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) { updateSchemaGroups(); }
        };
        wRadioNone.addSelectionListener(radioListener);
        wRadioXsd.addSelectionListener(radioListener);
        wRadioDtd.addSelectionListener(radioListener);

        tab.setControl(c);
    }

    private void buildFieldsTab(CTabFolder folder, int margin) {
        CTabItem tab = new CTabItem(folder, SWT.NONE);
        tab.setText("Fields");

        Composite c = new Composite(folder, SWT.NONE);
        props.setLook(c);
        c.setLayout(new FormLayout());

        // ---- Toolbar ----
        Button wGetFields = new Button(c, SWT.PUSH);
        wGetFields.setText("Get Fields");
        wGetFields.setToolTipText("Auto-populate from the fields produced by the previous step");
        props.setLook(wGetFields);
        FormData fdGet = new FormData();
        fdGet.top   = new FormAttachment(0, margin);
        fdGet.right = new FormAttachment(100, -margin);
        wGetFields.setLayoutData(fdGet);

        Button wDeleteRow = new Button(c, SWT.PUSH);
        wDeleteRow.setText("Delete Row");
        props.setLook(wDeleteRow);
        FormData fdDel = new FormData();
        fdDel.top   = new FormAttachment(0, margin);
        fdDel.right = new FormAttachment(wGetFields, -margin);
        wDeleteRow.setLayoutData(fdDel);

        Button wAddRow = new Button(c, SWT.PUSH);
        wAddRow.setText("Add Row");
        props.setLook(wAddRow);
        FormData fdAdd = new FormData();
        fdAdd.top   = new FormAttachment(0, margin);
        fdAdd.right = new FormAttachment(wDeleteRow, -margin);
        wAddRow.setLayoutData(fdAdd);

        // ---- Mapping table ----
        wMappingTable = new Table(c, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        wMappingTable.setHeaderVisible(true);
        wMappingTable.setLinesVisible(true);
        props.setLook(wMappingTable);

        TableColumn colNum  = new TableColumn(wMappingTable, SWT.NONE); colNum.setText("#");          colNum.setWidth(35);
        TableColumn colSrc  = new TableColumn(wMappingTable, SWT.NONE); colSrc.setText("Source Field"); colSrc.setWidth(160);
        TableColumn colXml  = new TableColumn(wMappingTable, SWT.NONE); colXml.setText("XML Name");     colXml.setWidth(160);
        TableColumn colType = new TableColumn(wMappingTable, SWT.NONE); colType.setText("Type");        colType.setWidth(90);
        TableColumn colPath = new TableColumn(wMappingTable, SWT.NONE); colPath.setText("Parent Path"); colPath.setWidth(180);

        FormData fdTable = new FormData();
        fdTable.left   = new FormAttachment(0, margin);
        fdTable.right  = new FormAttachment(100, -margin);
        fdTable.top    = new FormAttachment(wGetFields, margin);
        fdTable.bottom = new FormAttachment(100, -margin);
        wMappingTable.setLayoutData(fdTable);

        // ---- Inline cell editor ----
        tableEditor = new TableEditor(wMappingTable);
        tableEditor.horizontalAlignment = SWT.LEFT;
        tableEditor.grabHorizontal      = true;
        tableEditor.minimumWidth        = 50;

        wMappingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                // Dispose any active editor first
                Control old = tableEditor.getEditor();
                if (old != null) old.dispose();

                Point pt = new Point(e.x, e.y);
                TableItem item = wMappingTable.getItem(pt);
                if (item == null) return;

                // Determine which editable column was clicked (skip col 0 = row #)
                int clickedCol = -1;
                for (int col = 1; col <= 4; col++) {
                    Rectangle bounds = item.getBounds(col);
                    if (bounds.contains(pt)) { clickedCol = col; break; }
                }
                if (clickedCol < 0) return;

                final int  finalCol  = clickedCol;
                final TableItem finalItem = item;

                if (finalCol == 3) {
                    // Type column → Combo
                    Combo combo = new Combo(wMappingTable, SWT.READ_ONLY);
                    combo.setItems(NODE_TYPES);
                    int idx = combo.indexOf(finalItem.getText(finalCol));
                    combo.select(idx >= 0 ? idx : 0);
                    combo.addSelectionListener(new SelectionAdapter() {
                        @Override public void widgetSelected(SelectionEvent se) {
                            finalItem.setText(finalCol, combo.getText());
                        }
                    });
                    combo.addFocusListener(new FocusAdapter() {
                        @Override public void focusLost(FocusEvent fe) {
                            finalItem.setText(finalCol, combo.getText());
                            combo.dispose();
                        }
                    });
                    tableEditor.setEditor(combo, item, finalCol);
                    combo.setFocus();
                } else {
                    // Text columns
                    Text txt = new Text(wMappingTable, SWT.NONE);
                    txt.setText(finalItem.getText(finalCol));
                    txt.addModifyListener(me -> finalItem.setText(finalCol, txt.getText()));
                    txt.addFocusListener(new FocusAdapter() {
                        @Override public void focusLost(FocusEvent fe) {
                            finalItem.setText(finalCol, txt.getText());
                            txt.dispose();
                        }
                    });
                    tableEditor.setEditor(txt, item, finalCol);
                    txt.selectAll();
                    txt.setFocus();
                }
            }
        });

        // ---- Button listeners ----
        wAddRow.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                TableItem item = new TableItem(wMappingTable, SWT.NONE);
                item.setText(0, String.valueOf(wMappingTable.getItemCount()));
                item.setText(3, "ELEMENT");
                input.setChanged();
            }
        });

        wDeleteRow.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                Control old = tableEditor.getEditor();
                if (old != null) old.dispose();
                int[] sel = wMappingTable.getSelectionIndices();
                if (sel.length == 0) return;
                wMappingTable.remove(sel);
                renumberRows();
                input.setChanged();
            }
        });

        wGetFields.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                populateFromPrevStep();
            }
        });

        tab.setControl(c);
    }

    // -----------------------------------------------------------------------
    // Populate / collect
    // -----------------------------------------------------------------------

    private void getData() {
        wOutputFieldName.setText(nvl(input.getOutputFieldName()));
        wEncoding.setText(nvl(input.getEncoding(), "UTF-8"));
        wRootElement.setText(nvl(input.getRootElement()));
        wRowElement.setText(nvl(input.getRowElement()));

        XmlGeneratorStepMeta.SchemaType st = input.getSchemaType();
        wRadioNone.setSelection(st == XmlGeneratorStepMeta.SchemaType.NONE);
        wRadioXsd.setSelection(st == XmlGeneratorStepMeta.SchemaType.XSD);
        wRadioDtd.setSelection(st == XmlGeneratorStepMeta.SchemaType.DTD);
        updateSchemaGroups();

        wSchemaPath.setText(nvl(input.getSchemaPath()));
        wValidate.setSelection(input.isValidateOutput());
        wDtdPublicId.setText(nvl(input.getDtdPublicId()));
        wDtdSystemId.setText(nvl(input.getDtdSystemId()));

        wMappingTable.removeAll();
        int rowNum = 1;
        for (FieldMapping fm : input.getFieldMappings()) {
            TableItem item = new TableItem(wMappingTable, SWT.NONE);
            item.setText(0, String.valueOf(rowNum++));
            item.setText(1, nvl(fm.getSourceField()));
            item.setText(2, nvl(fm.getXmlName()));
            item.setText(3, fm.getNodeType().name());
            item.setText(4, nvl(fm.getParentPath()));
        }

        wStepname.selectAll();
        wStepname.setFocus();
    }

    private void ok() {
        // Flush any active editor
        Control activeEditor = tableEditor.getEditor();
        if (activeEditor != null) activeEditor.dispose();

        if (wOutputFieldName.getText().trim().isEmpty()) {
            showError("Output field name must not be empty.");
            return;
        }
        if (wRootElement.getText().trim().isEmpty()) {
            showError("Root element name must not be empty.");
            return;
        }
        if (wRowElement.getText().trim().isEmpty()) {
            showError("Row element name must not be empty.");
            return;
        }

        stepname = wStepname.getText();

        input.setOutputFieldName(wOutputFieldName.getText().trim());
        input.setEncoding(wEncoding.getText().trim());
        input.setRootElement(wRootElement.getText().trim());
        input.setRowElement(wRowElement.getText().trim());

        if (wRadioXsd.getSelection()) {
            input.setSchemaType(XmlGeneratorStepMeta.SchemaType.XSD);
        } else if (wRadioDtd.getSelection()) {
            input.setSchemaType(XmlGeneratorStepMeta.SchemaType.DTD);
        } else {
            input.setSchemaType(XmlGeneratorStepMeta.SchemaType.NONE);
        }

        input.setSchemaPath(wSchemaPath.getText().trim());
        input.setValidateOutput(wValidate.getSelection());
        input.setDtdPublicId(wDtdPublicId.getText().trim());
        input.setDtdSystemId(wDtdSystemId.getText().trim());

        List<FieldMapping> mappings = new ArrayList<>();
        for (TableItem item : wMappingTable.getItems()) {
            String src  = item.getText(1).trim();
            String xml  = item.getText(2).trim();
            if (src.isEmpty() && xml.isEmpty()) continue;
            FieldMapping fm = new FieldMapping();
            fm.setSourceField(src);
            fm.setXmlName(xml.isEmpty() ? src : xml);
            String typeStr = item.getText(3);
            try {
                fm.setNodeType(FieldMapping.NodeType.valueOf(typeStr));
            } catch (IllegalArgumentException ex) {
                fm.setNodeType(FieldMapping.NodeType.ELEMENT);
            }
            fm.setParentPath(item.getText(4).trim());
            mappings.add(fm);
        }
        input.setFieldMappings(mappings);

        dispose();
    }

    private void cancel() {
        stepname = null;
        input.setChanged(changed);
        dispose();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Enable/disable the XSD and DTD settings groups based on the selected radio. */
    private void updateSchemaGroups() {
        boolean isXsd = wRadioXsd.getSelection();
        boolean isDtd = wRadioDtd.getSelection();
        setGroupEnabled(gXsd, isXsd);
        setGroupEnabled(gDtd, isDtd);
    }

    private void setGroupEnabled(Group group, boolean enabled) {
        group.setEnabled(enabled);
        for (Control child : group.getChildren()) {
            child.setEnabled(enabled);
        }
    }

    /** Re-number the # column after a row deletion. */
    private void renumberRows() {
        TableItem[] items = wMappingTable.getItems();
        for (int i = 0; i < items.length; i++) {
            items[i].setText(0, String.valueOf(i + 1));
        }
    }

    /** Fetch field names from the step that feeds into this one and add them to the table. */
    private void populateFromPrevStep() {
        try {
            // stepMeta is not directly held in BaseStepDialog; we find it by name
            org.pentaho.di.trans.step.StepMeta sm = new org.pentaho.di.trans.step.StepMeta();
            sm.setName(wStepname.getText());
            RowMetaInterface prevFields = transMeta.getPrevStepFields(sm);
            String[] fieldNames = prevFields.getFieldNames();
            if (fieldNames.length == 0) {
                showInfo("No fields found from the previous step.\n"
                    + "Make sure this step is connected to an upstream step in the transformation.");
                return;
            }
            // Clear and repopulate
            Control old = tableEditor.getEditor();
            if (old != null) old.dispose();
            wMappingTable.removeAll();
            for (int i = 0; i < fieldNames.length; i++) {
                TableItem item = new TableItem(wMappingTable, SWT.NONE);
                item.setText(0, String.valueOf(i + 1));
                item.setText(1, fieldNames[i]);
                item.setText(2, fieldNames[i]);  // default: same name in XML
                item.setText(3, "ELEMENT");
                item.setText(4, "");
            }
            input.setChanged();
        } catch (Exception ex) {
            showError("Could not retrieve fields from previous step:\n" + ex.getMessage());
        }
    }

    // ---- Layout helpers ----

    private Label label(Composite parent, String text) {
        Label lbl = new Label(parent, SWT.RIGHT);
        lbl.setText(text);
        props.setLook(lbl);
        return lbl;
    }

    private Text text(Composite parent, ModifyListener lsMod) {
        Text t = new Text(parent, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        props.setLook(t);
        t.addModifyListener(lsMod);
        return t;
    }

    /** Attach a label to the left half, top = below {@code above} (or top of form if null). */
    private void formLeft(Control ctrl, Control above, int middle, int margin) {
        FormData fd = new FormData();
        fd.left  = new FormAttachment(0, 0);
        fd.right = new FormAttachment(middle, -margin);
        fd.top   = (above == null)
            ? new FormAttachment(0, margin)
            : new FormAttachment(above, margin);
        ctrl.setLayoutData(fd);
    }

    /** Attach a widget to the right half, top aligned with its companion label. */
    private void formRight(Control ctrl, Control above, Control labelPartner, int middle, int margin) {
        FormData fd = new FormData();
        fd.left  = new FormAttachment(middle, 0);
        fd.right = new FormAttachment(100, 0);
        fd.top   = (above == null)
            ? new FormAttachment(0, margin)
            : new FormAttachment(above, margin);
        ctrl.setLayoutData(fd);
    }

    private void showError(String message) {
        MessageBox mb = new MessageBox(shell, SWT.ICON_ERROR | SWT.OK);
        mb.setText("Error");
        mb.setMessage(message);
        mb.open();
    }

    private void showInfo(String message) {
        MessageBox mb = new MessageBox(shell, SWT.ICON_INFORMATION | SWT.OK);
        mb.setText("Information");
        mb.setMessage(message);
        mb.open();
    }

    private static String nvl(String s)               { return s != null ? s : ""; }
    private static String nvl(String s, String def)   { return (s != null && !s.isEmpty()) ? s : def; }
}
