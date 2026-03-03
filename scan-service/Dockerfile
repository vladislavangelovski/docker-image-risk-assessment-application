# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the whole repo so Maven can build module dependencies (e.g., "common")
COPY . .

# Produce only scan-service JAR (and required modules)
RUN mvn -q -pl scan-service -am clean package -DskipTests && \
    cp "$(ls scan-service/target/*.jar | grep -v -- '-plain\\.jar' | head -n1)" /workspace/app.jar

# ---------- Trivy binary stage ----------
FROM aquasec/trivy:0.67.2 AS trivy

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# (a) Install Trivy binary from pinned image
COPY --from=trivy /usr/local/bin/trivy /usr/local/bin/trivy

# (b) Copy the service JAR
COPY --from=build /workspace/app.jar /app/app.jar
RUN ln -s /app/app.jar /app.jar

# (c) App port
EXPOSE 8080

# (d) Run
ENTRYPOINT ["java","-jar","/app/app.jar"]
