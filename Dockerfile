FROM openjdk:16-jdk-alpine
WORKDIR /app
COPY target/k8s-logs*.jar /app/k8s-logs.jar
COPY target/config /app/config
CMD java -Xms64m -Xmx1023m -jar k8s-logs.jar -config ./config -host kub-clh-logs -user default -password lYLFLVDTdWuBLuCTilVS -schema logs -table kub_logs
