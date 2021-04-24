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

Access `Superset` via `http://localhost:9669`

## Get Started

Connect to KSQL Server

```shell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

Create kafka connector

```sql
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

```sql
SET 'auto.offset.reset' = 'earliest';
```

Create DISHES KTable

```sql
CREATE TABLE DISHES (
    rowkey VARCHAR PRIMARY KEY
) WITH (
    KAFKA_TOPIC = 'postgres.public.dishes', 
    VALUE_FORMAT = 'AVRO'
);
```

```sql
Name                 : DISHES
Field         | Type
------------------------------------------------
ROWKEY        | VARCHAR(STRING)  (primary key)
ID            | BIGINT
NAME          | VARCHAR(STRING)
PRICE         | VARCHAR(STRING)
TYPE          | VARCHAR(STRING)
RESTAURANT_ID | BIGINT
------------------------------------------------
```


Create RESTAURANTS KTable
```sql
CREATE TABLE RESTAURANTS (
    rowkey VARCHAR PRIMARY KEY
) WITH (
    KAFKA_TOPIC = 'postgres.public.restaurants', 
    VALUE_FORMAT = 'AVRO'
);
```

```sql
Name                 : RESTAURANTS
 Field  | Type
-----------------------------------------
 ROWKEY | VARCHAR(STRING)  (primary key)
 ID     | BIGINT
 NAME   | VARCHAR(STRING)
-----------------------------------------
```

Start Data Generator

```sql
cd datagen && ./mvnw spring-boot:run
```

Create ORDERSTREAMS KStream

```sql
CREATE STREAM ORDERSTREAMS (
    rowkey VARCHAR KEY
) WITH (
    KAFKA_TOPIC = 'orders',
    VALUE_FORMAT = 'AVRO'
);
```

```sql
Name                 : ORDERSTREAMS
 Field         | Type
-------------------------------------------------------------
 ROWKEY        | VARCHAR(STRING)  (key)
 RESTAURANT_ID | BIGINT
 ORDER_ID      | VARCHAR(STRING)
 LAT           | DOUBLE
 LON           | DOUBLE
 CREATED_AT    | BIGINT
 ORDER_LINES   | ARRAY<STRUCT<DISH_ID BIGINT, UNIT INTEGER>>
-------------------------------------------------------------
```
Flatten order streams and enrich with restaurant info (1)

```sql
create or replace stream order_with_restaurant 
with(KAFKA_TOPIC='order_with_restaurant', KEY_FORMAT='KAFKA', VALUE_FORMAT='AVRO', TIMESTAMP='CREATED_AT') as 
    select
        o.RESTAURANT_ID as RESTAURANT_ID,
        r.NAME as NAME,
        o.ORDER_ID as ORDER_ID,
        o.LAT as LAT,
        o.LON as LON,
        o.CREATED_AT as CREATED_AT,
        EXPLODE(o.ORDER_LINES) as ORDER_LINE
    from
        ORDERSTREAMS o
    inner join RESTAURANTS r on
        cast(o.RESTAURANT_ID as STRING) = r.ROWKEY 
    partition by o.ORDER_ID;
```
Enrich (1) downstream with dish info

```sql
create or replace stream order_with_restaurant_dish 
with(KAFKA_TOPIC='order_with_restaurant_dish', KEY_FORMAT='KAFKA', VALUE_FORMAT='AVRO', TIMESTAMP='CREATED_AT') as
    select
        owr.RESTAURANT_ID as RESTAURANT_ID,
        owr.NAME as RESTAURANT_NAME,
        owr.ORDER_ID as ORDER_ID,
        owr.LAT as LAT,
        owr.LON as LON,
        owr.CREATED_AT as CREATED_AT,
        map(
            'DISH_ID' := d.ROWKEY, 
            'DISH_NAME' := d.NAME, 
            'DISH_PRICE' := d.PRICE, 
            'DISH_TYPE' := d.TYPE, 
            'UNIT' := cast(owr.ORDER_LINE -> UNIT as STRING)
        ) as ORDER_LINE,
        cast(d.PRICE as DOUBLE) * cast(owr.ORDER_LINE -> UNIT as DOUBLE) as ORDER_LINE_PRICE
    from
        ORDER_WITH_RESTAURANT owr
    inner join DISHES d on
        cast(owr.ORDER_LINE -> DISH_ID as STRING) = d.ROWKEY
    partition by owr.ORDER_ID;
```

