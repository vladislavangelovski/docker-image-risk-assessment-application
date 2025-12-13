# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the whole repo so Maven can build module dependencies (e.g., "common")
COPY . .

# Speed up: use Maven wrapper cache; produce only scan-service JAR (and required modules)
RUN chmod +x ./mvnw && \
    ./mvnw -q -pl scan-service -am clean package -DskipTests spring-boot:repackage && \
    mkdir -p /out && \
    BOOT_JAR="$(ls scan-service/target/*.jar | grep -v -- '-plain\.jar' | head -n1)" && \
    cp "$BOOT_JAR" /out/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /

# (a) Install Trivy
RUN apt-get update && apt-get install -y ca-certificates curl && rm -rf /var/lib/apt/lists/* && \
    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
      | sh -s -- -b /usr/local/bin v0.67.2

# (b) Copy the service JAR
COPY --from=build /out/app.jar /app.jar

# (c) App port
EXPOSE 8080

# (d) Run
ENTRYPOINT ["java","-jar","/app.jar"]
