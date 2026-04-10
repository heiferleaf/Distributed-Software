FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /build

COPY . .

# -DskipTests 跳过测试，-pl 指定构建模块，-am 同时构建依赖模块
RUN mvn clean package -DskipTests -pl spike-user-service -am

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /build/spike-user-service/target/*.jar app.jar

EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
  CMD java -cp app.jar org.springframework.boot.loader.JarLauncher \
      -server -Xms128m -Xmx256m || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "-Xms128m", "-Xmx256m", "app.jar"]