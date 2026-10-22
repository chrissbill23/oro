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

# layers from the least to the most frequently changing
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER ubuntu

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
