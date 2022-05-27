package com.github.drxaos.k8s.logs;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Main {
    public static final Map<String, List<String>> params = new TreeMap<>();

    static void parseArgs(String[] args) {
        List<String> options = null;
        for (final String a : args) {
            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    throw new RuntimeException("Error at argument " + a);
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                throw new RuntimeException("Illegal parameter usage");
            }
        }
    }

    public static void checkConfig(String key, boolean singleValue, List<String> defaultValues) {
        List<String> values = params.computeIfAbsent(key, (k) -> defaultValues);
        if (singleValue && values.size() > 1) {
            throw new RuntimeException("too many parameters: " + key);
        }
    }

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        if (params.get("help") != null) {
            System.out.println("Options:\n" +
                    "-include - included namespaces\n" +
                    "-exclude - excluded namespaces\n" +
                    "-config - .kube/config\n" +
                    "-host - clickhouse host\n" +
                    "-port - clickhouse port\n" +
                    "-user - clickhouse user\n" +
                    "-password - clickhouse password\n" +
                    "-schema - clickhouse schema\n" +
                    "-table - clickhouse table\n" +
                    "\n" +
                    "CREATE TABLE log.message (\n" +
                    "`podTimestamp` DateTime64(9, 'UTC'),\n" +
                    " `appTimestamp` Nullable(DateTime64(9, 'UTC')),\n" +
                    " `podName` Nullable(String),\n" +
                    " `appThread` Nullable(String),\n" +
                    " `appLevel` Nullable(String),\n" +
                    " `appLogger` Nullable(String),\n" +
                    " `message` Nullable(String),\n" +
                    " `appCaller` Nullable(String),\n" +
                    " `partitionPeriod` String,\n" +
                    " `podUid` Nullable(String),\n" +
                    " `podNamespace` Nullable(String),\n" +
                    " `podContainer` Nullable(String)\n" +
                    ") ENGINE = MergeTree() PARTITION BY partitionPeriod ORDER BY podTimestamp");
        }

        checkConfig("include", false, List.of()); // included namespaces
        checkConfig("exclude", false, List.of("kube-system", "metallb-system", "k8s-logs", "loki-stack", "monitoring")); // excluded namespaces
        checkConfig("config", true, List.of()); // .kube/config
        checkConfig("host", true, List.of("localhost")); // clickhouse host
        checkConfig("port", true, List.of("8123")); // clickhouse port
        checkConfig("user", true, List.of("root")); // clickhouse user
        checkConfig("password", true, List.of("root")); // clickhouse password
        checkConfig("schema", true, List.of("logs")); // clickhouse schema
        checkConfig("table", true, List.of("message")); // clickhouse table

        System.out.println("Options: " + params);

        ApiClient client;
        List<String> config = params.get("config");
        if (config.size() > 0) {
            client = Config.fromConfig(config.get(0));
        } else {
            client = Config.defaultClient();
        }

        Configuration.setDefaultApiClient(client);
        CoreV1Api coreApi = new CoreV1Api(client);

        Watcher.watch(coreApi, params.get("include"), params.get("exclude"));
    }
}
