# 多阶段构建：构建阶段
FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app

# 复制Maven配置文件
COPY pom.xml .
# 下载依赖（利用Docker缓存层）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用（跳过测试以加速构建）
RUN mvn clean package -DskipTests

# 运行阶段
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

# 创建非root用户
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 从构建阶段复制构建产物
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 设置JVM选项
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# 启动应用
ENTRYPOINT exec java $JAVA_OPTS -jar app.jar