# totsuki

Ingestor for match data streams to store into S3.

Etymology: We "cook" raw matches into pseudorecordio files and prepare them for ETL jobs that read from S3.

## What?

This is a really simple Spark streaming application that does the following:

1. Subscribe to match Kafka topic, e.g. `bacchus.matches.NA`
2. Parse matches from Kafka topic (inefficient, TODO optimize)
3. Extract version from the message
4. Split stream into one RDD per unique version
5. Convert RDD to DF and write pseudorecordio file `$region/$version/$time.protolist` to an S3 bucket.

## Optimization?

The largest optimization would be to somehow send the version separately with the Kafka message, preventing a full deep parse of our very intricate Match message.

Additionally, the upload implementation currently uses the tempfile system to interface with the S3 TransferManager.
An issue has been opened to request the ability to pass in  byte arrays to the TransferManager which would prevent the need for interfacing with the file system: https://github.com/aws/aws-sdk-java/issues/964
