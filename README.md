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
