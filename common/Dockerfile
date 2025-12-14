# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy POM files first for better layer caching
COPY pom.xml ./
COPY common/pom.xml common/pom.xml

# Copy the remaining sources
COPY . .

# Build only the common module and capture the packaged artifact
RUN mvn -q -pl common -am clean package -DskipTests && \
    cp "$(ls common/target/*.jar | grep -v -- '-sources\\.jar\|-javadoc\\.jar\|-tests\\.jar\|-plain\\.jar' | head -n1)" /workspace/app.jar

# ---------- Runtime stage ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar
RUN ln -s /app/app.jar /app.jar

ENTRYPOINT ["java","-jar","/app/app.jar"]
