curl --user solr:SolrRocks -X POST http://localhost:8983/api/collections -H 'Content-Type: application/json' -d'
  {
    "create": {
      "name": "zipper",
      "config": "_default",
      "numShards": 2,
      "replicationFactor": 1,
      "waitForFinalState": true
    }
  }
'
