package com.o19s.solr.streaming;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.client.solrj.io.SolrClientCache;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.stream.CloudSolrStream;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.UpdateStream;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpression;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpressionParser;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.junit.Test;

public class DogStreamTest {
	/**
	 * Look at testParallelTerminatingDaemonUpdateStream in StreamDecoratorTest for good example
	 * @throws IOException when something goes wrong
	 */

	@Test
	public void testAccept() throws IOException {
		
		DogStream dogStream = new DogStream(null, null, 1);
		
		SolrInputDocument sid = new SolrInputDocument();
		sid.addField("id", 1);
		sid.addField("title", "Dune");
		sid.addField("author","Frank Herbert");
		sid.addField("keywords", "scifi");
		sid.addField("keywords", "epic");
		
		System.out.println(sid);
		
		SolrDocument sd = dogStream.convertSolrInputDocumentToSolrDocument(sid);
		
		System.out.println(sd);
		
		assertEquals(sid.getFieldValue("id"), sd.getFieldValue("id"));
		assertEquals(sid.getFieldValue("title"), sd.getFieldValue("title"));
		assertEquals(sid.getField("keywords").getValue(), sd.getFieldValue("keywords"));
		
	}
	
	@Test
	public void testTupleConvert() throws Exception {
		DogStream dogStream = new DogStream(null, null, 1);
		
		Tuple t = new Tuple();
		t.put("id", 1);
		t.put("title", "Dune");
		t.put("author","Frank Herbert");
		
		ArrayList<String> keywords = new ArrayList<String>(); // Create an ArrayList object
		keywords.add("scifi");
		keywords.add("epic");
		t.put("keywords", keywords);
		
		System.out.println(t.jsonStr());
		
		SolrInputDocument sid = dogStream.convertTupleToSolrInputDocument(t);
		
		System.out.println(sid);
		
		assertEquals(sid.getFieldValue("id"), t.getString("id"));
		assertEquals(sid.getFieldValue("title"), t.get("title"));
		Object o = t.get("keywords");
		assertEquals(2, t.getStrings("keywords").size());
		Collection<Object> objs = sid.getField("keywords").getValues();
		//String first = objs.size()
		//String firstActual = t.getStrings("keywords").get(0);
		//assertEquals(first, firstActual);
		//assertEquals(sid.getField("keywords").getValue(), t.get("keywords"));
		
	}
	
	@Test
	public void testWithChorus() throws Exception {
		  StreamExpression expression;
		    TupleStream stream;

		    StreamContext streamContext = new StreamContext();
		    SolrClientCache solrClientCache = new SolrClientCache();
		    streamContext.setSolrClientCache(solrClientCache);

		    StreamFactory factory = new StreamFactory()
		      .withCollectionZkHost("ecommerce", "localhost:2181")
		      .withCollectionZkHost("destinationCollection", "localhost:2181")
		      .withFunctionName("search", CloudSolrStream.class)
		      .withFunctionName("update", UpdateStream.class);
		    
		    
		    expression = StreamExpressionParser.parse("update(destinationCollection, batchSize=5, search(ecommerce, q=*:*, fl=\"id,_version_,ean\", sort=\"ean asc\"))");
		      stream = new UpdateStream(expression, factory);
		      stream.setStreamContext(streamContext);
		      //List<Tuple> tuples = getTuples(stream);
		      
		      //assert (tuples.size() == 1);
		
	}
	
	protected List<Tuple> getTuples(TupleStream tupleStream) throws IOException {
	    List<Tuple> tuples = new ArrayList<Tuple>();

	    try {
	      tupleStream.open();
	      for (Tuple t = tupleStream.read(); !t.EOF; t = tupleStream.read()) {
	        tuples.add(t);
	      }
	    } finally {
	      tupleStream.close();
	    }
	    return tuples;
	  }
	  

}
