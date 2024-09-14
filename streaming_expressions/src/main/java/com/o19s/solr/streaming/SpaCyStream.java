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
package com.o19s.solr.streaming;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.comp.StreamComparator;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.expr.Explanation;
import org.apache.solr.client.solrj.io.stream.expr.Expressible;
import org.apache.solr.client.solrj.io.stream.expr.StreamExplanation;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpression;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionNamedParameter;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionParameter;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionValue;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;
import org.apache.solr.common.util.Utils;
import org.json.simple.parser.ParseException;

/**
 * The spacy expression performs named entity extraction on documents using
 * spaCy from a stream Syntax: spacy(spacyUrl="http://someurl", fl="body",anyStream(...))
 **/

public class SpaCyStream extends TupleStream implements Expressible {

	private TupleStream stream;
	private StreamContext streamContext;

	private String spacyUrl;
	private String fl;
	private String model;
	private boolean details;
	private CloseableHttpClient client;

	public SpaCyStream(StreamExpression expression, StreamFactory factory) throws IOException {
		// grab all parameters out
		List<StreamExpression> streamExpressions = factory.getExpressionOperandsRepresentingTypes(expression,
				Expressible.class, TupleStream.class);
		StreamExpressionNamedParameter spacyUrlParam = factory.getNamedOperand(expression, "spacyUrl");
		StreamExpressionNamedParameter flParam = factory.getNamedOperand(expression, "fl");
		StreamExpressionNamedParameter modelParam = factory.getNamedOperand(expression, "model");
		StreamExpressionNamedParameter detailsParam = factory.getNamedOperand(expression, "details");

		spacyUrl = null;
		fl = null;
		model = "en";
		details = false;

		if (1 != streamExpressions.size()) {
			throw new IOException(
					String.format(Locale.ROOT, "Invalid expression %s - expecting a single stream but found %d",
							expression, streamExpressions.size()));
		}
		if (spacyUrlParam == null) {
			throw new IOException("spacyUrl parameter cannot be null for the spacy expression");
		} else {
			spacyUrl = ((StreamExpressionValue) spacyUrlParam.getParameter()).getValue();
		}
		if (flParam == null) {
			throw new IOException("fl parameter cannot be null for the spacy expression");
		} else {
			fl = ((StreamExpressionValue) flParam.getParameter()).getValue();
		}
		if (modelParam != null) {
			model = ((StreamExpressionValue) modelParam.getParameter()).getValue();
		}
		if (detailsParam != null) {
			details = Boolean.parseBoolean(((StreamExpressionValue) detailsParam.getParameter()).getValue());
		}

		TupleStream stream = factory.constructStream(streamExpressions.get(0));

		init(spacyUrl, stream, fl, model);
	}

	private void init(String spacyUrl, TupleStream tupleStream, String fl, String model) {
		this.spacyUrl = spacyUrl;
		this.stream = tupleStream;
		this.fl = fl;
		this.model = model;

	}

	@Override
	public List<TupleStream> children() {
		List<TupleStream> l = new ArrayList<>();
		l.add(stream);

		return l;
	}

	public StreamComparator getStreamSort() {
		return stream.getStreamSort();
	}

	@Override
	public void open() throws IOException {

		URI versionUrl = URI.create(spacyUrl + "/version");

		String status;
		boolean available = false;
		HttpClientBuilder builder = HttpClientBuilder.create();
		client = builder.build();
		try {
			HttpResponse response = client.execute(new HttpGet(versionUrl));
			available = response.getStatusLine().getStatusCode() == 200;
			status = response.getStatusLine().toString();
		} catch (java.net.ConnectException ce) {
			available = false;
			status = ce.getMessage();
		}
		if (!available) {
			throw new IOException(
					String.format(Locale.ROOT, "spaCy server at {} is not available: {}", spacyUrl, status));
		}

		stream.open();
	}

	@Override
	public void close() throws IOException {
		stream.close();
		client.close();
	}

