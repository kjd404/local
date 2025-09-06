FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# The Makefile copies the Bazel-built jar to apps/ingest-service/app.jar
COPY app.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
