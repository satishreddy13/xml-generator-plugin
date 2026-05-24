package com.example.pdi.plugin.xmlgenerator;

import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

/** Runtime data holder for XmlGeneratorStep. */
public class XmlGeneratorStepData extends BaseStepData implements StepDataInterface {
    /** Output row structure (input fields + the xml_output field appended). */
    public RowMetaInterface outputRowMeta;
}
