/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dataloader.ui.extraction;

import java.util.*;
import java.util.List;

import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.salesforce.dataloader.config.Config;
import com.salesforce.dataloader.controller.Controller;
import com.salesforce.dataloader.ui.Labels;
import com.sforce.soap.partner.*;

/**
 * Creates the soql
 * 
 * @author Lexi Viripaeff
 * @since 6.0
 */
public class ExtractionSOQLPage extends ExtractionPage {

    private Text soqlText;
    private Field[] fieldsInSObject;
    private CheckboxTableViewer fieldViewer;
    private final String[] operationsDisplayNormal = { "equals", "not equals", "less than", "greater than", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            "less than or equals", "greater than or equals" }; //$NON-NLS-1$ //$NON-NLS-2$
    private final String[] operationsDisplayString = {
            "equals", "not equals", "like", "starts with", "ends with", "contains", "less than", "greater than", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            "less than or equals", "greater than or equals" }; //$NON-NLS-1$ //$NON-NLS-2$
    private final String[] operationsDisplayMulti = { "equals", "not equals", "includes", "excludes" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private HashMap<String, String> operationMap;
    private CCombo whereFieldCombo;
    private Composite whereComp;
    private Composite builderComp;
    private boolean isPickListField;
    private boolean isFocusDialogWanted = true;
    private int lastFieldType;
    private Text valueText;
    private PicklistEntry[] picklistValues;
    private static final String SPACE = " "; //$NON-NLS-1$
    private static final String BEGIN_SINGLEQUOTE = " '"; //$NON-NLS-1$
    private static final String END_SINGLEQUOTE = "' "; //$NON-NLS-1$
    private static final String WILD_CARD = "%"; //$NON-NLS-1$
    private static final String OPEN_BRACKET = "(";//$NON-NLS-1$
    private static final String CLOSE_BRACKET = ")";//$NON-NLS-1$

    // fieldType constants
    private static final int FIELD_STRING = 0;
    private static final int FIELD_MULTI = 1;
    private static final int FIELD_NORMAL = 2;

    // SOQL building variables
    private StringBuffer fromEntityPart;
    private final String SELECT = "SELECT "; //$NON-NLS-1$
    private StringBuffer selectFieldsClausePart = new StringBuffer();
    private StringBuffer wherePart = new StringBuffer();
    private CCombo operCombo;
    private ArrayList<Field> selectedFieldsInFieldViewer = new ArrayList<Field>();
    private Button addWhereClause;
    private Button clearAllWhereClauses;

    public ExtractionSOQLPage(Controller controller) {
        super("ExtractionSOQLPage", controller); //$NON-NLS-1$ //$NON-NLS-2$
        initOperMap();
        lastFieldType = FIELD_NORMAL;
        isPickListField = false;
    }

    private void initOperMap() {
        operationMap = new HashMap<String, String>();
        operationMap.put("equals", "=");
        operationMap.put("not equals", "!=");
        operationMap.put("like", "like");
        operationMap.put("less than", "<");
        operationMap.put("greater than", ">");
        operationMap.put("less than or equals", "<=");
        operationMap.put("greater than or equals", ">=");
        operationMap.put("includes", "includes");
        operationMap.put("excludes", "excludes");
        operationMap.put("starts with", "like");
        operationMap.put("ends with", "like");
        operationMap.put("contains", "like");

    }

    private void setLastFieldType(int type) {
        lastFieldType = type;
    }

    @Override
    public void createControl(Composite parent) {
        Composite comp = new Composite(parent, SWT.NONE);
        GridData data;

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.horizontalSpacing = 10;
        gridLayout.marginHeight = 20;
        comp.setLayout(gridLayout);

        builderComp = new Composite(comp, SWT.NONE);
        data = new GridData(SWT.FILL, SWT.FILL, true, true);
        builderComp.setLayoutData(data);
        gridLayout = new GridLayout(2, false);
        gridLayout.horizontalSpacing = 25;
        builderComp.setLayout(gridLayout);

        Label fieldLable = new Label(builderComp, SWT.LEFT);
        fieldLable.setText(Labels.getString("ExtractionSOQLPage.chooseFields")); //$NON-NLS-1$

        Label fieldWhere = new Label(builderComp, SWT.LEFT);
        fieldWhere.setText(Labels.getString("ExtractionSOQLPage.createClauses")); //$NON-NLS-1$

        Composite fieldComp = new Composite(builderComp, SWT.NONE);
        gridLayout = new GridLayout(1, false);
        gridLayout.horizontalSpacing = 25;
        fieldComp.setLayout(gridLayout);
        data = new GridData(GridData.FILL_BOTH);
        fieldComp.setLayoutData(data);

        Text search = new Text(fieldComp, SWT.SEARCH | SWT.ICON_CANCEL | SWT.ICON_SEARCH);
        data = new GridData(GridData.FILL_HORIZONTAL);
        search.setLayoutData(data);

        fieldViewer = CheckboxTableViewer.newCheckList(fieldComp, SWT.BORDER);
        ColumnViewerToolTipSupport.enableFor(fieldViewer);
        fieldViewer.setLabelProvider(new ExtrFieldLabelProvider());
        fieldViewer.setContentProvider(new ExtrFieldContentProvider());
        data = new GridData(GridData.FILL_BOTH);
        data.widthHint = 50;
        data.heightHint = 120;
        fieldViewer.getTable().setLayoutData(data);
        fieldViewer.getTable().getHorizontalBar().setVisible(true);

        FieldFilter filter = new FieldFilter(search);
        fieldViewer.addFilter(filter);
        fieldViewer.addCheckStateListener(new ICheckStateListener() {
            @Override
            public void checkStateChanged(CheckStateChangedEvent event) {
                Field field = (Field)event.getElement();
                if (event.getChecked()) {
                    selectedFieldsInFieldViewer.add(field);
                } else {
                    for (Field selectedField : selectedFieldsInFieldViewer) {
                        if (selectedField.getName().equalsIgnoreCase(field.getName())) {
                            selectedFieldsInFieldViewer.remove(field);
                            break;
                        }
                    }                    
                }
                updateSoQLTextAndButtons();
            }
        });
        
        search.addSelectionListener(new SelectionAdapter() {
            public void widgetDefaultSelected(SelectionEvent e) {
                preserveFieldViewerCheckedItems();
                fieldViewer.refresh();
            }
        });
        
        search.addListener(SWT.KeyUp, new Listener() {
            public void handleEvent(Event e) {
                preserveFieldViewerCheckedItems();
                fieldViewer.refresh();
            }
        });

        whereComp = new Composite(builderComp, SWT.NONE);
        data = new GridData(GridData.FILL_VERTICAL);
        whereComp.setLayoutData(data);
        gridLayout = new GridLayout(2, false);
        whereComp.setLayout(gridLayout);

        Label fLabel = new Label(whereComp, SWT.RIGHT);
        fLabel.setText(Labels.getString("ExtractionSOQLPage.fields")); //$NON-NLS-1$
        fLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));

