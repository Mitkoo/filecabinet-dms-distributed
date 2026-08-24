FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY main-app/pom.xml main-app/pom.xml
COPY extraction-svc/pom.xml extraction-svc/pom.xml
COPY extraction-svc/src extraction-svc/src
RUN mvn -q -pl extraction-svc -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/extraction-svc/target/extraction-svc-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
