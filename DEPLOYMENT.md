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
# 1. 使用DaoCloud镜像
docker pull docker.m.daocloud.io/minio/minio:RELEASE.2023-03-20T20-16-18Z
docker tag docker.m.daocloud.io/minio/minio:RELEASE.2023-03-20T20-16-18Z minio/minio:RELEASE.2023-03-20T20-16-18Z

# 2. 拉取etcd
docker pull docker.m.daocloud.io/quayio/coreos/etcd:v3.5.5
docker tag docker.m.daocloud.io/quayio/coreos/etcd:v3.5.5 quay.io/coreos/etcd:v3.5.5

# 3. 拉取milvus（这步最关键）
docker pull docker.m.daocloud.io/milvusdb/milvus:v2.6.14
docker tag docker.m.daocloud.io/milvusdb/milvus:v2.6.14 milvusdb/milvus:v2.6.14

# 启动
cd ~
docker compose -f milvus-standalone-docker-compose.yml up -d

# 停止
docker compose -f milvus-standalone-docker-compose.yml down

# 重启
docker compose -f milvus-standalone-docker-compose.yml restart

# 状态
docker compose -f milvus-standalone-docker-compose.yml ps

# 日志
docker compose -f milvus-standalone-docker-compose.yml logs -f

# 测试
curl http://localhost:9091/healthz
```
### 1.5 IMKQAS App（Docker Compose）

第一步：停止所有服务
```bash
# 1. 停止后端服务
pkill -f "IMKQAS-1.0-SNAPSHOT.jar"

# 2. 停止前端开发服务器（如果有）
pkill -f "vite"

# 3. 确认所有服务已停止
ps aux | grep -E "IMKQAS-1.0-SNAPSHOT.jar|vite" | grep -v grep
```

第二步：备份当前版本（可选但推荐）
```bash
# 备份当前运行的 JAR 包
cd ~/IMKQAS
mkdir -p backups
cp target/IMKQAS-1.0-SNAPSHOT.jar backups/IMKQAS-$(date +%Y%m%d_%H%M%S).jar

# 备份前端构建产物（如果有）
cp -r frontend/dist backups/dist-$(date +%Y%m%d_%H%M%S) 2>/dev/null
```

第三步：拉取最新代码
```bash
cd ~/IMKQAS

# 1. 查看当前分支
git branch

# 2. 放弃本地修改（如果有冲突）
git reset --hard HEAD

# 3. 拉取最新代码
git pull

# 4. 查看更新内容
git log --oneline -5
```


第四步：重新构建后端
```bash
cd ~/IMKQAS

# 1. 清理旧的构建产物
mvn clean

# 2. 重新构建（跳过测试）
mvn clean package -DskipTests

# 3. 确认构建成功
ls -lh target/IMKQAS-1.0-SNAPSHOT.jar

```

第五步：重新构建前端
```bash
cd ~/IMKQAS/frontend

# 1. 清理旧的依赖和构建产物
rm -rf node_modules package-lock.json dist

# 2. 重新安装依赖
npm install --registry=https://registry.npmmirror.com

# 3. 构建前端（生产环境）
npm run build

# 4. 确认构建成功
ls -lh dist/
```

第六步：启动后端服务
```bash
cd ~/IMKQAS

# 1. 创建日志目录
mkdir -p logs

# 2. 启动后端（后台运行）
nohup java -jar target/IMKQAS-1.0-SNAPSHOT.jar > logs/backend.log 2>&1 &

# 3. 等待几秒让服务启动
sleep 5

# 4. 查看进程
ps aux | grep "IMKQAS-1.0-SNAPSHOT.jar" | grep -v grep

# 5. 查看启动日志
tail -30 logs/backend.log

# 6. 测试后端健康
curl http://localhost:8080/api/actuator/health
```

第七步：启动前端开发服务器（或配置Nginx）
方案A：启动开发服务器（用于调试）
bash
cd ~/IMKQAS/frontend

# 启动开发服务器
nohup npm run dev -- --host > dev.log 2>&1 &

# 查看日志
tail -f dev.log

方案B：配置 Nginx 提供静态文件（生产环境推荐）
bash
# 1. 确认 Nginx 配置
sudo tee /etc/nginx/conf.d/imkqas.conf <<-'EOF'
server {
listen 80;
server_name _;

    root /home/admin/IMKQAS/frontend/dist;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
EOF

# 2. 测试配置
sudo nginx -t

# 3. 重启 Nginx
sudo systemctl restart nginx

# 4. 测试访问
curl -I http://localhost
第八步：验证完整部署
bash
#!/bin/bash
echo "========== 完整部署验证 =========="

# 后端
echo -n "后端服务: "
if ps aux | grep -q "[I]MKQAS-1.0-SNAPSHOT.jar"; then
echo "✅ 运行中"
else
echo "❌ 未运行"
fi

echo -n "后端响应: "
if curl -s http://localhost:8080/api/actuator/health | grep -q "403\|200"; then
echo "✅ 正常"
else
echo "❌ 异常"
fi

# 前端
echo -n "前端服务: "
if ps aux | grep -q "[v]ite"; then
echo "✅ 开发服务器运行中 (端口5173)"
elif systemctl is-active --quiet nginx; then
echo "✅ Nginx 运行中 (端口80)"
else
echo "❌ 未运行"
fi

echo -n "前端访问: "
if curl -s -o /dev/null -w "%{http_code}" http://localhost | grep -q "200"; then
echo "✅ 可访问 (端口80)"
elif curl -s -o /dev/null -w "%{http_code}" http://localhost:5173 | grep -q "200"; then
echo "✅ 可访问 (端口5173)"
else
echo "❌ 不可访问"
fi

echo ""
echo "访问地址："
echo "  开发服务器: http://$(hostname -I | awk '{print $1}'):5173"
echo "  生产环境: http://$(hostname -I | awk '{print $1}')"