        whereFieldCombo = new CCombo(whereComp, SWT.DROP_DOWN | SWT.LEFT);
        data = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
        whereFieldCombo.setLayoutData(data);
        whereFieldCombo.addSelectionListener(new SelectionListener() {

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}

            @Override
            public void widgetSelected(SelectionEvent event) {
                // get the selected string
                String name = whereFieldCombo.getText();
                setAddWhereButtonState();

                // get the corresponding field
                if (name != null && name.length() > 0) {
                    for (int i = 0; i < fieldsInSObject.length; i++) {
                        Field field = fieldsInSObject[i];
                        if (name.equals(field.getName())) {

                            // picklist values
                            if (field.getType() == FieldType.picklist || field.getType() == FieldType.multipicklist) {
                                picklistValues = field.getPicklistValues();
                                isPickListField = true;
                            } else {
                                isPickListField = false;
                            }

                            // operations values
                            if (field.getType() == FieldType.string && lastFieldType != FIELD_STRING) {
                                operCombo.setItems(operationsDisplayString);
                                operCombo.setText(operationsDisplayString[0]);
                                setLastFieldType(FIELD_STRING);
                            } else if (field.getType() == FieldType.multipicklist && lastFieldType != FIELD_MULTI) {
                                operCombo.setItems(operationsDisplayMulti);
                                operCombo.setText(operationsDisplayMulti[0]);
                                setLastFieldType(FIELD_MULTI);
                            } else if (lastFieldType != FIELD_NORMAL && field.getType() != FieldType.multipicklist
                                    && !field.getType().toString().equals("string")) {
                                operCombo.setItems(operationsDisplayNormal);
                                operCombo.setText(operationsDisplayNormal[0]);
                                setLastFieldType(FIELD_NORMAL);
                            }
                            break;
                        }
                    }
                }
            }
        });
        
        whereFieldCombo.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // Do nothing
            }
        });
                
        whereFieldCombo.addMouseListener(new MouseListener() {

            @Override
            public void mouseDoubleClick(MouseEvent arg0) {
                // Do nothing
                
            }

            @Override
            public void mouseDown(MouseEvent arg0) {
                String text = whereFieldCombo.getText();
                updateWhereFieldComboList(text);
            }

            @Override
            public void mouseUp(MouseEvent arg0) {
                // setAddWhereButtonState();
            }
        });

        Label opLabel = new Label(whereComp, SWT.RIGHT);
        opLabel.setText(Labels.getString("ExtractionSOQLPage.operation")); //$NON-NLS-1$
        opLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        operCombo = new CCombo(whereComp, SWT.DROP_DOWN | SWT.LEFT | SWT.READ_ONLY);
        data = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
        operCombo.setLayoutData(data);
        operCombo.setItems(operationsDisplayNormal);
        operCombo.setText(operationsDisplayNormal[0]);
        operCombo.addSelectionListener(new SelectionListener() {

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {}

            @Override
            public void widgetSelected(SelectionEvent event) {
                setAddWhereButtonState();
            }
        });

        Label valLabel = new Label(whereComp, SWT.RIGHT);
        valLabel.setText(Labels.getString("ExtractionSOQLPage.value")); //$NON-NLS-1$
        valLabel.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_END));
        valueText = new Text(whereComp, SWT.BORDER);
        valueText.setTextLimit(70);
        data = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING | GridData.FILL_HORIZONTAL);
        valueText.setLayoutData(data);
        valueText.addKeyListener(new KeyListener() {
            public void keyReleased(KeyEvent key) {
                setAddWhereButtonState();
            }

            @Override
            public void keyPressed(KeyEvent arg0) {
                // DO NOTHING
                
            }
        });
        valueText.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {

                // if a picklist, pop a list
                if (isPickListField && isFocusDialogWanted) {
                    ExtrPopupList popUp = new ExtrPopupList(valueText.getShell());

                    String[] values = new String[picklistValues.length];
                    for (int x = 0, end = picklistValues.length; x < end; x++) {
                        values[x] = picklistValues[x].getValue();
                    }
                    popUp.setItems(values);

                    Rectangle rect = valueText.getBounds();
                    Composite sizer = valueText.getParent();
                    Rectangle sizerRect;
                    // need to determine the absolute position
                    while (sizer != null) {
                        sizerRect = sizer.getBounds();
                        rect.x = rect.x + sizerRect.x;
                        rect.y = rect.y + sizerRect.y;
                        if (sizer instanceof Shell) break;
                        sizer = sizer.getParent();
                    }
                    // if we return to the text after a selection, we don't want to pop up again
                    isFocusDialogWanted = false;
                    String selection = popUp.open(rect);
                    if (selection != null) {
                        valueText.setText(selection);
                    } else {
                        // this ordering is wacky because of when the next event gets thrown in Windows
                        isFocusDialogWanted = true;
                    }

                } else {
                    isFocusDialogWanted = true;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                // Do Nothing
            }
        });

        Composite whereButtonsComp = new Composite(whereComp, SWT.NONE);
        data = new GridData(GridData.FILL_BOTH);
        data.horizontalSpan = 2;
        whereButtonsComp.setLayoutData(data);
        gridLayout = new GridLayout(3, false);
        whereButtonsComp.setLayout(gridLayout);
        addWhereClause = new Button(whereButtonsComp, SWT.PUSH | SWT.FLAT);
        addWhereClause.setText(Labels.getString("ExtractionSOQLPage.addCondition")); //$NON-NLS-1$
        addWhereClause.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String whereField = whereFieldCombo.getText();
                String whereOper = operCombo.getText();
                String whereValue = valueText.getText();

                if (validateStr(whereField) && validateStr(whereOper)) {
                    if (wherePart.length() == 0) {
                        wherePart.append("WHERE \n    "); //$NON-NLS-1$
                    } else {
                        wherePart.append("\n    AND "); //$NON-NLS-1$
                    }

                    boolean isSingleQuoteValue = isSingleQuoteValue(whereField);
                    wherePart.append(whereField);
                    wherePart.append(SPACE);
                    wherePart.append(getOperValue(whereOper));

                    boolean isMultiPickList = isMultiPicklistOper(whereOper);

                    if (isMultiPickList) {
                        wherePart.append(SPACE);
                        wherePart.append(OPEN_BRACKET);
                    }
                    if (isSingleQuoteValue) {
                        wherePart.append(BEGIN_SINGLEQUOTE);
                    } else {
                        wherePart.append(SPACE);
                    }
                    if (whereOper.equals("ends with") || whereOper.equals("contains")) {
                        wherePart.append(WILD_CARD);
                    }
                    wherePart.append(whereValue);
                    if (whereOper.equals("starts with") || whereOper.equals("contains")) {
                        wherePart.append(WILD_CARD);
                    }

                    if (isSingleQuoteValue) {
                        wherePart.append(END_SINGLEQUOTE);
                    } else {
                        wherePart.append(SPACE);
                    }
                    if (isMultiPickList) {
                        wherePart.append(CLOSE_BRACKET);
                    }
                }
                updateSoQLTextAndButtons();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
        setAddWhereButtonState();

        clearAllWhereClauses = new Button(whereButtonsComp, SWT.PUSH | SWT.FLAT);
        clearAllWhereClauses.setText(Labels.getString("ExtractionSOQLPage.clearAllConditions")); //$NON-NLS-1$
        data = new GridData();
        data.horizontalSpan = 2;
        clearAllWhereClauses.setLayoutData(data);
        setClearWhereButtonState();
        clearAllWhereClauses.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                wherePart = new StringBuffer();
                updateSoQLTextAndButtons();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });

        // button Comp for fields
        Composite fieldButtonComp = new Composite(builderComp, SWT.NONE);
        gridLayout = new GridLayout(2, false);
        fieldButtonComp.setLayout(gridLayout);

        Button selectAll = new Button(fieldButtonComp, SWT.PUSH | SWT.FLAT);
        selectAll.setText(Labels.getString("ExtractionSOQLPage.selectAllFields")); //$NON-NLS-1$
        selectAll.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                fieldViewer.setAllChecked(true);
                updateSoQLTextAndButtons();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });

        Button clearAll = new Button(fieldButtonComp, SWT.PUSH | SWT.FLAT);
        clearAll.setText(Labels.getString("ExtractionSOQLPage.clearAllFields")); //$NON-NLS-1$
        clearAll.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                fieldViewer.setAllChecked(false);
                updateSoQLTextAndButtons();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}
        });
        setClearWhereButtonState();

        new Label(builderComp, SWT.NONE);

        // the bottom separator
        Label labelSeparator = new Label(comp, SWT.SEPARATOR | SWT.HORIZONTAL);
        data = new GridData(GridData.FILL_HORIZONTAL);
        labelSeparator.setLayoutData(data);

        Label messageLabel = new Label(comp, SWT.NONE);
        messageLabel.setText(Labels.getString("ExtractionSOQLPage.queryBelowMsg")); //$NON-NLS-1$

        soqlText = new Text(comp, SWT.MULTI | SWT.WRAP | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        data = new GridData(GridData.FILL_BOTH);
        data.heightHint = 80;
        soqlText.setLayoutData(data);

        soqlText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent arg0) {
                updateWherePart();
                setPageComplete();
            }
        });
        
        labelSeparator = new Label(comp, SWT.SEPARATOR | SWT.HORIZONTAL);
        data = new GridData(GridData.FILL_HORIZONTAL);
        labelSeparator.setLayoutData(data);
        setControl(comp);
        setupPage();
    }
    
    private void setAddWhereButtonState() {
        if (this.whereFieldCombo.getText() != null
                && !this.whereFieldCombo.getText().isBlank()
                && this.operCombo.getText() != null
                && !this.operCombo.getText().isBlank()
                && this.valueText.getText() != null
                && !this.valueText.getText().isBlank()
                && this.soqlText.getText() != null
                && !this.soqlText.getText().isBlank()) {
            this.addWhereClause.setEnabled(true);
        } else {
            this.addWhereClause.setEnabled(false);
        }
    }
    
    private void setClearWhereButtonState() {
        if (this.wherePart.length() == 0 || this.wherePart.toString().isBlank()) {
            this.clearAllWhereClauses.setEnabled(false);
        } else {
            this.clearAllWhereClauses.setEnabled(true);
        }
    }

    private String getOperValue(String operation) {
        return operationMap.get(operation);
    }
    
    private void preserveFieldViewerCheckedItems() {
        Field field = (Field)fieldViewer.getElementAt(0);
        int i = 0;
        while (field != null) {
            fieldViewer.setChecked(field, selectedFieldsInFieldViewer.contains(field));
            field = (Field)fieldViewer.getElementAt(++i);
        }
    }

    private boolean isSingleQuoteValue(String fieldName) {
        Field field;
        for (int i = 0; i < fieldsInSObject.length; i++) {
            field = fieldsInSObject[i];
            if (field.getName().equals(fieldName)) {
                switch (field.getType()) {
                case _boolean:
                case _double:
                case _int:
                case currency:
                case date:
                case datetime:
                case percent:
                    // don't quote above types
                    return false;
                default:
                    // quote the rest
                    // string:
                    // base64:
                    // combobox:
                    // email:
                    // id:
                    // multipicklist:
                    // phone:
                    // picklist:
                    // reference:
                    // textarea:
                    // url:
                    return true;
                }
            }
        }
        return true;
    }

    private boolean isMultiPicklistOper(String value) {
        return (value.equals("includes") || value.equals("excludes"));
    }

    private void generateSelectFromPart() {
        Field field = (Field)fieldViewer.getElementAt(0);
        int i = 0;
        while (field != null) {
            if (fieldViewer.getChecked(field)) {
                if (!selectedFieldsInFieldViewer.contains(field)) {
                    selectedFieldsInFieldViewer.add(field);
                }
            } else {
                selectedFieldsInFieldViewer.remove(field);
            }
            field = (Field)fieldViewer.getElementAt(++i);
        }

        selectFieldsClausePart = new StringBuffer();
        for (Field selectedField : selectedFieldsInFieldViewer) {
            selectFieldsClausePart.append(selectedField.getName());
            selectFieldsClausePart.append(", "); //$NON-NLS-1$
        }
        if (selectFieldsClausePart.length() > 0) {
            selectFieldsClausePart = new StringBuffer(selectFieldsClausePart.substring(0, selectFieldsClausePart.length()-2));
            selectFieldsClausePart.append(" ");
        }
    }

    private boolean validateStr(String str) {
        if (str != null && str.length() > 0) { return true; }
        return false;
    }

    protected boolean setupPagePostLogin() {
        initializeSOQLText();
        return true;
    }

    private void initializeSOQLText() {
        logger.debug(Labels.getString("ExtractionSOQLPage.initializeMsg")); //$NON-NLS-1$
        Config config = controller.getConfig();
        String entityStr = config.getString(Config.ENTITY);

        if (entityStr == null || entityStr.isBlank()) {
            return;
        }
        DescribeSObjectResult result = controller.getFieldTypes();
        fieldsInSObject = result.getFields();
        if (config.getBoolean(Config.SORT_EXTRACT_FIELDS)) {
            Arrays.sort(fieldsInSObject, new Comparator<Field>(){
                @Override
                public int compare(Field f1, Field f2)
                {
                    return f1.getName().compareTo(f2.getName());
                }
            });
        }
        fieldViewer.setInput(fieldsInSObject);
        updateWhereFieldComboList(null);
        builderComp.layout();
        whereComp.layout();
        fromEntityPart = new StringBuffer("\nFROM ").append(config.getString(Config.ENTITY)).append(" "); //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    private void updateWhereFieldComboList(String filterStr) {
        List<String> fieldNames = new ArrayList<String>();
        for (int i = 0; i < fieldsInSObject.length; i++) {
            // include all fields except encrypted string ones
            String name = fieldsInSObject[i].getName().toLowerCase();
            if(FieldType.encryptedstring != fieldsInSObject[i].getType()) {
                if (filterStr == null 
                        || filterStr.isEmpty() 
                        || name.contains(filterStr.toLowerCase())) {
                    fieldNames.add(fieldsInSObject[i].getName());
                }
            }
        }
        String[] fieldNamesArray = fieldNames.toArray(new String[fieldNames.size()]);
        Arrays.sort(fieldNamesArray);
        whereFieldCombo.setItems(fieldNamesArray);
    }

    private void updateSoQLTextAndButtons() {
        generateSelectFromPart();
        if (selectFieldsClausePart == null || selectFieldsClausePart.toString().isBlank()) {
            // clear the SoQL text and where clause
            wherePart = new StringBuffer("");
            soqlText.setText("");
        } else {
            StringBuffer soql = new StringBuffer(SELECT);
            soql.append(selectFieldsClausePart);
            soql.append(fromEntityPart);
            if (wherePart != null && !wherePart.toString().isBlank()) {
                soql.append("\n");
                soql.append(wherePart);
            }
            soqlText.setText(soql.toString());
        }
        setAddWhereButtonState();
        setClearWhereButtonState();
    }
    
    private void updateWherePart() {
        String soqlStr = soqlText.getText();
        String whereClause = "";
        if (soqlStr != null && !soqlStr.isBlank()) {
            soqlStr = soqlStr.toLowerCase();
            int idx = soqlStr.indexOf("where");
            if (idx >= 0) {
                whereClause = soqlText.getText().substring(idx);
            }
        }
        wherePart = new StringBuffer(whereClause);
    }

    public String getSOQL() {
        return soqlText.getText();
    }

    @Override
    public IWizardPage getNextPage() {
        String finishStepPageStr = ExtractionFinishPage.class.getSimpleName(); //$NON-NLS-1$
        ExtractionFinishPage finishStepPage = (ExtractionFinishPage)getWizard().getPage(finishStepPageStr);
        if (!saveSoQL()) {
            setPageComplete(false);
            return null; // do not proceed to the next page if SoQL is not specified
        }
        // get the next wizard page
        if (finishStepPage != null) {
            finishStepPage.setupPage();
            return finishStepPage;
        } else {
            return super.getNextPage();
        }
    }

    /*
     * (non-Javadoc)
     * @see com.salesforce.dataloader.ui.OperationPage#finishAllowed()
     */
    @Override
    public boolean finishAllowed() {
        if (this.controller.getConfig().getBoolean(Config.ENABLE_EXTRACT_STATUS_OUTPUT)) {
            // this page is not the finish page if extract status output is enabled
            return false;
        }
        return saveSoQL();
    }
    
    private boolean saveSoQL() {
        String soqlStr = getSOQL();
        if (soqlStr == null || soqlStr.isBlank()) {
            return false;
        }
        controller.getConfig().setValue(Config.EXTRACT_SOQL, soqlStr);
        if (!controller.saveConfig()) { return false; }
        return true;
    }

    @Override
    public void setPageComplete() {
        setPageComplete(saveSoQL());
    }
    
    @Override
    public boolean canFlipToNextPage() {
        return (this.controller.getConfig().getBoolean(Config.ENABLE_EXTRACT_STATUS_OUTPUT) && isPageComplete());
     }
}