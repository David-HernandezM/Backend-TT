FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/conversor_sql-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_OPTS=""
CMD ["sh","-c","java $JAVA_OPTS -jar app.jar"]
