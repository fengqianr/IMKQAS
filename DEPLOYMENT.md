# IMKQAS 医疗知识问答系统部署指南

## 服务器要求

- **操作系统**: Linux (推荐 Ubuntu 20.04+ 或 CentOS 7+)
- **Docker**: 20.10.0+
- **Docker Compose**: 2.0.0+
- **内存**: 最低 8GB，推荐 16GB+ (Milvus 需要较多内存)
- **存储**: 最少 50GB 可用空间
- **网络**: 开放端口 8080, 3306, 6379, 9000, 9001, 19530

## 服务器准备

### 1. 安装 Docker 和 Docker Compose

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install docker.io docker-compose

# CentOS/RHEL
sudo yum install docker docker-compose
sudo systemctl start docker
sudo systemctl enable docker
```

### 2. 配置防火墙（如果需要）

```bash
# 开放必要端口
sudo ufw allow 22/tcp
sudo ufw allow 8080/tcp
sudo ufw allow 3306/tcp
sudo ufw allow 6379/tcp
sudo ufw allow 9000/tcp
sudo ufw allow 9001/tcp
sudo ufw allow 19530/tcp
sudo ufw enable
```

### 3. 克隆代码到服务器

```bash
git clone <repository-url>
cd IMKQAS
```

## 部署步骤

### 方式一：使用 Docker Compose（推荐）

1. **构建和启动所有服务**

```bash
# 构建应用镜像
docker-compose build

# 启动所有服务（后台运行）
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f app
```

2. **验证服务状态**

```bash
# 检查所有容器是否正常运行
docker-compose ps

# 检查应用健康状态
curl http://8.138.40.200:8080/api/actuator/health
```

### 方式二：分步部署

如果需要单独部署某个服务，可以参考以下命令：

```bash
# 仅启动数据库和缓存
docker-compose up -d mysql redis

# 启动对象存储和向量数据库
docker-compose up -d minio milvus etcd

# 最后启动应用
docker-compose up -d app
```

## 服务访问地址

在服务器 `8.138.40.200` 上部署后，可以通过以下地址访问服务：

| 服务 | 内部地址（容器间） | 外部地址（从服务器外访问） | 默认凭据                                                                                       |
|------|-------------------|--------------------------|--------------------------------------------------------------------------------------------|
| **Spring Boot 应用** | `http://app:8080` | `http://8.138.40.200:8080` | -                                                                                          |
| **MySQL 数据库** | `mysql:3306` | `8.138.40.200:3306` | 用户: `root`<br>密码: `fqr10292588`                                   |
| **Redis 缓存** | `redis:6379` | `8.138.40.200:6379` |
| **MinIO 对象存储** | `http://minio:9000` | `http://8.138.40.200:9000` | AccessKey: `IVL8IthgH8OjCEDLhFKT`<br>SecretKey: `MaJEjDYQYSKfWVadxSPM2knEIKkVrXLgWvvxB8M8` |
| **MinIO 控制台** | `http://minio:9001` | `http://8.138.40.200:9001` | 同上                                                                                         |
| **Milvus 向量数据库** | `milvus:19530` | `8.138.40.200:19530` | -                                                                                          |

## 应用接口

- **API 文档 (Swagger UI)**: http://8.138.40.200:8080/api/swagger-ui.html
- **OpenAPI 规范**: http://8.138.40.200:8080/api/v3/api-docs
- **健康检查**: http://8.138.40.200:8080/api/actuator/health

## 初始配置

### 1. 初始化 MinIO 存储桶

应用启动时会自动创建 `medical-documents` 存储桶。如果需要手动创建：

```bash
访问 MinIO 控制台
http://8.138.40.200:9001
使用 IVL8IthgH8OjCEDLhFKT / MaJEjDYQYSKfWVadxSPM2knEIKkVrXLgWvvxB8M8 登录
创建名为 "medical-documents" 的存储桶
```

### 2. 初始化 Milvus 集合

应用首次启动时会自动在 Milvus 中创建集合。如果需要手动验证：

```bash
# 安装 Milvus 客户端
pip install pymilvus

# 连接并检查
python -c "
from pymilvus import connections, utility
connections.connect(host='8.138.40.200', port='19530')
print('Collections:', utility.list_collections())
"
```

### 3. 数据库初始化

Flyway 会在应用启动时自动执行数据库迁移。初始数据包括：

- 管理员用户: `admin` / `13800000000` / `admin123`
- 测试用户: `testuser` / `13900000000` / `admin123`

## 环境变量配置

