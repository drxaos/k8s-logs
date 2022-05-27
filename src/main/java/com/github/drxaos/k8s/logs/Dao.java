package com.github.drxaos.k8s.logs;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.kubernetes.client.openapi.models.V1Pod;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

public class Dao {
    final static HikariDataSource ds;
    final static String table;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:clickhouse://" + Main.params.get("host").get(0) + ":" + Main.params.get("port").get(0) + "/" + Main.params.get("schema").get(0));
        config.setUsername(Main.params.get("user").get(0));
        config.setPassword(Main.params.get("password").get(0));
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "32");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        ds = new HikariDataSource(config);

        table = Main.params.get("schema").get(0) + "." + Main.params.get("table").get(0);
    }

    public static AtomicLong count = new AtomicLong(0L);

    V1Pod pod;

    public Dao(V1Pod pod) {
        this.pod = pod;
    }

    public String queryForLastTS(V1Pod pod) {
        String sql = "select max(podTimestamp) from " + table + " where podUid = ?";
        try (var connection = ds.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, pod.getMetadata().getUid());
                try (var resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString(1);
                    }
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String queryForExist(V1Pod pod, String partitionPeriod, String podTimestamp) {
        String sql = "select podTimestamp from " + table + " where partitionPeriod = ? and podUid = ? and podTimestamp = toDateTime64(?, 9, 'UTC')";
        try (var connection = ds.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, partitionPeriod);
                statement.setString(2, pod.getMetadata().getUid());
                statement.setString(3, podTimestamp);
                try (var resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString(1);
                    }
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(LogRecord record) {
        String sql = "INSERT INTO " + table + "\n" +
                "(podTimestamp, appTimestamp, podName, appThread, appLevel, appLogger, message, appCaller, partitionPeriod, podUid, podNamespace, podContainer)\n" +
                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (var connection = ds.getConnection()) {
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, record.podTimestamp());
                statement.setString(2, record.appTimestamp());
                statement.setString(3, record.podName());
                statement.setString(4, record.appThread());
                statement.setString(5, record.appLevel());
                statement.setString(6, record.appLogger());
                statement.setString(7, record.message());
                statement.setString(8, record.appCaller());
                statement.setString(9, record.partitionPeriod());
                statement.setString(10, record.podUid());
                statement.setString(11, record.podNamespace());
                statement.setString(12, record.podContainer());
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(LogRecord record) {
        String exist = queryForExist(pod, record.partitionPeriod(), record.podTimestamp());
        if (exist == null) {
            save(record);
            count.incrementAndGet();
        }
    }
}
