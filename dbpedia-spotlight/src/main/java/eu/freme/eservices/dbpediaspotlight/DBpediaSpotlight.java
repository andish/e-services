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
package eu.freme.eservices.dbpediaspotlight;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import eu.freme.common.conversion.rdf.RDFConversionService;
import eu.freme.common.exception.BadRequestException;
import eu.freme.common.exception.ExternalServiceFailedException;
import eu.freme.common.rest.BaseRestController;
import eu.freme.common.rest.NIFParameterSet;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;

import eu.freme.common.conversion.rdf.RDFConstants;

@RestController
public class DBpediaSpotlight extends BaseRestController {

    Logger logger = Logger.getLogger(DBpediaSpotlight.class);

    @Value("${freme.eentity.dbpediaSpotlightEndpointUrl:http://spotlight.nlp2rdf.aksw.org/spotlight}")
    String dbpediaSpotlightUrl;

    @Autowired
    RDFConversionService rdfConversionService;


    @RequestMapping(value = "/e-entity/dbpedia-spotlight/documents", method = {
            RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<String> execute(
            /*@RequestParam(value = "input", required = false) String input,
			@RequestParam(value = "i", required = false) String i,
			@RequestParam(value = "informat", required = false) String informat,
			@RequestParam(value = "f", required = false) String f,
			@RequestParam(value = "outformat", required = false) String outformat,
			@RequestParam(value = "o", required = false) String o,
			@RequestParam(value = "prefix", required = false) String prefix,
			@RequestParam(value = "p", required = false) String p,
			*/
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            @RequestHeader(value = "Content-Type", required = false) String contentTypeHeader,
            @RequestParam(value = "language", required = true) String languageParam,
            @RequestParam(value = "confidence", required = false) String confidenceParam,
            @RequestParam Map<String, String> allParams,
            @RequestBody(required = false) String postBody) {
        try {
            NIFParameterSet nifParameters = this.normalizeNif(postBody, acceptHeader, contentTypeHeader, allParams, false);

            Model inModel = ModelFactory.createDefaultModel();
            Model outModel = ModelFactory.createDefaultModel();

            // Check the language parameter.
            if (!languageParam.equals("en")) {
                // The language specified with the langauge parameter is not supported.
                throw new eu.freme.common.exception.BadRequestException("Unsupported language [" + languageParam + "].");
            }

            String textForProcessing = null;

            if (nifParameters.getInformat().equals(RDFConstants.RDFSerialization.PLAINTEXT)) {
                // input is sent as value of the input parameter
                textForProcessing = nifParameters.getInput();
            } else {

                inModel = rdfConversionService.unserializeRDF(nifParameters.getInput(), nifParameters.getInformat());

                StmtIterator iter = inModel.listStatements(null, RDF.type, inModel.getResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#Context"));

                boolean textFound = false;
                String tmpPrefix = "http://freme-project.eu/#";
                // The first nif:Context with assigned nif:isString will be processed.
                while (!textFound) {
                    Resource contextRes = iter.nextStatement().getSubject();
                    tmpPrefix = contextRes.getURI().split("#")[0];
//                    System.out.println(tmpPrefix);
                    nifParameters.setPrefix(tmpPrefix + "#");
//                    System.out.println(parameters.getPrefix());
                    Statement isStringStm = contextRes.getProperty(inModel.getProperty("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#isString"));
                    if (isStringStm != null) {
                        textForProcessing = isStringStm.getObject().asLiteral().getString();
                        textFound = true;
                    }
                }

                if (textForProcessing == null) {
                    throw new eu.freme.common.exception.BadRequestException("No text to process.");
                }
            }
//            System.out.println("the prefix: "+parameters.getPrefix());

            validateConfidenceScore(confidenceParam);

            String dbpediaSpotlightRes = callDBpediaSpotlight(textForProcessing, confidenceParam, languageParam, nifParameters.getPrefix());
            outModel.read(new ByteArrayInputStream(dbpediaSpotlightRes.getBytes()), null, "TTL");
            outModel.add(inModel);
            // remove unwanted info
            outModel.removeAll(null, RDF.type, OWL.ObjectProperty);
            outModel.removeAll(null, RDF.type, OWL.DatatypeProperty);
            outModel.removeAll(null, RDF.type, OWL.Class);
            outModel.removeAll(null, RDF.type, OWL.Class);
            ResIterator resIter = outModel.listResourcesWithProperty(RDF.type, outModel.getResource("http://persistence.uni-leipzig.org/nlp2rdf/ontologies/rlog#Entry"));
            while (resIter.hasNext()) {
                Resource res = resIter.next();
                outModel.removeAll(res, null, (RDFNode) null);
            }
            return createSuccessResponse(outModel, nifParameters.getOutformat());

        } catch (BadRequestException e) {
            logger.error("failed", e);
            throw new eu.freme.common.exception.BadRequestException(e.getMessage());
        } catch (eu.freme.common.exception.ExternalServiceFailedException e) {
            logger.error("failed", e);
            throw new ExternalServiceFailedException();
        } catch (Exception e) {
            logger.error("failed", e);
            throw new eu.freme.common.exception.BadRequestException(e.getMessage());
        }
    }
    public String callDBpediaSpotlight(String text, String confidenceParam, String languageParam, String prefix) throws ExternalServiceFailedException, BadRequestException {
        try {
            if(prefix.equals("http://freme-project.eu/")) {
                prefix = "http://freme-project.eu/#";
            }

            if(confidenceParam == null) {
                confidenceParam = "0.3";
            }

            HttpResponse ex = Unirest.post(dbpediaSpotlightUrl + "?f=text&t=direct&confidence=" + confidenceParam + "&prefix=" + URLEncoder.encode(prefix, "UTF-8")).header("Content-Type", "application/x-www-form-urlencoded").body("i=" + URLEncoder.encode(text, "UTF-8")).asString();
            if(ex.getStatus() != HttpStatus.OK.value()) {
                if(ex.getStatus() == HttpStatus.BAD_REQUEST.value()) {
                    throw new BadRequestException((String)ex.getBody());
                } else {
                    throw new ExternalServiceFailedException((String)ex.getBody());
                }
            } else {
                String nif = (String)ex.getBody();
                return nif;
            }
        } catch (UnirestException e) {
            throw new ExternalServiceFailedException(e.getMessage());
        } catch (UnsupportedEncodingException e) {
           throw new BadRequestException("Unsupported Encoding: "+e.getMessage());
        }
    }

    private void validateConfidenceScore(String confidenceParam) {
        if (confidenceParam == null)
            return;
        double confVal = Double.parseDouble(confidenceParam);
        if (confVal >= .00 && confVal <= 1.0) {
            // the conf value is OK.
        } else {
            throw new BadRequestException("The value of the confidence parameter is out of the range [0..1].");
        }
    }
}
