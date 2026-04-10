FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# 为了利用构建缓存，先复制所有的 pom 文件
COPY pom.xml ./
COPY gateway/pom.xml ./gateway/
COPY spike-user-service/pom.xml ./spike-user-service/

# 下载依赖
# RUN mvn dependency:go-offline -B -pl gateway -am

# 复制源码
COPY gateway/src ./gateway/src

# 编译打包 (只构建 gateway 和它的依赖 (-am))
RUN mvn clean package -DskipTests -pl gateway -am

# 运行时阶段基于轻量的 JRE 镜像
FROM eclipse-temurin:17-jre

WORKDIR /app

# 从构建阶段复制打包好的 jar 文件
COPY --from=builder /app/gateway/target/gateway-0.0.1-SNAPSHOT.jar ./gateway.jar

# 暴露端口
EXPOSE 8080

# 启动服务
ENTRYPOINT ["java", "-jar", "gateway.jar"]
