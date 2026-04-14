# IMKQAS 医疗知识问答系统部署指南

## 服务器配置

- **操作系统**: CentOS 7+
- **内存**: 3.5Gi
- **Docker**: 20.10.0+
- **Docker Compose**: 2.0.0+
- **IP地址**: 8.138.40.200

## 服务架构

采用**混合部署模式**：

| 服务 | 运行方式 | 管理方式 |
|------|----------|----------|
| MySQL | 系统进程 | systemd |
| Redis | 系统进程 | 手动启动 |
| MinIO | 系统进程 | 手动启动 |
| Milvus | Docker容器 | docker-compose |
| IMKQAS App | Docker容器 | docker-compose |

---

## 服务访问地址

在服务器 `8.138.40.200` 上部署后，可以通过以下地址访问服务：

| 服务 | 内部地址（容器间） | 外部地址（从服务器外访问） | 默认凭据 |
|------|-------------------|--------------------------|----------|
| **Spring Boot 应用** | `http://app:8080` | `http://8.138.40.200:8080` | - |
| **MySQL 数据库** | `mysql:3306` | `8.138.40.200:3306` | 用户: `root`<br>密码: `fqr10292588` |
| **Redis 缓存** | `redis:6379` | `8.138.40.200:6379` | - |
| **MinIO 对象存储** | `http://minio:9000` | `http://8.138.40.200:9000` | AccessKey: `IVL8IthgH8OjCEDLhFKT`<br>SecretKey: `MaJEjDYQYSKfWVadxSPM2knEIKkVrXLgWvvxB8M8` |
| **MinIO 控制台** | `http://minio:9001` | `http://8.138.40.200:9001` | 同上 |
| **Milvus 向量数据库** | `milvus:19530` | `8.138.40.200:19530` | - |

## 应用接口

- **API 文档 (Swagger UI)**: http://8.138.40.200:8080/api/swagger-ui.html
- **OpenAPI 规范**: http://8.138.40.200:8080/api/v3/api-docs
- **健康检查**: http://8.138.40.200:8080/api/actuator/health

---

## 一、服务启动指南

### 1.1 MySQL（已配置开机自启）

```bash
# 启动
sudo systemctl start mysqld

# 停止
sudo systemctl stop mysqld

# 状态
sudo systemctl status mysqld

# 连接测试
mysql -u root -p'fqr10292588' -e "SELECT 1"
```


### 1.2 Redis
```bash
# 启动
cd ~/redis-7.0.12
src/redis-server --daemonize yes

# 停止
redis-cli shutdown

# 测试
redis-cli ping
# 应返回 PONG
```

### 1.3 MinIO
```bash
# 启动
export MINIO_ROOT_USER=IVL8IthgH8OjCEDLhFKT
export MINIO_ROOT_PASSWORD=MaJEjDYQYSKfWVadxSPM2knEIKkVrXLgWvvxB8M8
nohup minio server ~/minio_data --console-address ":9001" > ~/minio.log 2>&1 &

# 停止
pkill minio

# 测试
curl http://localhost:9000/minio/health/live
# 应返回 OK
```
### 1.4 Milvus（Docker Compose）
```bash
# 启动
cd ~
docker-compose -f milvus-standalone-docker-compose.yml up -d

# 停止
docker-compose -f milvus-standalone-docker-compose.yml down

# 重启
docker-compose -f milvus-standalone-docker-compose.yml restart

# 状态
docker-compose -f milvus-standalone-docker-compose.yml ps

# 日志
docker-compose -f milvus-standalone-docker-compose.yml logs -f

# 测试
curl http://localhost:9091/healthz
```
### 1.5 IMKQAS App（Docker Compose）
```bash
# 首次构建并启动
cd ~/IMKQAS
export DOCKER_BUILDKIT=0
docker-compose up --build -d

# 启动（已构建过）
docker-compose up -d

# 停止
docker-compose down

# 重启
docker-compose restart app

# 状态
docker-compose ps

# 日志
docker-compose logs -f app

# 测试
curl http://localhost:8080/api/actuator/health
```