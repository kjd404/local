FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/ingest-service-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
