package com.o19s.solr.streaming;

import java.io.FileOutputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
import java.io.OutputStream;
import java.util.Map.Entry;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient.Builder;
import org.apache.solr.client.solrj.io.SolrClientCache;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.comp.StreamComparator;
import org.apache.solr.client.solrj.io.stream.PushBackStream;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.expr.Explanation;
import org.apache.solr.client.solrj.io.stream.expr.Explanation.ExpressionType;
import org.apache.solr.client.solrj.io.stream.expr.Expressible;
import org.apache.solr.client.solrj.io.stream.expr.StreamExplanation;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpression;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionNamedParameter;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionValue;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.core.SolrResourceLoader;
import org.noggit.CharArr;
import org.noggit.JSONWriter;
import org.apache.solr.common.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends tuples emitted by a wrapped {@link TupleStream} to disk in the
 * userfiles.
 * 
 * @since 6.0.0
 */
public class DogStream extends TupleStream implements Expressible {
	private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static String BATCH_SAVED_FIELD_NAME = "batchSaved"; // field name in summary tuple for #docs updated in
																// batch
	// private String collection;
	private String filepath;
	// private String zkHost;
	private int updateBatchSize;
	/**
	 * Indicates if the {@link CommonParams#VERSION_FIELD} should be removed from
	 * tuples when converting to JSON docs. May be set per expression using the
	 * <code>"pruneVersionField"</code> named operand, defaults to the value
	 * returned by {@link #defaultPruneVersionField()}
	 */
	private boolean pruneVersionField;
	private int batchNumber;
	private long totalDocsIndex;
	private PushBackStream tupleSource;
	// private transient SolrClientCache cache;
	// private transient CloudSolrClient cloudSolrClient;
	private List<SolrInputDocument> documentBatch = new ArrayList<>();
	private String coreName; // if we ever shard the writing process out to multiple workers???

	// Solr 9
	private Path chroot;
	// Solr 8
	// private String chroot;

	private CharArr charArr = new CharArr(1024 * 2);
	JSONWriter jsonWriter = new JSONWriter(charArr, -1);
	private Writer writer;
	private OutputStream fos;
	int bufferSize = 1024 * 1024;

	public DogStream(StreamExpression expression, StreamFactory factory) throws IOException {
		// String collectionName = factory.getValueOperand(expression, 0);
		// verifyCollectionName(collectionName, expression);
		String filepathName = factory.getValueOperand(expression, 0);

		if (filepathName == null) {
			throw new IllegalArgumentException("No filepaths provided to stream");
		}
		final String filepathWithoutSurroundingQuotes = stripSurroundingQuotesIfTheyExist(filepathName);
		if (StringUtils.isEmpty(filepathWithoutSurroundingQuotes)) {
			throw new IllegalArgumentException("No filepaths provided to stream");
		}

		this.filepath = filepathWithoutSurroundingQuotes;

		// String zkHost = findZkHost(factory, collectionName, expression);
		// verifyZkHost(zkHost, collectionName, expression);

		int updateBatchSize = extractBatchSize(expression, factory);
		pruneVersionField = factory.getBooleanOperand(expression, "pruneVersionField", defaultPruneVersionField());

		// Extract underlying TupleStream.
		List<StreamExpression> streamExpressions = factory.getExpressionOperandsRepresentingTypes(expression,
				Expressible.class, TupleStream.class);
		if (1 != streamExpressions.size()) {
			throw new IOException(
					String.format(Locale.ROOT, "Invalid expression %s - expecting a single stream but found %d",
							expression, streamExpressions.size()));
		}
		StreamExpression sourceStreamExpression = streamExpressions.get(0);
		init(filepath, factory.constructStream(sourceStreamExpression), updateBatchSize);
	}

	public DogStream(String filepath, TupleStream tupleSource, int updateBatchSize) throws IOException {
		if (updateBatchSize <= 0) {
			throw new IOException(
					String.format(Locale.ROOT, "batchSize '%d' must be greater than 0.", updateBatchSize));
		}
		pruneVersionField = defaultPruneVersionField();
		init(filepath, tupleSource, updateBatchSize);
	}

	private String stripSurroundingQuotesIfTheyExist(String value) {
		if (value.length() < 2)
			return value;
		if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
			return value.substring(1, value.length() - 1);
		}

