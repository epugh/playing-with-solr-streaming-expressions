Look at https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/logs.adoc



# Setting up

```
docker-compose up --build

docker exec solr1 solr create_collection -c real_estate -p 8983 -shards 3

wget https://raw.githubusercontent.com/searchintuition/query_log_insights_presentation/master/data/real_estate_queries.tsv

docker cp real_estate_queries.tsv solr3:/var/solr/data/userfiles/

```

# Load the tsv formatted data

```
cat('real_estate_queries.tsv',  maxLines=500)
```

```
commit(real_estate,
  update(real_estate,
    parseTSV(
      cat('real_estate_queries.tsv')
    )
  )
)
```

The UI may not return due to it taking a while, so do with a smaller number of lines to see it.  However, you should have 49863 rows!

```
random(real_estate)
```

```
stats(real_estate, count(*), min(timestamp), max(timestamp))
```

Lets look at what states we have!  Fortunantly we have `state_str`

```
facet(real_estate, buckets="state_str", rows="20")
```
Indiana has the most results!

What about queries?

Head:
```
facet(real_estate, buckets="query_str", rows="20")
```

Body ?:
```
facet(real_estate, buckets="query_str", rows="1000")
```

Tail:
```
top(
  n=500,
  facet(real_estate, buckets="query_str", rows="-1"),
  sort="count(*) asc"
)
```
or look a them all via
```
sort(
  facet(real_estate, buckets="query_str", rows="5000"),
  by="count(*) asc"
)
```

So who gets the most clicks?
```
facet(real_estate, buckets="document", rows="20")
```

When did we get our clicks?

```
timeseries(
  real_estate,
  q="*:*",
  field="timestamp",
  start="2006-03-01T00:01:13Z",
  end="2006-05-31T23:06:05Z",
  gap="+10MINUTES",
  format="HH:mm:ss",
  count("*")
)
```

Which position gets clicked?

sum(col), avg(col), min(col), max(col) and count(

```
stats(real_estate, q="*:*", avg(position), min(position), max(position), count(position))
```

Surprised average position is so deep!
```
"count(*)": 49863,
"max(position)": 500,
"min(position)": 1,
"avg(position)": 6.53841976103311
```

We should get a nice Scatter Plot out of:
```
random(real_estate, fl="x, position")
```
https://github.com/apache/lucene-solr/blob/visual-guide/solr/solr-ref-guide/src/logs.adoc#qtime-scatter-plot

We should then look at the various other charts, where we could look at precentats of going deeper than 1, deeper than 5, deepr then 10 etc...!  Should get the classic curve.  (Pareto???)


Now, looking https://github.com/searchintuition/query_log_insights_presentation/blob/master/2_Related_Queries.ipynb

Transform to fit the recipe

```
facet(real_estate, buckets="query_str", rows="-1")
```
```
top(
  n=500,
  facet(real_estate, buckets="query_str, document_str", rows="-1"),
  sort="query_str asc"
)
```

Lets create a "data frame"

```
docker exec solr1 solr create_collection -c query_pairs_df -p 8983 -shards 3
```

hint?  Need to drop a "data frame" ???
```
docker exec solr1 solr delete -c query_pairs_df
```

```
commit(query_pairs_df,
  update(query_pairs_df,
    select(
      facet(real_estate, buckets="query_str, document_str", rows="5000"),
      query_str as query,
      document_str as document,
      count(*) as count
    )
  )
)
```

Now, let's look at our distribution!

```
search(query_pairs_df, q="query_str:"real estate"", sort="query_str desc, count desc")
```
