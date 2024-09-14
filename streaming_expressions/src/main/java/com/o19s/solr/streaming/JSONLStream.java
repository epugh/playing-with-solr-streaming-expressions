package com.o19s.solr.streaming;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.comp.StreamComparator;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.expr.Explanation;
import org.apache.solr.client.solrj.io.stream.expr.Explanation.ExpressionType;
import org.apache.solr.client.solrj.io.stream.expr.Expressible;
import org.apache.solr.client.solrj.io.stream.expr.StreamExplanation;
import org.apache.solr.client.solrj.io.stream.expr.StreamExpression;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;

import org.noggit.ObjectBuilder;

public class JSONLStream extends TupleStream implements Expressible {

  private static final long serialVersionUID = 1;
  private String currentFile;
  private int lineNumber;

  protected TupleStream originalStream;

  public JSONLStream(StreamExpression expression,StreamFactory factory) throws IOException {
    // grab all parameters out
    List<StreamExpression> streamExpressions = factory.getExpressionOperandsRepresentingTypes(expression, Expressible.class, TupleStream.class);

    // validate expression contains only what we want.
    if(expression.getParameters().size() != streamExpressions.size()){
      throw new IOException(String.format(Locale.ROOT,"Invalid expression %s - unknown operands found", expression));
    }

    if(1 != streamExpressions.size()){
      throw new IOException(String.format(Locale.ROOT,"Invalid expression %s - expecting a single stream but found %d",expression, streamExpressions.size()));
    }

    init(factory.constructStream(streamExpressions.get(0)));
  }

  private void init(TupleStream stream){
    this.originalStream = stream;
  }

  @Override
  public StreamExpression toExpression(StreamFactory factory) throws IOException{
    return toExpression(factory, true);
  }

  private StreamExpression toExpression(StreamFactory factory, boolean includeStreams) throws IOException {
    // function name
    StreamExpression expression = new StreamExpression(factory.getFunctionName(this.getClass()));

    if(includeStreams){
      // streams
      if(originalStream instanceof Expressible){
        expression.addParameter(((Expressible)originalStream).toExpression(factory));
      }
      else{
        throw new IOException("This JSONLStream contains a non-expressible TupleStream - it cannot be converted to an expression");
      }
    }
    else{
      expression.addParameter("<stream>");
    }

    return expression;
  }

  @Override
  public Explanation toExplanation(StreamFactory factory) throws IOException {

    return new StreamExplanation(getStreamNodeId().toString())
        .withChildren(new Explanation[] {
            originalStream.toExplanation(factory)
            // we're not including that this is wrapped with a ReducerStream stream because that's just an implementation detail
        })
        .withFunctionName(factory.getFunctionName(this.getClass()))
        .withImplementingClass(this.getClass().getName())
        .withExpressionType(ExpressionType.STREAM_DECORATOR)
        .withExpression(toExpression(factory, false).toString());
  }

  public void setStreamContext(StreamContext context) {
    this.originalStream.setStreamContext(context);
  }

  public List<TupleStream> children() {
    List<TupleStream> l =  new ArrayList<TupleStream>();
    l.add(originalStream);
    return l;
  }

  public void open() throws IOException {
    originalStream.open();
  }

  public void close() throws IOException {
    originalStream.close();
  }

  public Tuple read() throws IOException {
    Tuple tuple = originalStream.read();
    ++lineNumber;
    if(tuple.EOF) {
      return tuple;
    } else {
      String file = formatFile(tuple.getString("file"));
      String line = tuple.getString("line");

      if (!file.equals(currentFile)) {
          this.currentFile = file;
          this.lineNumber = 1; //New file so reset the lineNumber
      }

      //JSONParser parser = new JSONParser(line);
      //System.out.println("Here is the line:" + line);
      Object o = ObjectBuilder.fromJSON(line);

      Tuple out = new Tuple((Map)o);
      
      // Set the id based on the file and the line number if we don't have one defined already.
      if (out.get("id") == null) {
    	  out.put("id", file+"_"+lineNumber);
      }

      return out;

    }
  }

  private String formatFile(String file) {
    //We don't want the ./ which carries no information but can lead to problems in creating the id for the field.
    if(file.startsWith("./")) {
      return file.substring(2);
    } else {
      return file;
    }
  }


  /** Return the stream sort - ie, the order in which records are returned */
  public StreamComparator getStreamSort(){
    return originalStream.getStreamSort();
  }

  public int getCost() {
    return 0;
  }

}
