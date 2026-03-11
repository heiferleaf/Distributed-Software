# 构建成 jar 包，需要源码，pom中的依赖配置，jdk包，以及maven工具


FROM maven:3.9-eclipse-temurin-17 AS builder
# 设置工作路径
WORKDIR /app
# 把依赖配置放到工作路径下
COPY pom.xml .
# 先下载依赖（利用 Docker 层缓存，代码变化时不需要重新下载依赖）
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# 将运行环境打包为镜像
# 使用更小的 JRE 镜像，减小最终镜像体积
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 创建非 root 用户（安全最佳实践）
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/app.jar app.jar

# JVM 参数：容器感知内存，避免 OOM
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]