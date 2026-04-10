FROM eclipse-temurin:17-jre
WORKDIR /app

COPY target/spike-product-service-*.jar /app/product-service.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "product-service.jar"]
