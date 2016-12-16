# totsuki

Ingestor for match data streams to store into S3.

Etymology: We "cook" raw matches into Parquet files and prepare them for ETL jobs that read from S3.

## What?

This is a really simple Spark streaming application that does the following:

1. Subscribe to match Kafka topic, e.g. `bacchus:match:na`
2. Parse matches from Kafka topic (inefficient, TODO optimize)
3. Extract version from the message
4. Split stream into one RDD per unique version
5. Convert RDD to DF and write Parquet `$region/$version/$time.parquet` to an S3 bucket.

## Optimization?

The largest optimization would be to somehow send the version separately with the Kafka message, preventing a full deep parse of our very intricate Match message.

Another thing we should do is evaluate whether Parquet is the best method for storage. We should carefully monitor resource usage and see if it is reasonable. Avro is supposedly better for sequential reads, but I am using Parquet here because it is built-in to Spark, and I don't want to bother with Avro schemas.