Aggregate orders of each dish per 30 seconds

```sql
create table dish_order_30seconds_report with(KAFKA_TOPIC='dish_order_30seconds_report', KEY_FORMAT='AVRO', VALUE_FORMAT='AVRO') as
    select
        ORDER_LINE['DISH_ID'],
        ORDER_LINE['DISH_NAME'],
        cast(as_value(ORDER_LINE['DISH_ID']) as BIGINT) as DISH_ID,
        as_value(ORDER_LINE['DISH_NAME']) as DISH_NAME,
        as_value(FROM_UNIXTIME(WINDOWSTART)) as WINDOW_START,
        as_value(FROM_UNIXTIME(WINDOWEND)) as WINDOW_END,
        count(1) as ORDER_COUNT
    from
        order_with_restaurant_dish window TUMBLING (SIZE 30 SECONDS)
    group by ORDER_LINE['DISH_ID'], ORDER_LINE['DISH_NAME'];
```

Test:

```sql
select DISH_ID, DISH_NAME, WINDOW_START, WINDOW_END, ORDER_COUNT from dish_order_30seconds_report emit changes limit 5;
```
Result:
```sql
+---------------------------------------------+---------------------------------------------+---------------------------------------------+---------------------------------------------+---------------------------------------------+
|DISH_ID                                      |DISH_NAME                                    |WINDOW_START                                 |WINDOW_END                                   |ORDER_COUNT                                  |
+---------------------------------------------+---------------------------------------------+---------------------------------------------+---------------------------------------------+---------------------------------------------+
|12                                           |Mỳ vịt tiềm                                  |2021-04-24T02:55:30.000                      |2021-04-24T02:56:00.000                      |1                                            |
|11                                           |Mì xíu                                       |2021-04-24T02:55:30.000                      |2021-04-24T02:56:00.000                      |1                                            |
|24                                           |Gà quay nguyên con                           |2021-04-24T02:55:30.000                      |2021-04-24T02:56:00.000                      |1                                            |
|6                                            |Cơm chiên dương châu                         |2021-04-24T02:55:30.000                      |2021-04-24T02:56:00.000                      |1                                            |
|16                                           |Mì xào hải sản                               |2021-04-24T02:55:30.000                      |2021-04-24T02:56:00.000                      |1                                            |
```

Create sink connector save aggregate result to Citus Data

```sql
CREATE SINK CONNECTOR `dish_order_30seconds_report_sink` WITH (
    'connector.class' = 'io.confluent.connect.jdbc.JdbcSinkConnector',
    'connection.url' = 'jdbc:postgresql://citus:5432/merchant',
    'connection.user' = 'merchant',
    'connection.password' = 'merchant',
    'insert.mode' = 'upsert',
    'topics' = 'dish_order_30seconds_report',
    'key.converter' = 'io.confluent.connect.avro.AvroConverter',
    'key.converter.schema.registry.url' = 'http://schema-registry:8081',
    'value.converter' = 'io.confluent.connect.avro.AvroConverter',
    'value.converter.schema.registry.url' = 'http://schema-registry:8081',
    'pk.mode' = 'record_value',
    'pk.fields' = 'DISH_ID,WINDOW_START,WINDOW_END',
    'auto.create' = true,
    'auto.evolve' = true
);
```

Connect Superset to Citus

```shell
postgresql://merchant:merchant@citus:5432/merchant
```