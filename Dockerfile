# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar -------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what Maven needs first, so the dependency download is cached
# until pom.xml changes (source edits do not trigger a re-download).
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests need Docker (Testcontainers) and already run in CI, so they are skipped here.
RUN ./mvnw -B -q -DskipTests package

# ---- Stage 2: run it --------------------------------------------------------
# Only a JRE (no compiler, no Maven, no sources) ends up in the final image.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Do not run as root inside the container
RUN useradd --system --uid 10001 partsflow
USER partsflow

COPY --from=build /workspace/target/partsflow-*.jar app.jar

EXPOSE 8080
# Use the memory limit of the container, not the memory of the whole machine
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