		return value;
	}

	private void init(String filepathName, TupleStream tupleSource, int updateBatchSize) {
		this.filepath = filepathName;
		this.updateBatchSize = updateBatchSize;
		this.tupleSource = new PushBackStream(tupleSource);
	}

	@Override
	public void open() throws IOException {
//    setCloudSolrClient();
		setupFileWriter();
		tupleSource.open();
	}

	private void setupFileWriter() {

		Path fileToWrite = chroot.resolve(filepath).normalize();
		if (!fileToWrite.startsWith(chroot)) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
					"file/directory to stream must be under " + chroot);
		}

		// if ( Files.exists(fileToWrite)) {
		// throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
		// "file/directory to stream doesn't exist: " + crawlRootStr);
		// }

		try {
			fos = new FileOutputStream(fileToWrite.toFile());
		} catch (FileNotFoundException e) {
			throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Couldn't write to file " + fileToWrite);
		}
		if (fileToWrite.endsWith(".gz"))
			try {
				fos = new GZIPOutputStream(fos);
			} catch (IOException e) {
				throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
						"IOException writing GZip to file " + fileToWrite);
			}
		if (bufferSize > 0) {
			fos = new BufferedOutputStream(fos, bufferSize);
		}
		writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);

	}

	@Override
	public Tuple read() throws IOException {

		for (int i = 0; i < updateBatchSize; i++) {
			Tuple tuple = tupleSource.read();
			if (tuple.EOF) {
				if (documentBatch.isEmpty()) {
					return tuple;
				} else {
					tupleSource.pushBack(tuple);
					uploadBatchToCollection(documentBatch); // We could refactor UpdateStream, DeleteStream, and
															// DogStream to share this logic.
					int b = documentBatch.size();
					documentBatch.clear();
					return createBatchSummaryTuple(b);
				}
			}
			documentBatch.add(convertTupleToSolrInputDocument(tuple));
		}

		uploadBatchToCollection(documentBatch);
		int b = documentBatch.size();
		documentBatch.clear();
		return createBatchSummaryTuple(b);
	}

	@Override
	public void close() throws IOException {
		tupleSource.close();
		closeFileWriter();
	}

	private void closeFileWriter() throws IOException {
		writer.flush();
		fos.flush();
		fos.close();

	}

	@Override
	public StreamComparator getStreamSort() {
		return tupleSource.getStreamSort();
	}

	@Override
	public List<TupleStream> children() {
		ArrayList<TupleStream> sourceList = new ArrayList<TupleStream>(1);
		sourceList.add(tupleSource);
		return sourceList;
	}

	@Override
	public StreamExpression toExpression(StreamFactory factory) throws IOException {
		return toExpression(factory, true);
	}

	private StreamExpression toExpression(StreamFactory factory, boolean includeStreams) throws IOException {
		StreamExpression expression = new StreamExpression(factory.getFunctionName(this.getClass()));
		expression.addParameter(filepath);
		expression.addParameter(new StreamExpressionNamedParameter("batchSize", Integer.toString(updateBatchSize)));

		if (includeStreams) {
			if (tupleSource instanceof Expressible) {
				expression.addParameter(((Expressible) tupleSource).toExpression(factory));
			} else {
				throw new IOException(
						"This ParallelStream contains a non-expressible TupleStream - it cannot be converted to an expression");
			}
		} else {
			expression.addParameter("<stream>");
		}

		return expression;
	}

	@Override
	public Explanation toExplanation(StreamFactory factory) throws IOException {

		// An update stream is backward wrt the order in the explanation. This stream is
		// the "child"
		// while the collection we're updating is the parent.

		StreamExplanation explanation = new StreamExplanation(getStreamNodeId() + "-datastore");

		explanation.setFunctionName(String.format(Locale.ROOT, "dog (%s)", filepath));
		explanation.setImplementingClass("Solr/Lucene");
		explanation.setExpressionType(ExpressionType.DATASTORE);
		explanation.setExpression("Save into " + filepath);

		// child is a datastore so add it at this point
		StreamExplanation child = new StreamExplanation(getStreamNodeId().toString());
		child.setFunctionName(String.format(Locale.ROOT, factory.getFunctionName(getClass())));
		child.setImplementingClass(getClass().getName());
		child.setExpressionType(ExpressionType.STREAM_DECORATOR);
		child.setExpression(toExpression(factory, false).toString());
		child.addChild(tupleSource.toExplanation(factory));

		explanation.addChild(child);

		return explanation;
	}

	@Override
	public void setStreamContext(StreamContext context) {
		this.tupleSource.setStreamContext(context);
		this.coreName = (String) context.get("core");
		final SolrCore core = (SolrCore) context.get("solr-core");

		// Solr 9
		// this.chroot = core.getCoreContainer().getUserFilesPath();
		// Solr 8
		this.chroot = Paths.get(core.getCoreContainer().getSolrHome(), SolrResourceLoader.USER_FILES_DIRECTORY);

		if (!Files.exists(chroot)) {
			throw new IllegalStateException(
					chroot + " directory used to load files must exist but could not be found!");
		}
		this.tupleSource.setStreamContext(context);
	}

	private int extractBatchSize(StreamExpression expression, StreamFactory factory) throws IOException {
		StreamExpressionNamedParameter batchSizeParam = factory.getNamedOperand(expression, "batchSize");
		if (batchSizeParam == null) {
			// Sensible default batch size
			return 250;
		}
		String batchSizeStr = ((StreamExpressionValue) batchSizeParam.getParameter()).getValue();
		return parseBatchSize(batchSizeStr, expression);
	}

	private int parseBatchSize(String batchSizeStr, StreamExpression expression) throws IOException {
		try {
			int batchSize = Integer.parseInt(batchSizeStr);
			if (batchSize <= 0) {
				throw new IOException(String.format(Locale.ROOT,
						"invalid expression %s - batchSize '%d' must be greater than 0.", expression, batchSize));
			}
			return batchSize;
		} catch (NumberFormatException e) {
			throw new IOException(String.format(Locale.ROOT,
					"invalid expression %s - batchSize '%s' is not a valid integer.", expression, batchSizeStr));
		}
	}

	/**
	 * Used during initialization to specify the default value for the
	 * <code>"pruneVersionField"</code> option. {@link DogStream} returns
	 * <code>true</code> for backcompat and to simplify slurping of data from one
	 * collection to another.
	 */
	protected boolean defaultPruneVersionField() {
		return true;
	}

	SolrInputDocument convertTupleToSolrInputDocument(Tuple tuple) {
		SolrInputDocument doc = new SolrInputDocument();
		// Solr 8 version of tuple handling
		for (Object field : tuple.fields.keySet()) {

			if (!(((String)field).equals(CommonParams.VERSION_FIELD) && pruneVersionField)) {
				Object value = tuple.get(field);
				if (value instanceof List) {
					addMultivaluedField(doc, (String)field, (List<?>) value);
				} else {
					doc.addField((String)field, value);
				}
			}
		}
		log.debug("Tuple [{}] was converted into SolrInputDocument [{}].", tuple, doc);

		return doc;
	}

	private void addMultivaluedField(SolrInputDocument doc, String fieldName, List<?> values) {
		for (Object value : values) {
			doc.addField(fieldName, value);
		}
	}

	/**
	 * This method will be called on every batch of tuples consumed, after
	 * converting each tuple in that batch to a JSON Document.
	 */
	protected void uploadBatchToCollection(List<SolrInputDocument> documentBatch) throws IOException {
		if (documentBatch.size() == 0) {
			return;
		}

		try {
			for (SolrInputDocument sid : documentBatch) {
				SolrDocument sd = convertSolrInputDocumentToSolrDocument(sid);
				accept(sd);
			}
		} catch (IOException e) {
			// TODO: it would be nice if there was an option to "skipFailedBatches"
			// TODO: and just record the batch failure info in the summary tuple for that
			// batch and continue
			//
			// TODO: The summary batches (and/or stream error) should also pay attention to
			// the error metadata
			// from the SolrServerException ... and ideally also any TolerantUpdateProcessor
			// metadata

			log.warn("Unable to add documents to collection due to unexpected error.", e);
			String className = e.getClass().getName();
			String message = e.getMessage();
			throw new IOException(String.format(Locale.ROOT,
					"Unexpected error when writing documents to file %s- %s:%s", filepath, className, message));
		}
	}

	private Tuple createBatchSummaryTuple(int batchSize) {
		assert batchSize > 0;
		Tuple tuple = new Tuple();
		this.totalDocsIndex += batchSize;
		++batchNumber;
		tuple.put(BATCH_SAVED_FIELD_NAME, batchSize);
		tuple.put("totalSaved", this.totalDocsIndex);
		tuple.put("batchNumber", batchNumber);
		if (coreName != null) {
			tuple.put("worker", coreName);
		}
		return tuple;
	}

	// Copied from the ExportTool
	public synchronized void accept(SolrDocument doc) throws IOException {
		charArr.reset();
		Map<String, Object> m = new LinkedHashMap<>(doc.size());
		doc.forEach((s, field) -> {
			// if (s.equals("_version_") || s.equals("_roor_")) return;
			if (field instanceof List) {
				if (((List<?>) field).size() == 1) {
					field = ((List<?>) field).get(0);
				}
			}
			field = constructDateStr(field);
			if (field instanceof List) {
				List<?> list = (List<?>) field;
				if (hasdate(list)) {
					ArrayList<Object> listCopy = new ArrayList<>(list.size());
					for (Object o : list)
						listCopy.add(constructDateStr(o));
					field = listCopy;
				}
			}
			m.put(s, field);
		});
		jsonWriter.write(m);
		writer.write(charArr.getArray(), charArr.getStart(), charArr.getEnd());
		writer.append('\n');
		// super.accept(doc);
	}

	private boolean hasdate(List<?> list) {
		boolean hasDate = false;
		for (Object o : list) {
			if (o instanceof Date) {
				hasDate = true;
				break;
			}
		}
		return hasDate;
	}

	private Object constructDateStr(Object field) {
		if (field instanceof Date) {
			field = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(((Date) field).getTime()));
		}
		return field;
	}

	public SolrDocument convertSolrInputDocumentToSolrDocument(SolrInputDocument sid) {
		SolrDocument doc = new SolrDocument();

		for (Entry<String, SolrInputField> entry : sid.entrySet()) {
			doc.addField(entry.getKey(), entry.getValue().getValue());
		}
		return doc;
	}

}
