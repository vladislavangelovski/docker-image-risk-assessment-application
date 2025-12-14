# ---- Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# copy root pom + module poms first for better caching
COPY pom.xml .
COPY ai-service/pom.xml ai-service/pom.xml
COPY common/pom.xml common/pom.xml
# (optional) copy other module poms if they are referenced
# COPY cve-store-service/pom.xml cve-store-service/pom.xml
# COPY scan-service/pom.xml scan-service/pom.xml

# now copy full source
COPY . .
# build just ai-service (and what it needs)

RUN mvn -q -pl ai-service -am clean package -DskipTests && \
    cp "$(ls ai-service/target/*.jar | grep -v -- '-plain\\.jar' | head -n1)" /workspace/app.jar

# ---- Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
# pick the bootable fat jar:
COPY --from=build /workspace/app.jar /app/app.jar
RUN ln -s /app/app.jar /app.jar
# If your ai-service uses server.port: 8083 in application.yml,
# we expose 8083 here; otherwise change both lines to 8080.
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
