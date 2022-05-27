# k8s-logs

Утилита для перенаправления логов из кубера в кликхаус.

Сборка: [dist/k8s-logs.jar](dist/k8s-logs.jar)

### Параметры запуска:
* -include - included namespaces
* -exclude - excluded namespaces
* -config - .kube/config
* -host - clickhouse host
* -port - clickhouse port
* -user - clickhouse user
* -password - clickhouse password
* -schema - clickhouse schema
* -table - clickhouse table

### Параметры по умолчанию
```
exclude=[kube-system, metallb-system]
include=[]
config=[] (ищется в системе)
host=[localhost]
port=[8123]
user=[root]
password=[root]
schema=[log]
table=[message]
```

### Схема таблицы для логов
```sql
CREATE TABLE log.message (
`podTimestamp` DateTime64(9, 'UTC'),
`appTimestamp` Nullable(DateTime64(9, 'UTC')),
`podName` Nullable(String),
`appThread` Nullable(String),
`appLevel` Nullable(String),
`appLogger` Nullable(String),
`message` Nullable(String),
`appCaller` Nullable(String),
`partitionPeriod` String,
`podUid` Nullable(String),
`podNamespace` Nullable(String),
`podContainer` Nullable(String)
) ENGINE = MergeTree() PARTITION BY partitionPeriod ORDER BY podTimestamp
```
