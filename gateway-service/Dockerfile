# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the repository to build the gateway module and its dependencies
COPY . .

RUN mvn -q -pl gateway-service -am clean package -DskipTests && \
    mkdir -p /out && \
    BOOT_JAR="$(ls gateway-service/target/*.jar | grep -v -- '-plain\\.jar' | head -n1)" && \
    cp "$BOOT_JAR" /out/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /

COPY --from=build /out/app.jar /app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
