# syntax=docker/dockerfile:1

# ---- Build -------------------------------------------------------------------------------
# Tests are not run here: CI runs the full suite (unit + Testcontainers ITs) before an image
# is built, and running Testcontainers inside a docker build would need the host's socket.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first, in their own layer, so a source-only change does not download the
# internet again. The cache mount keeps ~/.m2 across builds on the same builder.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/tutorspoint-backend-*.jar \
       extract --layers --launcher --destination /build/extracted

# ---- Runtime -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# A fixed uid/gid, so the ownership of the mounted uploads volume is predictable.
RUN addgroup -S -g 10001 tutorspoint \
    && adduser -S -D -H -u 10001 -G tutorspoint tutorspoint \
    && mkdir -p /app /var/lib/tutorspoint/uploads \
    && chown tutorspoint:tutorspoint /var/lib/tutorspoint/uploads

WORKDIR /app

# Least-changing layer first: a release that only touches our code ships one small layer.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER tutorspoint

ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    STORAGE_ROOT=/var/lib/tutorspoint/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"

EXPOSE 8080

# Flyway migrates, and Hibernate validates, before the web server takes a request, so a
# healthy container is one whose schema matched. start-period covers a cold start on a
# small VPS; a failed migration exits the JVM long before it runs out.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
