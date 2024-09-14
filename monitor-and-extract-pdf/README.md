## Demo One: bin/solr post and doing it by hand.

This is more or less based on our integration testing that we run in Solr project: https://github.com/apache/solr/blob/main/solr/packaging/test/test_extraction.bats

See https://solr.apache.org/guide/solr/latest/indexing-guide/post-tool.html for more info.

Download Solr 9.7 and start it from whatever you checked it out.

Start Solr Cloud WITH the Extraction module enabled:

```
bin/solr start -c -Dsolr.modules=extraction
```

Now create your collection "pdf-extracted-data" using the default schema that ships with Solr:

```
bin/solr create -c pdf-extracted-data -d _default
```

Now comes some magic, you need to add to the pdf-extracted-data collection a NEW request handler 
that enables extraction end point.  It's no longer there by default.  Fortunantly it's just a slightly complex API call:

```
curl -X POST -H 'Content-type:application/json' -d '{
    "add-requesthandler": {
      "name": "/update/extract",
      "class": "solr.extraction.ExtractingRequestHandler",
      "defaults":{ "lowernames": "true", "captureAttr":"true"}
    }
  }' "http://localhost:8983/solr/pdf-extracted-data/config"
```

Okay, we are now all set.   Let's practice indexing a SINGLE document, just to make sure it's working.
We're going to use `curl` and index a 10 MB file, just to verify Solr doesn't keel over.
You need to run this command from a terminal that is in the SAME directory as this README for the paths to work.
You may also need to futz with the slashes/backslashes for Windows versus Linux.

BEGIN NOT WORKING - requires disabling Java Security Manager and local filesystem permissions.
We are going to set the doc id to "doc1" via the `literal.id` parameter.

```
 curl "http://localhost:8983/solr/pdf-extracted-data/update/extract?literal.id=doc1&commit=true" -F "myfile=@./files/bcreg20090507a1.pdf"
 curl "http://localhost:8983/solr/pdf-extracted-data/update?commit=true"
```
END NOT WORKING

Now let's try it with our Solr tool, from where you downloaded it, using the right path.
Here is where my files are:

```
export FILES_DIR=/Users/epugh/Documents/projects/playing-with-solr-streaming-expressions/monitor-and-extract-pdf/files
```

```
bin/solr post --filetypes pdf -url http://localhost:8983/solr/pdf-extracted-data/update ${FILES_DIR}/bcreg20090507a1.pdf
```

Go query for the data in Solr!  You should have one doc.

Want to get rid of it?  The id is the file path.

```
bin/solr post -c pdf-extracted-data --mode args "{delete: {id:'/Users/epugh/Documents/projects/playing-with-solr-streaming-expressions/monitor-and-extract-pdf/files/bcreg20090507a1.pdf'}}"
```

Now we are ready to handle more files.  We are going to take the approach popularized by Ripley in the movie Aliens:

https://i.imgur.com/cAFOwQ3.gif

First we delete all the documents:

```
bin/solr post -c pdf-extracted-data --mode args "{commit: {}}"
```

And then we index all the documents that end in `.pdf`:

```
bin/solr --filetypes pdf -url http://localhost:8983/solr/pdf-extracted-data/update ${FILES_DIR}
```
