# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy POMs first for incremental caching
COPY pom.xml ./
COPY gateway-service/pom.xml gateway-service/pom.xml
COPY common/pom.xml common/pom.xml

# Bring in the remaining sources
COPY . .

# Build only the gateway-service (and required modules) and isolate the runnable JAR
RUN mvn -q -pl gateway-service -am clean package -DskipTests && \
    cp "$(ls gateway-service/target/*.jar | grep -v -- '-plain\\.jar' | head -n1)" /workspace/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/app.jar /app/app.jar
RUN ln -s /app/app.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
