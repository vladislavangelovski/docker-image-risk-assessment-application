# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the whole repo so Maven can build module dependencies (e.g., "common")
COPY . .

# Produce only scan-service JAR (and required modules)
RUN mvn -q -pl scan-service -am clean package -DskipTests && \
    cp "$(ls scan-service/target/*.jar | grep -v -- '-plain\\.jar' | head -n1)" /workspace/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /

# (a) Install Trivy
RUN apt-get update && apt-get install -y ca-certificates curl && rm -rf /var/lib/apt/lists/* && \
    curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
      | sh -s -- -b /usr/local/bin v0.67.2

# (b) Copy the service JAR
COPY --from=build /workspace/app.jar /app.jar

# (c) App port
EXPOSE 8080

# (d) Run
ENTRYPOINT ["java","-jar","/app.jar"]