可以通过修改 `docker-compose.yml` 中的环境变量来定制配置：

### 关键环境变量

```yaml
# 数据库配置
SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/imkqas?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME: root
SPRING_DATASOURCE_PASSWORD: fqr10292588

# Redis配置
SPRING_REDIS_HOST: redis
SPRING_REDIS_PORT: 6379

# MinIO配置
IMKQAS_MINIO_ENDPOINT: http://minio:9000
IMKQAS_MINIO_ACCESS_KEY: IVL8IthgH8OjCEDLhFKT
IMKQAS_MINIO_SECRET_KEY: MaJEjDYQYSKfWVadxSPM2knEIKkVrXLgWvvxB8M8

# Milvus配置
IMKQAS_MILVUS_HOST: milvus
IMKQAS_MILVUS_PORT: 19530

# JWT配置
IMKQAS_SECURITY_JWT_SECRET: imkqas-medical-rag-secret-key-2026
IMKQAS_SECURITY_JWT_EXPIRATION: 86400000  # 24小时
```

### 生产环境安全建议

1. **修改默认密码**:
   - MySQL root 密码和用户密码
   - Redis 密码
   - MinIO AccessKey/SecretKey
   - JWT 密钥

2. **启用 SSL/TLS**:
   - 为 MySQL 配置 SSL
   - 为应用启用 HTTPS
   - 使用域名而非 IP 地址

3. **配置备份**:
   ```bash
   # 数据库备份
   docker exec imkqas-mysql mysqldump -u root -pfqr10292588 imkqas > backup.sql
   
   # 数据卷备份
   docker run --rm -v imkqas_mysql_data:/data -v $(pwd):/backup alpine tar czf /backup/mysql_backup.tar.gz /data
   ```

## 监控和维护

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs

# 查看特定服务日志
docker-compose logs app
docker-compose logs mysql

# 实时查看日志
docker-compose logs -f app
```

### 服务管理

```bash
# 重启服务
docker-compose restart app

# 停止服务
docker-compose down

# 停止并删除数据（慎用！）
docker-compose down -v

# 更新代码后重新部署
git pull
docker-compose build app
docker-compose up -d app
```

### 数据持久化

所有数据都存储在 Docker 卷中：

- `imkqas_mysql_data`: MySQL 数据库数据
- `imkqas_redis_data`: Redis 数据
- `imkqas_minio_data`: MinIO 对象存储数据
- `imkqas_milvus_data`: Milvus 向量数据
- `imkqas_etcd_data`: etcd 数据
- `imkqas_app_logs`: 应用日志

### 备份和恢复

```bash
# 备份所有数据卷
docker run --rm -v imkqas_mysql_data:/mysql_data -v imkqas_redis_data:/redis_data -v imkqas_minio_data:/minio_data -v $(pwd):/backup alpine tar czf /backup/full_backup_$(date +%Y%m%d).tar.gz /mysql_data /redis_data /minio_data

# 恢复数据
docker run --rm -v imkqas_mysql_data:/mysql_data -v $(pwd):/backup alpine tar xzf /backup/backup.tar.gz -C /
```

## 故障排除

### 常见问题

1. **端口冲突**
   ```
   Error: Port is already allocated
   ```
   解决方案：修改 `docker-compose.yml` 中的端口映射或停止占用端口的服务。

2. **内存不足**
   ```
   Milvus 启动失败或运行缓慢
   ```
   解决方案：增加服务器内存或调整 JVM 参数。

3. **数据库连接失败**
   ```
   Application 启动时连接数据库失败
   ```
   解决方案：确保 MySQL 容器已启动并运行正常，检查网络连接。

4. **存储空间不足**
   ```
   No space left on device
   ```
   解决方案：清理磁盘空间或增加存储容量。

### 获取帮助

1. 查看服务日志：`docker-compose logs [service]`
2. 进入容器调试：`docker exec -it imkqas-app sh`
3. 检查网络连接：`docker network inspect imkqas_imkqas-network`

## 更新和升级

### 应用更新

```bash
# 拉取最新代码
git pull

# 重新构建应用镜像
docker-compose build app

# 重启应用服务
docker-compose up -d app
```

### 数据库迁移

Flyway 会自动执行数据库迁移。如需手动执行：

```bash
# 进入应用容器
docker exec -it imkqas-app sh

# 查看迁移状态
# 应用会自动执行迁移
```

---

**部署成功标志**：访问 `http://8.138.40.200:8080/api/swagger-ui.html` 可以看到 API 文档页面。

如有问题，请检查日志文件或联系系统管理员。