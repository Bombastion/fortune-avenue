# --- Build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Copy just the build config first so dependency resolution is cached
# in its own layer and only re-runs when these files change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Now copy sources and build the runnable jar.
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# --- Test stage ---
# Reuses everything the build stage already set up (JDK, wrapper, cached
# deps, sources). Doesn't run tests at image-build time — CMD runs them
# when the container starts, so results/reports can be written to a
# mounted volume instead of being stuck inside a build layer.
FROM build AS test
CMD ["./gradlew", "--no-daemon", "test"]

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy AS runtime

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
