# ====== build ======
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copiamos lo mínimo para cachear dependencias
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

# Ahora sí, el código
COPY src src
RUN ./mvnw -q -DskipTests package

# ====== runtime ======
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV JAVA_OPTS=""
# Copia el único jar generado (fat jar de Spring Boot)
COPY --from=build /workspace/target/*.jar app.jar
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
