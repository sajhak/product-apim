/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except 
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.am.integration.tests.rest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

import javax.ws.rs.core.Response;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.generic.APIMTestCaseUtils;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.integration.common.admin.client.AuthenticatorClient;
import org.wso2.carbon.integration.common.utils.mgt.ServerConfigurationManager;

/**
 * This test validates https://wso2.org/jira/browse/APIMANAGER-5916,
 * functionality to send a PUT request with empty body
 *
 */
public class APIMANAGER5916PUTWithEmptyBodyTestCase extends APIMIntegrationBaseTest {

	private final Log log = LogFactory.getLog(APIMANAGER5916PUTWithEmptyBodyTestCase.class);
	private String apiContext = "api5916";

	private ServerConfigurationManager serverConfigurationManager;

	@Factory(dataProvider = "userModeDataProvider")
	public APIMANAGER5916PUTWithEmptyBodyTestCase(TestUserMode userMode) {
		this.userMode = userMode;
	}

	@BeforeClass(alwaysRun = true)
	public void setEnvironment() throws Exception {
		super.init(userMode);
		serverConfigurationManager = new ServerConfigurationManager(
				new AutomationContext(APIMIntegrationConstants.AM_PRODUCT_GROUP_NAME,
						APIMIntegrationConstants.AM_GATEWAY_WRK_INSTANCE, TestUserMode.SUPER_TENANT_ADMIN));

		// apply axis2.xml changes
		log.info(" Applying axis2.xml ");
		serverConfigurationManager.applyConfigurationWithoutRestart(new File(getAMResourceLocation() + File.separator
				+ "configFiles" + File.separator + "APIM5916" + File.separator + "axis2.xml"));

		AuthenticatorClient login = new AuthenticatorClient(gatewayContextMgt.getContextUrls().getBackEndUrl());
		String session = login.login("admin", "admin", "localhost");

		// Upload the synapse
		String file = "artifacts" + File.separator + "AM" + File.separator + "synapseconfigs" + File.separator + "rest"
				+ File.separator + "api_chunking_disable_5916.xml";
		log.info(" [api_chunking_disable_5916.xml] synapse config loaded successfully");

		OMElement synapseConfig = APIMTestCaseUtils.loadResource(file);
		APIMTestCaseUtils.updateSynapseConfiguration(synapseConfig, gatewayContextMgt.getContextUrls().getBackEndUrl(),
				session);
		Thread.sleep(5000);

	}

	@Test(groups = "wso2.am", description = "Test for checking PUT operation of an API with chunking disabled and empty payload")
	public void testAPIInvocation() throws Exception {
		OutputStream out = null;
		HttpURLConnection connection = null;
		InputStream in = null;
		try {

			URL url = new URL(getAPIInvocationURLHttp(apiContext + "/1.0.0/aa"));
			connection = (HttpURLConnection) url.openConnection();
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("PUT");
			connection.setRequestProperty("Accept", "application/octet-stream");
			connection.setRequestProperty("Content-Type", "application/octet-stream");
			connection.setRequestProperty("Content-Length", "0");

			FileInputStream fis = new FileInputStream(getAMResourceLocation() + File.separator + "configFiles"
					+ File.separator + "APIM5916" + File.separator + "payload.json");
			InputStreamReader isr = new InputStreamReader(fis, "UTF8");
			Reader inputReader = new BufferedReader(isr);
			out = connection.getOutputStream();
			Writer writer = new OutputStreamWriter(out, "UTF-8");
			pipe(inputReader, writer);
			writer.close();
			in = connection.getInputStream();
			PrintWriter output = new PrintWriter(new BufferedWriter(new FileWriter("foo.out")));
			Reader reader = new InputStreamReader(in, Charset.defaultCharset());
			pipe(reader, output);
			reader.close();
			Assert.assertEquals(connection.getResponseCode(), Response.Status.OK.getStatusCode(),
					"Response code mismatched when api invocation");

		} catch (IOException e) {
			Assert.assertTrue(false, "Error occurred when invoking the API. [" + e.getMessage() + "]");
			log.error("Error occurred when invoking the API");
			throw new Exception("IOException while invoking API " + e.getMessage(), e);
		} finally {
			if (out != null) {
				out.close();
			}
			if (in != null) {
				in.close();
			}
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	@AfterClass(alwaysRun = true)
	public void destroy() throws Exception {
		serverConfigurationManager.restoreToLastConfiguration();
		super.cleanUp();
	}

	@DataProvider
	public static Object[][] userModeDataProvider() {
		return new Object[][] { new Object[] { TestUserMode.SUPER_TENANT_ADMIN }, };
	}

	private static void pipe(Reader reader, Writer writer) throws IOException {
		char[] buf = new char[1024];
		int read;
		while ((read = reader.read(buf)) >= 0) {
			writer.write(buf, 0, read);
		}
		writer.flush();
	}
}
