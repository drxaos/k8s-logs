package com.github.drxaos.k8s.logs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.PodLogs;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Streamer extends Thread {
    final static DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS", Locale.ROOT).withZone(ZoneOffset.UTC);
    final static String timestampPattern = "\\d+[-.]\\d+[-.]\\d+[-T]\\d+[-:]\\d+[-:]\\d+([-.,]\\d+)?";
    final static String datePattern = "\\d+[-.]\\d+[-.]\\d+";
    final static String timePattern = "\\d+[-:]\\d+[-:]\\d+([-.,]\\d+)?";
    final static List<String> logLevels = List.of("TRACE", "DEBUG", "INFO", "WARN", "WARNING", "ERROR", "FATAL", "SEVERE", "OFF", "CONFIG", "FINE", "FINER", "FINEST");

    V1Pod pod;
    V1Container container;
    Dao dao;

    public Streamer(V1Pod pod, V1Container container, Dao dao) {
        this.pod = pod;
        this.container = container;
        this.dao = dao;
        start();
        System.out.println("started: " + pod.getMetadata().getName() + "/" + container.getName());
    }

    public void run() {
        String podNamespace = pod.getMetadata().getNamespace();
        String podName = pod.getMetadata().getName();
        String podContainer = container.getName();
        String podUid = pod.getMetadata().getUid();

        PodLogs logs = new PodLogs();

        try (InputStream inputStream = logs.streamNamespacedPodLog(podNamespace, podName, podContainer, 1 * 24 * 60 * 60, null, true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            String line;
            while ((line = reader.readLine()) != null) {

                OffsetDateTime podTimestamp = OffsetDateTime.now();
                String appTimestamp = null;
                String appThread = null;
                String appLevel = null;
                String appLogger = null;
                String appCaller = null;
                String message = line;

                tryTimestamp:
                {
                    String s0 = line;
                    String[] s = s0.split(" ", 2);
                    if (s.length != 2) {
                        break tryTimestamp;
                    }

                    String podTimestampCandidate = s[0]
                            .replace(",", ".");

                    try {
                        podTimestamp = OffsetDateTime.parse(podTimestampCandidate);
                    } catch (DateTimeParseException e) {
                        break tryTimestamp;
                    }

                    s0 = s[1];

                    try {
                        if (s0.startsWith("{")) {
                            // пытаемся что-нибудь оттуда вытащить
                            Map<String, Object> result = new ObjectMapper().readValue(s0, HashMap.class);
                            try {
                                appTimestamp = result.get("timestamp").toString()
                                        .replace("T", " ")
                                        .replace("Z", "")
                                        .replace(",", ".");
                            } catch (Exception e) {
                                try {
                                    appTimestamp = result.get("ts").toString()
                                            .replace("T", " ")
                                            .replace("Z", "")
                                            .replace(",", ".");
                                } catch (Exception e2) {
                                    // ignore
                                }
                            }
                            try {
                                appLogger = result.get("logger").toString();
                            } catch (Exception e) {
                                // ignore
                            }
                            try {
                                appThread = result.get("thread").toString();
                            } catch (Exception e) {
                                // ignore
                            }
                            try {
                                appLevel = result.get("level").toString();
                            } catch (Exception e) {
                                // ignore
                            }
                            try {
                                message = result.get("message").toString();
                                try {
                                    message += "\n" + result.get("stackTrace").toString();
                                } catch (Exception e) {
                                    // ignore
                                }
                            } catch (Exception e) {
                                message = s0;
                            }
                            try {
                                appCaller = result.get("caller_class_name").toString() + "." + result.get("caller_method_name").toString() +
                                        "(" + result.get("caller_file_name").toString() + ":" + result.get("caller_line_number").toString() + ")";
                            } catch (Exception e) {
                                try {
                                    appCaller = result.get("caller").toString();
                                } catch (Exception e2) {
                                    // ignore
                                }
                            }

                            break tryTimestamp;
                        }
                    } catch (Exception e) {
                        // не получилось
                    }

                    tryAppTimestamp:
                    {
                        s = s0.split(" ", 2);
                        if (s.length > 1 && s[0].matches(timestampPattern)) {
                            appTimestamp = s[0]
                                    .replace("T", " ")
                                    .replace("Z", "")
                                    .replace(",", ".");
                            s0 = s[1];
                        } else {
                            s = s0.split(" ", 3);
                            if (s.length > 2 && s[0].matches(datePattern) && s[1].matches(timePattern)) {
                                appTimestamp = (s[0] + " " + s[1])
                                        .replace("T", " ")
                                        .replace("Z", "")
                                        .replace(",", ".");
                                s0 = s[2];
                            } else {
                                break tryAppTimestamp;
                            }
                        }

                        tryLevel:
                        {
                            s = s0.split(" ", 2);
                            if (s.length > 1 && logLevels.contains(s[0])) {
                                appLevel = s[0];
                                s0 = s[1];
                            }
                        }
                    }

                    message = s0;
                }

                String partitionPeriod = podTimestamp.getYear() + "_" + podTimestamp.getMonthValue() + "_" + podTimestamp.getDayOfMonth();

                var log = new LogRecord(
                        podTimestamp.format(timeFormatter),
                        appTimestamp,
                        podName,
                        appLevel,
                        appThread,
                        appLogger,
                        message,
                        appCaller,
                        podUid,
                        podNamespace,
                        podContainer,
                        partitionPeriod
                );

                dao.write(log);
            }
        } catch (Exception e) {
            System.out.println("error: " + podName + "/" + container.getName());
            e.printStackTrace();
        } finally {
            System.out.println("finished: " + pod.getMetadata().getName() + "/" + container.getName());
        }
    }

}
