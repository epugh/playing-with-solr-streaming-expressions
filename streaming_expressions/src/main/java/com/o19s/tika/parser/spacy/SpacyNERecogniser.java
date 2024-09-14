package com.o19s.tika.parser.spacy;
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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.tika.parser.ner.NERecogniser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class offers an implementation of {@link NERecogniser} based on spaCy.
 * This NER requires additional setup, due to Http requests to an endpoint
 * server that runs spaCy as a RESTful service:
 * https://github.com/jgontrum/spacy-api-docker See (Someday!)
 * <a href="http://wiki.apache.org/tika/TikaAndspaCy">
 *
 */
public class SpacyNERecogniser implements NERecogniser {

	private static final Logger LOG = LoggerFactory.getLogger(SpacyNERecogniser.class);
	private static boolean available = false;
	private static final String SPACY_REST_HOST = "http://localhost:8080";
	private String restHostUrlStr;
	/**
	 * some common entities identified by NLTK
	 */
	public static final Set<String> ENTITY_TYPES = new HashSet<String>() {
		{
			add("NAMES");
		}
	};

	public SpacyNERecogniser() {
		try {

			String restHostUrlStr = "";
			try {
				restHostUrlStr = readRestUrl();
			} catch (IOException e) {
				LOG.warn("Can't read rest url", e);
			}

			if (restHostUrlStr == null || restHostUrlStr.equals("")) {
				this.restHostUrlStr = SPACY_REST_HOST;
			} else {
				this.restHostUrlStr = restHostUrlStr;
			}

			URI versionUrl = URI.create(restHostUrlStr + "/version");

			String status;
			DefaultHttpClient client = new DefaultHttpClient();
			try {
				HttpResponse response = client.execute(new HttpGet(versionUrl));
				available = response.getStatusLine().getStatusCode() == 200;
				status = response.getStatusLine().toString();
			} catch (java.net.ConnectException ce) {
				available = false;
				status = ce.getMessage();
			}

			LOG.info("Spacy Server at {}, Available = {}, API Status = {}", restHostUrlStr, available, status);

		} catch (Exception e) {
			LOG.warn(e.getMessage(), e);
		}
	}

	private static String readRestUrl() throws IOException {
		Properties spacyProperties = new Properties();
		spacyProperties.load(SpacyNERecogniser.class.getResourceAsStream("SpacyServer.properties"));

		return spacyProperties.getProperty("spacy.server.url");
	}

	/**
	 * @return {@code true} if server endpoint is available. returns {@code false}
	 *         if server endpoint is not available for requests.
	 */
	public boolean isAvailable() {
		return available;
	}

	/**
	 * Gets set of entity types recognised by this recogniser
	 * 
	 * @return set of entity classes/types
	 */
	public Set<String> getEntityTypes() {
		return ENTITY_TYPES;
	}

	/**
	 * recognises names of entities in the text
	 * 
	 * @param text text which possibly contains names
	 * @return map of entity type -&gt; set of names
	 */
	public Map<String, Set<String>> recognise(String text) {
		Map<String, Set<String>> entities = new HashMap<>();
		text = text.replace("\n", " ").replace("\r", " ").replace("\"", ""); // Spacy API doesn't like CRLF
		text = new String(text.getBytes(), StandardCharsets.US_ASCII); // Remove any é characters.
		// text = "Pastafarians are smarter than people with Coca Cola bottles";
		try {
			String url = restHostUrlStr + "/ent";

			DefaultHttpClient client = new DefaultHttpClient();

			HttpPost httpPost = new HttpPost(url);

			String json = "{\"text\":\"" + text + "\",\"model\":\"en\"}";
			StringEntity entity = new StringEntity(json);
			httpPost.setEntity(entity);
			httpPost.setHeader("Accept", "application/json");
			httpPost.setHeader("Content-type", "application/json");

			CloseableHttpResponse response = client.execute(httpPost);

			int responseCode = response.getStatusLine().getStatusCode();
			// String result = EntityUtils.toString(response.getEntity());
			if (responseCode == 200) {
				String result = EntityUtils.toString(response.getEntity());
				// System.out.println(result);
				JSONParser parser = new JSONParser();
				JSONArray j = (JSONArray) parser.parse(result);
				Iterator<?> keys = j.iterator();
				while (keys.hasNext()) {
					JSONObject key = (JSONObject) keys.next();
					String type = ((String) key.get("type")).toUpperCase(Locale.ENGLISH);
					ENTITY_TYPES.add(type);
					if (!entities.containsKey(type)) {
						entities.put(type, new HashSet<String>());
					}
					entities.get(type).add((String) key.get("text"));

				}
			}
		} catch (Exception e) {
			LOG.debug(e.getMessage(), e);
		}

		return entities;
	}

}
