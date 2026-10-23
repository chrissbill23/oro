# ---------- build ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# dependencies first, so they stay cached while only the sources change
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# the tests need Docker (Testcontainers): run them with ./mvnw test, not in the image build
COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/oro-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---------- run ----------
FROM eclipse-temurin:25-jre
WORKDIR /app

# curl: the health check only
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# layers from the least to the most frequently changing
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# the attachments of the local storage (a volume in docker-compose.yml)
RUN mkdir -p /data/attachments && chown ubuntu:ubuntu /data/attachments
ENV ORO_ATTACHMENTS_DIR=/data/attachments

# readiness for the status, liveness to stop a stuck Oro (docker/healthcheck.sh); its start may take a minute
COPY --chmod=755 docker/healthcheck.sh /usr/local/bin/oro-healthcheck
HEALTHCHECK --interval=15s --timeout=12s --start-period=90s --retries=3 CMD ["oro-healthcheck"]

USER ubuntu

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
