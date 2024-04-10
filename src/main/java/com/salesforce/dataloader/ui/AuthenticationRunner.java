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

import com.salesforce.dataloader.client.DefaultSimplePost;
import com.salesforce.dataloader.config.Config;
import com.salesforce.dataloader.controller.Controller;
import com.salesforce.dataloader.model.LoginCriteria;
import com.salesforce.dataloader.util.ExceptionUtil;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.fault.LoginFault;
import com.sforce.soap.partner.fault.UnexpectedErrorFault;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.bind.XmlObject;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;

import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * AuthenticationRunner is the UI orchestration of logging in.
 */
public class AuthenticationRunner {
    private static Logger logger = LogManager.getLogger(AuthenticationRunner.class);

    private final Config config;
    private final Controller controller;
    private final Consumer<Boolean> complete;
    private final String nestedException = "nested exception is:";
    private final Shell shell;
    private Consumer<String> messenger;
    private LoginCriteria criteria;


    public AuthenticationRunner(Shell shell, Config config, Controller controller, Consumer<Boolean> complete) {
        this.shell = shell;
        this.config = config;
        this.controller = controller;
        this.complete = complete;
    }

    public Config getConfig() {
        return config;
    }



    public void login(LoginCriteria criteria, Consumer<String> messenger) {
        this.messenger = messenger;
        this.criteria = criteria;

        criteria.updateConfig(config);

        BusyIndicator.showWhile(Display.getDefault(), new Thread(this::loginAsync));
    }

    private void loginAsync(){
        try {
            messenger.accept(Labels.getString("LoginPage.verifyingLogin"));

            if ((criteria.getMode() == LoginCriteria.OAuthLoginDefault)
            	&& config.getBoolean(Config.OAUTH_LOGIN_FROM_BROWSER)
            	&& config.getString(Config.OAUTH_CLIENTID) != null
            	&& !config.getString(Config.OAUTH_CLIENTID).isEmpty()) {
            	LoginCriteria existingCriteria = criteria;
            	criteria = new LoginCriteria(LoginCriteria.OAuthLoginFromBrowser);
            	criteria.setEnvironment(existingCriteria.getEnvironment());
            	criteria.setInstanceUrl(existingCriteria.getInstanceUrl());
            	criteria.setUserName(existingCriteria.getUserName());
            	criteria.setPassword(existingCriteria.getPassword());
            }
            if (criteria.getMode() == LoginCriteria.OAuthLoginDefault){

                boolean hasSecret = !config.getString(Config.OAUTH_CLIENTSECRET).trim().equals("");
                OAuthFlow flow = hasSecret ? new OAuthSecretFlow(shell, config) : new OAuthTokenFlow(shell, config);
                if (!flow.open()){
                    String message = Labels.getString("LoginPage.invalidLogin");
                    if (flow.getStatusCode() == DefaultSimplePost.PROXY_AUTHENTICATION_REQUIRED) {
                        message = Labels.getFormattedString("LoginPage.proxyError", flow.getReasonPhrase());
                    }

                    if (flow.getReasonPhrase() == null) {
                        logger.info("OAuth login dialog closed without logging in");
                    } else {
                        logger.info("Login failed:" + flow.getReasonPhrase());
                    }
                    messenger.accept(message);
                    complete.accept(false);
                    return;
                }
            } else if (criteria.getMode() == LoginCriteria.OAuthLoginFromBrowser) {
            	OAuthLoginFromBrowserFlow flow = new OAuthLoginFromBrowserFlow(shell, config);
            	if (!flow.open()) {
	                String message = Labels.getString("LoginPage.invalidLogin");
	                messenger.accept(message);
	                complete.accept(false);
	                return;
            	}
            }
            if (controller.login() && controller.getEntityDescribes() != null) {
                messenger.accept(Labels.getString("LoginPage.loginSuccessful"));
                controller.saveConfig();
                controller.updateLoaderWindowTitleAndCacheUserInfoForTheSession();
                PartnerConnection conn = controller.getPartnerClient().getConnection();
                logger.debug("org_id = " + conn.getUserInfo().getOrganizationId());
                logger.debug("user_id = " + conn.getUserInfo().getUserId());
                if (logger.getLevel() == Level.DEBUG) { 
                    // avoid making a remote API call to the server unless log level is DEBUG
                    logger.debug(getSoqlResultsAsString(
                            "\nConnected App Information: ",
                            "SELECT Name, id FROM ConnectedApplication WHERE name like 'Dataloader%'"
                                    , conn));
                    logger.debug(getSoqlResultsAsString(
                            "\nOrg Instance Information:",
                            "SELECT InstanceName FROM Organization"
                                    , conn));
                    }
                complete.accept(true);
            } else {
                messenger.accept(Labels.getString("LoginPage.invalidLogin"));
                complete.accept(false);
            }
        } catch (LoginFault lf ) {
            messenger.accept(Labels.getString("LoginPage.invalidLogin"));
            complete.accept(false);
        } catch (UnexpectedErrorFault e) {
            handleError(e, e.getExceptionMessage());
        } catch (Throwable e) {
            handleError(e, e.getMessage());
        }
    }
        
    private String getSoqlResultsAsString(String prefix, String soql, PartnerConnection conn) throws ConnectionException {
        QueryResult result = conn.query(soql);
        final SObject[] sfdcResults = result.getRecords();
        String debugInfo = prefix;
        if (sfdcResults != null) {
            for (SObject sobj : sfdcResults) {
                Iterator<XmlObject> fields = sobj.getChildren();
                if (fields == null) continue;
                String fieldsStr = "    ";
                boolean isIdInFields = false;
                while (fields.hasNext()) {
                    XmlObject field = fields.next();
                    if ("type".equalsIgnoreCase(field.getName().getLocalPart())) {
                        continue;
                    }
                    if (field.getValue() == null || field.getValue().toString().isBlank()) {
                        continue;
                    }
                    if ("id".equalsIgnoreCase(field.getName().getLocalPart())) {
                        if (isIdInFields) {
                            continue;
                        }
                        isIdInFields = true;
                    }
                    fieldsStr += field.getName().getLocalPart() + " = " + field.getValue() + "  ";
                }
                debugInfo += "\n" + fieldsStr;
            }
        }
        return debugInfo;
    }

    private void handleError(Throwable e, String message) {
        if (message == null || message.length() < 1) {
            messenger.accept(Labels.getString("LoginPage.invalidLogin"));
        } else {
            int x = message.indexOf(nestedException);
            if (x >= 0) {
                x += nestedException.length();
                message = message.substring(x);
            }
            messenger.accept(message.replace('\n', ' ').trim());
        }
        complete.accept(false);
        logger.error(message);
        logger.error("\n" + ExceptionUtil.getStackTraceString(e));
    }
}
