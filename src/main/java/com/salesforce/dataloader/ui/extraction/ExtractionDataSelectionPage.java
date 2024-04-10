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

import java.io.File;
import java.util.*;
import java.util.Map.Entry;

import org.eclipse.jface.viewers.*;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

import com.salesforce.dataloader.controller.Controller;
import com.salesforce.dataloader.dao.DataAccessObjectFactory;
import com.salesforce.dataloader.exception.MappingInitializationException;
import com.salesforce.dataloader.ui.EntitySelectionListViewerUtil;
import com.salesforce.dataloader.ui.Labels;
import com.salesforce.dataloader.ui.UIUtils;
import com.sforce.soap.partner.DescribeGlobalSObjectResult;

/**
 * Describe your class here.
 *
 * @author Lexi Viripaeff
 * @since 6.0
 */
public class ExtractionDataSelectionPage extends ExtractionPage {

    // These filter extensions are used to filter which files are displayed.
    private ListViewer lv;
    private Text fileText;
    public Composite comp;

    public ExtractionDataSelectionPage(Controller controller) {
        super("ExtractionDataSelectionPage", controller); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public void createControl(Composite parent) {
        getShell().setImage(UIUtils.getImageRegistry().get("sfdc_icon")); //$NON-NLS-1$

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.horizontalSpacing = 10;
        gridLayout.marginHeight = 15;
        gridLayout.verticalSpacing = 5;
        gridLayout.marginRight = 5;

        comp = new Composite(parent, SWT.NONE);
        comp.setLayout(gridLayout);
        lv = EntitySelectionListViewerUtil.getEntitySelectionListViewer(this.getClass(), comp, this.controller.getConfig());
        lv.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                setPageComplete();
            }

        });

        setupPage();

        Label clearLabel = new Label(comp, SWT.NONE);
        GridData data = new GridData(GridData.VERTICAL_ALIGN_END);
        data.heightHint = 20;
        clearLabel.setLayoutData(data);

        //now select the file
        Composite compChooser = new Composite(comp, SWT.NONE);
        data = new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_END);

        compChooser.setLayoutData(data);

        GridLayout gLayout = new GridLayout(3, false);
        compChooser.setLayout(gLayout);

        //file Label
        Label fileLabel = new Label(compChooser, SWT.NONE);
        fileLabel.setText(Labels.getString("ExtractionDataSelectionPage.chooseTarget")); //$NON-NLS-1$

        //file text
        fileText = new Text(compChooser, SWT.BORDER);
        fileText.setText(Labels.getString("ExtractionDataSelectionPage.defaultFileName")); //$NON-NLS-1$
        data = new GridData(GridData.FILL_HORIZONTAL);
        fileText.setLayoutData(data);

        fileText.addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                setPageComplete();
            }
        });

        Button fileButton = new Button(compChooser, SWT.PUSH | SWT.FLAT);
        fileButton.setText(Labels.getString("ExtractionDataSelectionPage.chooseFile")); //$NON-NLS-1$
        fileButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                FileDialog dlg = new FileDialog(getShell(), SWT.SAVE);
                String initialFile = fileText.getText();
                if(initialFile.length() == 0) {
                    initialFile = Labels.getString("ExtractionDataSelectionPage.defaultFileName"); //$NON-NLS-1$
                }
                dlg.setFileName(initialFile);
                String filename = dlg.open();
                if (filename != null && !"".equals(filename)) { //$NON-NLS-1$
                    //set the text, and see if the page is valid
                    fileText.setText(filename);
                    setPageComplete();
                }
            }
        });

        setControl(comp);
    }
    
    protected boolean setupPagePostLogin() {
        if (this.controller.isLoggedIn()) {
            setInput(this.controller.getEntityDescribes());
            lv.refresh();
        }
        return true;
    }

    /**
     * Function to dynamically set the entity list
     */
    private void setInput(Map<String, DescribeGlobalSObjectResult> entityDescribes) {
        Map<String, DescribeGlobalSObjectResult> inputDescribes = new HashMap<String, DescribeGlobalSObjectResult>();

        if (entityDescribes != null) {
            // for each object, check whether the object is valid for the Extract operation
            for (Entry<String, DescribeGlobalSObjectResult> entry : entityDescribes.entrySet()) {
                if (entry.getValue().isQueryable()) {
                    inputDescribes.put(entry.getKey(), entry.getValue());
                }
            }
        }
        lv.setInput(inputDescribes);
        lv.getControl().getParent().pack();
        lv.refresh();
    }

    private boolean checkEntityStatus() {
        IStructuredSelection selection = (IStructuredSelection)lv.getSelection();
        DescribeGlobalSObjectResult entity = (DescribeGlobalSObjectResult)selection.getFirstElement();
        if (entity != null) { return true; }
        return false;

    }

    public void setPageComplete() {

        if (!(fileText.getText().equals("")) && checkEntityStatus()) { //$NON-NLS-1$
            setPageComplete(true);
        } else {
            setPageComplete(false);
        }

    }

    /**
     * Returns the next page, describes SObject and performs the total size calculation
     *
     * @return IWizardPage
     */

    @Override
    public IWizardPage getNextPage() {

        // if output file already exists, confirm that the user wants to replace it
        if(new File(fileText.getText()).exists()) {
            int button = UIUtils.warningConfMessageBox(getShell(), Labels.getString("UI.fileAlreadyExists"));
            if(button == SWT.NO) {
                return this;
            }
        }

        //get entity
        IStructuredSelection selection = (IStructuredSelection)lv.getSelection();
        DescribeGlobalSObjectResult entity = (DescribeGlobalSObjectResult)selection.getFirstElement();

        try {
            // reinitialize the data mapping (UI extraction currently uses only implicit mapping)
            controller.initializeOperation(DataAccessObjectFactory.CSV_WRITE_TYPE, 
                    fileText.getText(), entity.getName());
        } catch (MappingInitializationException e) {
            UIUtils.errorMessageBox(getShell(), e);
            return this;
        }

        //set the query
        ExtractionSOQLPage soql = (ExtractionSOQLPage)getWizard().getPage(ExtractionSOQLPage.class.getSimpleName()); //$NON-NLS-1$
        soql.setupPage();
        soql.setPageComplete(true);
        return super.getNextPage();
    }

    // nothing to finish before moving to the next page
    public boolean finishPage() {
        return true;
    }
}
