FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

COPY target/wool-coat-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx512m"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
