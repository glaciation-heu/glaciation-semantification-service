/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dell.glaciation.processors.jsonld;

import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.annotation.behavior.ReadsAttribute;
import org.apache.nifi.annotation.behavior.ReadsAttributes;
import org.apache.nifi.annotation.behavior.WritesAttribute;
import org.apache.nifi.annotation.behavior.WritesAttributes;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processor.io.OutputStreamCallback;

import java.util.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.http.auth.*;


@Tags({"semantification"})
@CapabilityDescription("Semantification and write to the Jena Fuseki")
@SeeAlso({})
@ReadsAttributes({@ReadsAttribute(attribute="", description="")})
@WritesAttributes({@WritesAttribute(attribute="", description="")})
public class Semantification extends AbstractProcessor {

    public static final PropertyDescriptor DESTINATION = new PropertyDescriptor
            .Builder().name("DESTINATION")
            .displayName("SPARQL endpoint destination")
            .description("URL of the SPARQL endpoint destination with graph, e.g., ds")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final PropertyDescriptor GSP_ENDPOINT = new PropertyDescriptor
            .Builder().name("GSP_ENDPOINT")
            .displayName("GSP endpoint")
            .description("GSP endpoint of the SPARQL destination")
            .required(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("Success")
            .description("Example relationship")
            .build();

    public static final Relationship FAILURE = new Relationship.Builder()
            .name("Failure")
            .description("Example relationship")
            .build();

    private List<PropertyDescriptor> descriptors;

    private Set<Relationship> relationships;

    @Override
    protected void init(final ProcessorInitializationContext context) {
        descriptors = new ArrayList<>();
        descriptors.add(DESTINATION);
        descriptors.add(GSP_ENDPOINT);
        descriptors = Collections.unmodifiableList(descriptors);

        relationships = new HashSet<>();
        relationships.add(SUCCESS);
        relationships.add(FAILURE);
        relationships = Collections.unmodifiableSet(relationships);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return this.relationships;
    }

    @Override
    public final List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return descriptors;
    }

    @OnScheduled
    public void onScheduled(final ProcessContext context) {

    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }
        // Read JSON
        String content = getContent(flowFile, session);
        String timestamp = JsonPath.read(content, "$.timestamp");
        String resolution = JsonPath.read(content, "$.frame_resolution");
        String robotId = JsonPath.read(content, "$.robot_id");
        String cameraId = JsonPath.read(content, "$.camera_id");
        List<Map<String, Object>> detections = JsonPath.read(content, "$.detections");

        // Semantify fields
        Model model = ModelFactory.createDefaultModel();
        String PREFIXGLC = "https://glaciation-project.eu/reference_model#";
        String PREFIXSAREF = "https://saref.etsi.org/core/";
        
        Property hasTimestamp = model.createProperty(PREFIXSAREF+"hasTimestamp");
        Property fileLocation = model.createProperty(PREFIXGLC+"fileLocation");
        Property hasDetection = model.createProperty(PREFIXGLC+"hasDetection");
        Property hasBBox = model.createProperty(PREFIXGLC+"hasBBox");
        Property hasResolution = model.createProperty(PREFIXGLC+"hasResolution");
        Property hasLabel = model.createProperty(PREFIXGLC+"hasLabel");
        Property hasConfidence = model.createProperty(PREFIXGLC+"hasConfidence");
        Property hasX = model.createProperty(PREFIXGLC+"hasX");
        Property hasY = model.createProperty(PREFIXGLC+"hasY");
        Property hasWidth = model.createProperty(PREFIXGLC+"hasWidth");
        Property hasHeight = model.createProperty(PREFIXGLC+"hasHeight");
        Property makesMeasurement = model.createProperty(PREFIXSAREF+"makesmeasurement");
        Property consistsOf = model.createProperty(PREFIXSAREF+"consistsOf");

        String frame = PREFIXGLC + flowFile.getAttribute("filename");
        Resource frameResource = model.createResource(frame);
        Resource yoloResultResource = model.createResource(PREFIXGLC + "YOLOResult");
        Resource detectionResource = model.createResource(PREFIXGLC + "Detection");
        Resource bboxResource = model.createResource(PREFIXGLC + "BBox");
        Resource robotResource = model.createResource(PREFIXGLC + robotId);
        Resource cameraResource = model.createResource(PREFIXGLC + cameraId);

        frameResource.addProperty(hasTimestamp, timestamp);
        frameResource.addProperty(fileLocation, flowFile.getAttribute("directory"));
        frameResource.addProperty(RDF.type, yoloResultResource);
        robotResource.addProperty(makesMeasurement, frameResource);
        robotResource.addProperty(consistsOf, cameraResource);
        
        for (Map<String, Object> detection: detections) {
            //Resource detectionResource = model.createResource();
            frameResource.addProperty(hasDetection, model.createResource()
                .addProperty(hasLabel, String.valueOf(detection.get("label")))
                .addProperty(hasConfidence, String.valueOf(detection.get("confidence")))
                .addProperty(hasBBox, model.createResource()
                    .addProperty(hasX, String.valueOf(((Map)detection.get("bounding_box")).get("x")))
                    .addProperty(hasY, String.valueOf(((Map)detection.get("bounding_box")).get("y")))
                    .addProperty(hasWidth, String.valueOf(((Map)detection.get("bounding_box")).get("width")))
                    .addProperty(hasHeight, String.valueOf(((Map)detection.get("bounding_box")).get("height")))
                ));
        }
        
        // Authentivation
        //AuthEnv.get().registerUsernamePassword(URI.create(context.getProperty(DESTINATION).getValue()+"data"), "glaciationnifi", "glaciationnifi");
        Authenticator authenticator = AuthLib.authenticator("admin", "password");
        HttpClient httpClient = HttpClient.newBuilder()
            .authenticator(authenticator)
            .build();
        // Write to Jena
        RDFConnection connection = RDFConnectionFuseki.create()
                //.destination("http://localhost:3030/ds/")
                //.queryEndpoint("query")
                //.updateEndpoint("update")
                //.gspEndpoint("data")
                .destination(context.getProperty(DESTINATION).getValue())
                .gspEndpoint(context.getProperty(GSP_ENDPOINT).getValue())
                .acceptHeaderSelectQuery("application/sparql-results+json, application/sparql-results+xml;q=0.9")
                .httpClient(httpClient)
                .build();
        
        // Get String version of the model with JSON-LD format
        String syntax = "JSON-LD";
        StringWriter out = new StringWriter();
        model.write(out, syntax);
        String modelString = out.toString();
	    
	    try ( RDFConnection conn = connection ) {
            conn.load(model);
            // change flowFile with model content
            flowFile = session.write(flowFile, new OutputStreamCallback() {
                @Override
                public void process(OutputStream out) throws IOException {
                    out.write(modelString.getBytes());
                }
            });
        }
        
        // Transfer flow as it was
        session.transfer(flowFile, SUCCESS);
    }

    private String getContent(FlowFile flowFile, ProcessSession session) {
        final var byteArrayOutputStream = new ByteArrayOutputStream();
        session.exportTo(flowFile, byteArrayOutputStream);
        return byteArrayOutputStream.toString();    

    }
}
