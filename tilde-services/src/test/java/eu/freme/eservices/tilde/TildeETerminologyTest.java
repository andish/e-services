/**
 * Copyright (C) 2015 Agro-Know, Deutsches Forschungszentrum für Künstliche Intelligenz, iMinds,
 * Institut für Angewandte Informatik e. V. an der Universität Leipzig,
 * Istituto Superiore Mario Boella, Tilde, Vistatec, WRIPL (http://freme-project.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.freme.eservices.tilde;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequestWithBody;

import eu.freme.bservices.testhelper.TestHelper;
import eu.freme.bservices.testhelper.ValidationHelper;
import eu.freme.bservices.testhelper.api.IntegrationTestSetup;
import eu.freme.common.conversion.rdf.RDFConstants;

/**
 * @author Jan Nehring - jan.nehring@dfki.de
 */

public class TildeETerminologyTest {

	String sourceLang = "en";
	String targetLang = "de";
	TestHelper testHelper;
	ValidationHelper validationHelper;

	@Before
	public void setup() {
		ApplicationContext context = IntegrationTestSetup
				.getContext(TestConstants.pathToPackage);
		testHelper = context.getBean(TestHelper.class);
		validationHelper = context.getBean(ValidationHelper.class);
	}
	
	private HttpRequestWithBody baseRequest() {
		String url = testHelper.getAPIBaseUrl() + "/e-terminology/tilde";
		return Unirest.post(url).queryString("source-lang", sourceLang)
				.queryString("target-lang", targetLang);
	}
	
	@Test
	public void testETermimnology() throws UnirestException, IOException,
			Exception {

		HttpResponse<String> response = baseRequest()
				.queryString("informat", "text")
				.queryString("input", "hello world")
				.queryString("outformat", "turtle").asString();

		validationHelper.validateNIFResponse(response,
				RDFConstants.RDFSerialization.TURTLE);

		String data = FileUtils.readFileToString(new File("src/test/resources/rdftest/e-terminology/example1.ttl"));
//		String data = FileUtils.readFileToString(new File("src/test/resources/rdftest/e-translate/data.turtle"));
		response = baseRequest().header("Content-Type", "text/turtle")
				.body(data).asString();
		validationHelper.validateNIFResponse(response, RDFConstants.RDFSerialization.TURTLE);

		assertTrue(response.getStatus() == 200);
		assertTrue(response.getBody().length() > 0);

		response = baseRequest()
				.queryString("informat", "text").queryString("outformat", "turtle").body("hello world")
				.asString();
		validationHelper.validateNIFResponse(response, RDFConstants.RDFSerialization.TURTLE);

		assertTrue(response.getStatus() == 200);
		assertTrue(response.getBody().length() > 0);

	}
}
