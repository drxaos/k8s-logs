package com.github.drxaos.k8s.logs;

import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class Watcher {

    static ConcurrentHashMap<String, Streamer> streamers = new ConcurrentHashMap<>();

    public static void watch(CoreV1Api coreApi, List<String> includeNs, List<String> excludeNS) {
        while (true) {
            try {
                streamers.entrySet().removeIf(e -> !e.getValue().isAlive());

                V1PodList list = coreApi.listPodForAllNamespaces(null, null, null, null, null, null, null, null, 10, null);
                for (V1Pod pod : list.getItems()) {
                    if (includeNs != null && includeNs.size() > 0 && !includeNs.contains(pod.getMetadata().getNamespace())) {
                        continue;
                    }
                    if (excludeNS != null && excludeNS.contains(pod.getMetadata().getNamespace())) {
                        continue;
                    }
                    if (Objects.equals(pod.getStatus().getPhase(), "Running")) {
                        List<V1Container> containers = pod.getSpec().getContainers();
                        for (V1Container container : containers) {
                            streamers.computeIfAbsent(pod.getMetadata().getUid() + "/" + container.getName(), (x) -> new Streamer(pod, container, new Dao(pod)));
                        }
                    }
                }
                Thread.sleep(15000);
                System.out.println("streams: " + streamers.size() + " logs: " + Dao.count.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
