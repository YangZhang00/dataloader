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
package com.salesforce.dataloader.ui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.*;

import com.salesforce.dataloader.config.Config;
import com.salesforce.dataloader.controller.Controller;

public abstract class BaseDialog extends Dialog {

    private Controller controller;
    private String message;
    protected Logger logger;
    protected boolean success = true;

    /**
     * InputDialog constructor
     *
     * @param parent
     *            the parent
     */
    protected BaseDialog(Shell parent, Controller controller) {
        super(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        this.controller = controller;
        this.logger = LogManager.getLogger(this.getClass());
        setText(Labels.getString(this.getClass().getSimpleName() + ".title")); //$NON-NLS-1$
        setMessage(Labels.getString(this.getClass().getSimpleName() + ".message")); //$NON-NLS-1$
    }

    protected Shell openAndGetShell() {
        final Shell shell = new Shell(getParent(), getStyle());
        shell.setText(getText());
        shell.setImage(UIUtils.getImageRegistry().get("sfdc_icon")); //$NON-NLS-1$
        createContents(shell);
        shell.pack();
        shell.open();
        setShellBounds(shell);
        return shell;
    }
    
    protected void setShellBounds(Shell dialogShell) {
        Rectangle shellBounds = dialogShell.getBounds();
        Rectangle persistedWizardBounds = UIUtils.getPersistedWizardBounds(this.controller.getConfig());
        shellBounds.x = persistedWizardBounds.x + Config.DIALOG_X_OFFSET;
        shellBounds.y = persistedWizardBounds.y + Config.DIALOG_Y_OFFSET;
        dialogShell.setBounds(shellBounds);
    }
    
    protected abstract void createContents(Shell shell);
    
    /**
     * Gets the message
     *
     * @return String
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message
     *
     * @param message
     *            the new message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    protected Controller getController() {
        return this.controller;
    }

    /**
     * Opens the dialog and returns the input
     *
     * @return String
     */
    public boolean open() {
        // Create the dialog window
        Shell shell = openAndGetShell();
        Display display = shell.getDisplay();
        doWork(shell);
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        return success;
    }
    
    private void doWork(Shell shell) {
        Display display = shell.getDisplay();
        BusyIndicator.showWhile(display, new Thread() {
            @Override
            public void run() {
                processingWithBusyIndicator(shell);
            }
        });
    }
    
    protected void processingWithBusyIndicator(Shell shell) {
        // no op
    }
    
    protected Rectangle getPersistedDialogBounds() {
        Config config = getController().getConfig();
        Rectangle wizardBounds = UIUtils.getPersistedWizardBounds(config);
        int xOffset = wizardBounds.x + Config.DIALOG_X_OFFSET;
        int yOffset = wizardBounds.y + Config.DIALOG_Y_OFFSET;
        int width = wizardBounds.width;
        int height = wizardBounds.height;
        if (config != null) {
            try {
                xOffset = config.getInt(Config.WIZARD_X_OFFSET) + Config.DIALOG_X_OFFSET;
                yOffset = config.getInt(Config.WIZARD_Y_OFFSET) + Config.DIALOG_Y_OFFSET;
                width = config.getInt(Config.DIALOG_BOUNDS_PREFIX + getClass().getSimpleName() + Config.DIALOG_WIDTH_SUFFIX);
                height = config.getInt(Config.DIALOG_BOUNDS_PREFIX + getClass().getSimpleName() + Config.DIALOG_HEIGHT_SUFFIX);
            } catch (Exception ex) {
                // no op
            }
        }
        if (width == 0) {
            width = wizardBounds.width;
        }
        if (height == 0) {
            height = wizardBounds.height;
        }
        return new Rectangle(xOffset, yOffset, width, height);
    }
}