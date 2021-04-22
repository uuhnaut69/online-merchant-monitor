# Online merchant monitor

An example about online merchant monitor based on Kafka Ecosystem

## Prerequisites

- `Java 11+`
- `Docker`
- `Docker-compose`

## Setup

- Start env

```shell
docker-compose up -d
```
- Check health

```shell
docker-compose ps
```

- Create superset user

```shell
docker exec -it superset superset fab create-admin \
               --username admin \
               --firstname Superset \
               --lastname Admin \
               --email admin@superset.com \
               --password admin
```

- Migrate superset local DB to latest

```shell
docker exec -it superset superset db upgrade
```

- Setup superset roles

```shell
docker exec -it superset superset init
```

## Get Started

Connect to KSQL Server

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Create kafka connector

```shell
CREATE SOURCE CONNECTOR `postgresql-connector` WITH (
    'connector.class' = 'io.debezium.connector.postgresql.PostgresConnector',
    'database.hostname' = 'postgresql',
    'database.port' = '5432',
    'database.user' = 'postgres',
    'database.password' = 'postgres',
    'database.dbname' = 'postgres',
    'database.server.name' = 'postgres',
    'decimal.handling.mode' = 'string',
    'key.converter' = 'org.apache.kafka.connect.storage.StringConverter',
    'key.converter.schemas.enable' = 'false',
    'value.converter' = 'io.confluent.connect.avro.AvroConverter',
    'value.converter.schema.registry.url' = 'http://schema-registry:8081',
    'transforms' = 'unwrap,ExtractField',
    'transforms.unwrap.type' = 'io.debezium.transforms.ExtractNewRecordState',
    'transforms.ExtractField.type' = 'org.apache.kafka.connect.transforms.ExtractField$Key',
    'transforms.unwrap.delete.handling.mode' = 'none',
    'transforms.ExtractField.field' = 'id'
);
```

Set offset to earliest

```shell
SET 'auto.offset.reset' = 'earliest';
```

Create DISHES KTable

```shell
CREATE TABLE DISHES (
    rowkey VARCHAR PRIMARY KEY
) WITH (
    KAFKA_TOPIC = 'postgres.public.dishes', 
    VALUE_FORMAT = 'AVRO'
);
```

Create RESTAURANTS KTable
```shell
CREATE TABLE RESTAURANTS (
    rowkey VARCHAR PRIMARY KEY
) WITH (
    KAFKA_TOPIC = 'postgres.public.restaurants', 
    VALUE_FORMAT = 'AVRO'
);
```

Start Data Generator

```shell
cd datagen && ./mvnw spring-boot:run
```

Create ORDERSTREAMS KStream

```shell
CREATE STREAM ORDERSTREAMS (
    rowkey VARCHAR KEY
) WITH (
    KAFKA_TOPIC = 'orders',
    VALUE_FORMAT = 'AVRO'
);
```