	@Override
	public Tuple read() throws IOException {

		Tuple docTuple = stream.read();
		if (docTuple.EOF) {
			return docTuple;
		}

		String text = docTuple.getString(fl);
		try {
			List<Map<String, String>> results = recognise(text);

			Map dude = new HashMap();

			for (Map result : results) {
				if (!dude.containsKey(result.get("type"))) {
					dude.put(result.get("type"), new HashSet());
				}
				Set forType = (Set) dude.get(result.get("type"));
				forType.add(result.get("text"));

			}

			for (Iterator<?> i = dude.keySet().iterator(); i.hasNext();) {
				String type = (String) i.next();
				docTuple.put("ner_" + type.toLowerCase(), dude.get(type));

			}
			if (details) {
				docTuple.put("ner_details", results.toString());
			}

		} catch (Exception e) {
			System.out.println(e.getMessage());
			throw new IOException(e);
		}

		return docTuple;
	}

	/**
	 * recognises names of entities in the text
	 * 
	 * @param text text which possibly contains names
	 * @return map of entity type -&gt; set of names
	 * @throws IOException
	 * @throws ClientProtocolException
	 */
	public List<Map<String, String>> recognise(String text)
			throws ClientProtocolException, IOException {
		List<Map<String, String>> entities = new ArrayList<>();

		text = text.replace("\n", " ").replace("\r", " ").replace("\"", ""); // Spacy API doesn't like CRLF
		text = new String(text.getBytes(), StandardCharsets.US_ASCII); // Remove any é characters.
		// text = "Pastafarians are smarter than people with Coca Cola bottles";
		String url = spacyUrl + "/ent";

		HttpPost httpPost = new HttpPost(url);

		String json = "{\"text\":\"" + text + "\",\"model\":\"en\"}";
		StringEntity entity = new StringEntity(json);
		httpPost.setEntity(entity);
		httpPost.setHeader("Accept", "application/json");
		httpPost.setHeader("Content-type", "application/json");

		System.out.println(json);

		CloseableHttpResponse response = client.execute(httpPost);

		int responseCode = response.getStatusLine().getStatusCode();

		if (responseCode == 200) {
			String result = EntityUtils.toString(response.getEntity());
			System.out.println(result);
			entities = (List<Map<String, String>>) Utils.fromJSON(result.getBytes(UTF_8));

		}

		return entities;
	}

	@Override
	public StreamExpressionParameter toExpression(StreamFactory factory) throws IOException {
		return toExpression(factory, true);
	}

	private StreamExpression toExpression(StreamFactory factory, boolean includeStreams) throws IOException {
		// function name
		StreamExpression expression = new StreamExpression(factory.getFunctionName(this.getClass()));

		expression.addParameter(new StreamExpressionNamedParameter("spacyUrl", spacyUrl));
		expression.addParameter(new StreamExpressionNamedParameter("fl", fl));
		expression.addParameter(new StreamExpressionNamedParameter("model", model));

		// stream
		if (includeStreams) {
			if (stream instanceof Expressible) {
				expression.addParameter(((Expressible) stream).toExpression(factory));
			} else {
				throw new IOException(
						"The SpaCyStream contains a non-expressible TupleStream - it cannot be converted to an expression");
			}
		}

		return expression;
	}

	@Override
	public Explanation toExplanation(StreamFactory factory) throws IOException {
		StreamExplanation explanation = new StreamExplanation(getStreamNodeId().toString());

		explanation.setFunctionName(factory.getFunctionName(this.getClass()));
		explanation.setImplementingClass(this.getClass().getName());
		explanation.setExpressionType(Explanation.ExpressionType.STREAM_DECORATOR);
		explanation.setExpression(toExpression(factory, false).toString());

		return explanation;
	}

	public void setStreamContext(StreamContext streamContext) {
		this.streamContext = streamContext;
		this.stream.setStreamContext(streamContext);
	}
}
