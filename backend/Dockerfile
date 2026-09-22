# Java 27 (GA 2026-09-15). Temurin har annu inga 27-avbildningar publicerade
# (eclipse-temurin:27-jdk/jre och maven:3.9-eclipse-temurin-27 ger alla 404 pa Docker Hub,
# kontrollerat 2026-09-22), sa bygget gar pa Liberica - samma OpenJDK 27, annan leverantor.
# Maven kommer fran wrappern i repot eftersom ingen maven-avbildning har JDK 27 an; den
# laddar ner sig sjalv med wget, curl ELLER bara java och staller darfor inga krav pa
# basavbildningen. Byt tillbaka till Temurin nar deras 27 dyker upp - tva rader.
FROM bellsoft/liberica-openjdk-debian:27 AS build
WORKDIR /app
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests

FROM bellsoft/liberica-openjre-alpine:27
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
