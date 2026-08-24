FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY main-app/pom.xml main-app/pom.xml
COPY extraction-svc/pom.xml extraction-svc/pom.xml
COPY main-app/src main-app/src
RUN mvn -q -pl main-app -am -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/main-app/target/main-app-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
