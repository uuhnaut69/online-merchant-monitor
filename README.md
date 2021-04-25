# Online merchant monitor

An example about online merchant monitor based on Kafka, Kafka Stream, Kafka Connect,KSQL

![Flow](https://github.com/uuhnaut69/online-merchant-monitor/blob/main/images/Flow.png)

![DemoChart](https://github.com/uuhnaut69/online-merchant-monitor/blob/main/images/DemoChart.png)

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
CREATE
SOURCE CONNECTOR `postgresql-connector`
WITH (
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
CREATE TABLE DISHES
(
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
CREATE TABLE RESTAURANTS
(
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
CREATE
STREAM ORDERSTREAMS (
    rowkey VARCHAR KEY
)
WITH (
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
create
or
replace
stream order_with_restaurant
with (KAFKA_TOPIC='order_with_restaurant', KEY_FORMAT='KAFKA', VALUE_FORMAT='AVRO', TIMESTAMP ='CREATED_AT') as
select o.RESTAURANT_ID        as RESTAURANT_ID,
       r.NAME                 as NAME,
       o.ORDER_ID             as ORDER_ID,
       o.LAT                  as LAT,
       o.LON                  as LON,
       o.CREATED_AT           as CREATED_AT,
       EXPLODE(o.ORDER_LINES) as ORDER_LINE
from ORDERSTREAMS o
         inner join RESTAURANTS r on
    cast(o.RESTAURANT_ID as STRING) = r.ROWKEY partition by o.ORDER_ID emit changes;
```

Enrich (1) downstream with dish info

Currently KSQLDB aggregate_functions COLLECT_SET() not support MAP, STRUCT, ARRAY types so we need convert complex
column to VARCHAR/STRING

```sql
create
or
replace
stream order_with_restaurant_dish
with (KAFKA_TOPIC='order_with_restaurant_dish', KEY_FORMAT='KAFKA', VALUE_FORMAT='AVRO', TIMESTAMP ='CREATED_AT') as
select owr.RESTAURANT_ID                                                as RESTAURANT_ID,
       owr.NAME                                                         as RESTAURANT_NAME,
       owr.ORDER_ID                                                     as ORDER_ID,
       owr.LAT                                                          as LAT,
       owr.LON                                                          as LON,
       owr.CREATED_AT                                                   as CREATED_AT,
       map(
               'DISH_ID' := d.ROWKEY,
               'DISH_NAME' := d.NAME,
               'DISH_PRICE' := d.PRICE,
               'DISH_TYPE' := d.TYPE,
               'UNIT' := cast(owr.ORDER_LINE -> UNIT as STRING)
           )                                                            as ORDER_LINE,
       ('DISH_ID:=' + d.ROWKEY + ',DISH_NAME:=' + d.NAME + ',DISH_PRICE:=' + d.PRICE + ',DISH_TYPE:=' + d.type +
        ',ORDER_UNIT:=' + cast(owr.ORDER_LINE -> UNIT as VARCHAR))      as ORDER_LINE_STRING,
       cast(d.PRICE as DOUBLE) * cast(owr.ORDER_LINE -> UNIT as DOUBLE) as ORDER_LINE_PRICE
from ORDER_WITH_RESTAURANT owr
         inner join DISHES d on
    cast(owr.ORDER_LINE -> DISH_ID as STRING) = d.ROWKEY partition by owr.ORDER_ID emit changes;
```

Aggregate orders of each dish per 30 seconds

```sql
create table dish_order_30seconds_report
    with (KAFKA_TOPIC = 'dish_order_30seconds_report', KEY_FORMAT = 'AVRO', VALUE_FORMAT = 'AVRO') as
select ORDER_LINE['DISH_ID'],
       ORDER_LINE['DISH_NAME'],
       cast(as_value(ORDER_LINE['DISH_ID']) as BIGINT) as DISH_ID,
       as_value(ORDER_LINE['DISH_NAME'])               as DISH_NAME,
       as_value(FROM_UNIXTIME(WINDOWSTART))            as WINDOW_START,
       as_value(FROM_UNIXTIME(WINDOWEND))              as WINDOW_END,
       count(1)                                        as ORDER_COUNT
from order_with_restaurant_dish window TUMBLING (SIZE 30 SECONDS)
    group by ORDER_LINE['DISH_ID'], ORDER_LINE['DISH_NAME'] emit changes;
```

Test:

```sql
select DISH_ID, DISH_NAME, WINDOW_START, WINDOW_END, ORDER_COUNT
from dish_order_30seconds_report emit changes
limit 5;
```

Result:

```sql
+---------------------------------+---------------------------------+---------------------------------+---------------------------------+---------------------------------+
|DISH_ID                          |DISH_NAME                        |WINDOW_START                     |WINDOW_END                       |ORDER_COUNT                      |
+---------------------------------+---------------------------------+---------------------------------+---------------------------------+---------------------------------+
|7                                |Roasted pork meat                |2021-04-25T13:16:00.000          |2021-04-25T13:16:30.000          |1                                |
|2                                |Grilled octopus                  |2021-04-25T13:16:00.000          |2021-04-25T13:16:30.000          |1                                |
|8                                |Seaweed soup                     |2021-04-25T13:16:00.000          |2021-04-25T13:16:30.000          |1                                |
|9                                |Sour soup                        |2021-04-25T13:16:00.000          |2021-04-25T13:16:30.000          |1                                |
|5                                |Roasted duck                     |2021-04-25T13:16:00.000          |2021-04-25T13:16:30.000          |1                                |
```

Create sink connector save aggregate result to Citus Data

```sql
CREATE
SINK CONNECTOR `dish_order_30seconds_report_sink`
WITH (
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

Create enriched orders KTable

```sql
create table enriched_orders
    with (KAFKA_TOPIC = 'enriched_orders', KEY_FORMAT = 'AVRO', VALUE_FORMAT = 'AVRO', TIMESTAMP = 'CREATED_AT') as
select RESTAURANT_ID,
       RESTAURANT_NAME,
       ORDER_ID,
       LAT,
       LON,
       CREATED_AT,
       as_value(RESTAURANT_ID)                          as ENRICHED_ORDER_RESTAURANT_ID,
       as_value(RESTAURANT_NAME)                        as ENRICHED_ORDER_RESTAURANT_NAME,
       as_value(ORDER_ID)                               as ENRICHED_ORDER_ID,
       as_value(LAT)                                    as ENRICED_ORDER_LAT,
       as_value(LON)                                    as ENRICED_ORDER_LON,
       as_value(CREATED_AT)                             as ENRICHED_ORDER_CREATED_DATE,
       transform(collect_set(ORDER_LINE_STRING),
                 item => SPLIT_TO_MAP(item, ',', ':=')) as ENRICHED_ORDER_LINES,
       sum(ORDER_LINE_PRICE)                            as ENRICHED_ORDER_TOTAL_PRICE
from order_with_restaurant_dish
group by RESTAURANT_ID,
         RESTAURANT_NAME,
         ORDER_ID,
         LAT,
         LON,
         CREATED_AT emit changes;
```

Test

```sql
select *
from enriched_orders emit changes
limit 1;
```

Result

```sql
+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+
|RESTAURANT|RESTAURANT|ORDER_ID  |LAT       |LON       |CREATED_AT|ENRICHED_O|ENRICHED_O|ENRICHED_O|ENRICED_OR|ENRICED_OR|ENRICHED_O|ENRICHED_O|ENRICHED_O|
|_ID       |_NAME     |          |          |          |          |RDER_RESTA|RDER_RESTA|RDER_ID   |DER_LAT   |DER_LON   |RDER_CREAT|RDER_LINES|RDER_TOTAL|
|          |          |          |          |          |          |URANT_ID  |URANT_NAME|          |          |          |ED_DATE   |          |_PRICE    |
+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+----------+
|1         |RESTAURANT|d0c43512-d|16.9464080|108.732419|1619356561|1         |RESTAURANT|d0c43512-d|16.9464080|108.732419|1619356561|[{DISH_NAM|720000.0  |
|          |_A        |d7a-4768-9|3337097   |66962814  |202       |          |_A        |d7a-4768-9|3337097   |66962814  |202       |E=Roasted |          |
|          |          |028-fed917|          |          |          |          |          |028-fed917|          |          |          |pork meat,|          |
|          |          |12ba88    |          |          |          |          |          |12ba88    |          |          |          | ORDER_UNI|          |
|          |          |          |          |          |          |          |          |          |          |          |          |T=4, DISH_|          |
|          |          |          |          |          |          |          |          |          |          |          |          |PRICE=1800|          |
|          |          |          |          |          |          |          |          |          |          |          |          |00.00, DIS|          |
|          |          |          |          |          |          |          |          |          |          |          |          |H_TYPE=ROA|          |
|          |          |          |          |          |          |          |          |          |          |          |          |STED, DISH|          |
|          |          |          |          |          |          |          |          |          |          |          |          |_ID=7}]   |          |
```

Connect Superset to Citus

```shell
postgresql://merchant:merchant@citus:5432/merchant
```