# THUOCL医学词库下载脚本

本目录包含用于自动下载和更新THUOCL（清华大学开放中文词库）医学词库的脚本。

## 脚本说明

### 1. Python脚本 (`download_thuocl_medical.py`)

**功能特性：**
- 支持断点续传和错误重试（最多3次）
- 自动计算MD5哈希，仅当文件变化时更新
- 支持ZIP文件自动解压
- 详细的日志输出和进度显示
- 文件预览功能（显示前5行）
- 自动备份原文件

**依赖要求：**
- Python 3.6+
- 标准库（无需额外安装）

**使用方法：**
```bash
# 基本用法（使用默认URL）
python download_thuocl_medical.py

# 指定下载URL和目标目录
python download_thuocl_medical.py --url "https://example.com/medical.zip" --target-dir "/data/dict"

# 详细输出模式
python download_thuocl_medical.py --verbose

# 使用环境变量配置
export THUOCL_DOWNLOAD_URL="https://example.com/medical.txt"
export THUOCL_TARGET_DIR="/data/dict"
export THUOCL_FILENAME="medical_dict.txt"
python download_thuocl_medical.py
```

### 2. Shell脚本 (`download_thuocl_medical.sh`)

**功能特性：**
- 纯Bash实现，无需Python环境
- 支持curl和wget两种下载工具
- 自动检测和安装依赖
- 颜色化日志输出
- 错误处理和重试机制

**依赖要求：**
- Bash 4.0+
- curl 或 wget
- md5sum
- unzip
- 标准Unix工具（awk, sed, grep等）

**使用方法：**
```bash
# 添加执行权限
chmod +x download_thuocl_medical.sh

# 基本用法
./download_thuocl_medical.sh

# 指定参数
./download_thuocl_medical.sh --url "https://example.com/medical.zip" --target-dir "/data/dict"

# 详细输出
./download_thuocl_medical.sh --verbose
```

## THUOCL医学词库信息

### 词库详情
- **名称**：THUOCL医学词库（THUOCL Medical）
- **词条数量**：18,749条
- **格式**：TSV（制表符分隔值）
- **更新日期**：2017年1月20日
- **授权**：开放许可

### 下载URL配置

**默认URL：** `https://thunlp.oss-cn-qingdao.aliyuncs.com/THUOCL_medical.txt`

**备用URL（如默认URL不可用）：**
1. 开放知识图谱平台：http://data.openkg.cn/de/dataset/thuocl
2. 医学词库专属页面：http://59.110.85.131/dataset/thuocl/resource/6fb08a68-9f07-4e43-b607-26e39fdb5d74

**获取最新URL的步骤：**
1. 访问 http://data.openkg.cn/de/dataset/thuocl
2. 找到"医学"词库部分
3. 点击下载链接获取TSV文件
4. 将下载链接配置到脚本中

## 与IMKQAS系统集成

### 配置文件
在Spring Boot的`application.yml`中配置：
```yaml
imkqas:
  query-rewrite:
    thuocl-dict-path: /data/dict/medical_dict.txt
    synonyms-file: classpath:synonyms.txt
    stopwords-file: classpath:stopwords.txt
    enable-spell-checker: true
```

### 定时任务配置
在QueryRewriteServiceImpl中已实现热加载机制：
```java
@Scheduled(fixedDelay = 3600000) // 每小时检查一次
public void reloadMedicalTerms() {
    // 自动重新加载THUOCL词库
}
```

### 手动更新词库
```bash
# 使用Python脚本
cd scripts
python download_thuocl_medical.py --target-dir "/data/dict"

# 或使用Shell脚本
cd scripts
./download_thuocl_medical.sh --target-dir "/data/dict"
```

## 目录结构
```
/data/dict/
├── medical_dict.txt      # THUOCL医学词库（主文件）
├── medical_dict.md5      # MD5哈希缓存
└── medical_dict.txt.bak.* # 自动备份文件
```

## 错误处理

### 常见问题及解决方案

1. **下载失败**
   - 检查网络连接
   - 验证URL是否可访问
   - 尝试使用备用URL

2. **MD5校验失败**
   - 删除`medical_dict.md5`文件强制重新下载
   - 检查文件是否完整下载

3. **权限问题**
   - 确保对`/data/dict`目录有读写权限
   - 使用sudo运行脚本（如需要）

4. **依赖缺失**
   - Python脚本：确保Python 3.6+已安装
   - Shell脚本：安装缺失的命令（curl/wget, md5sum, unzip）

### 调试模式
```bash
# Python脚本
python download_thuocl_medical.py --verbose

# Shell脚本
./download_thuocl_medical.sh --verbose
```

## 自动化部署

### Linux定时任务（Cron）
```bash
# 编辑crontab
crontab -e

# 每天凌晨2点自动更新
0 2 * * * cd /path/to/IMKQAS/scripts && python download_thuocl_medical.py

# 或使用Shell脚本
0 2 * * * cd /path/to/IMKQAS/scripts && ./download_thuocl_medical.sh
```

### Docker集成
在Dockerfile中添加：
```dockerfile
# 下载THUOCL词库
RUN mkdir -p /data/dict && \
    cd /tmp && \
    curl -L -o thuocl_medical.txt "https://thunlp.oss-cn-qingdao.aliyuncs.com/THUOCL_medical.txt" && \
    mv thuocl_medical.txt /data/dict/medical_dict.txt
```

## 性能优化建议

1. **缓存策略**
   - 脚本已实现MD5缓存，避免重复下载
   - 建议每小时检查一次更新

2. **网络优化**
   - 使用国内镜像源（如阿里云OSS）
   - 设置合适的超时时间

3. **存储优化**
   - 定期清理备份文件（保留最近3个）
   - 监控磁盘空间使用情况

## 安全注意事项

1. **URL验证**
   - 只从可信源下载文件
   - 验证文件完整性（MD5校验）

2. **权限控制**
   - 限制对`/data/dict`目录的访问权限
   - 使用非root用户运行脚本

3. **日志监控**
   - 定期检查脚本运行日志
   - 监控下载失败情况

## 联系支持

如遇到问题，请：
1. 检查日志文件获取详细错误信息
2. 验证网络连接和URL可访问性
3. 确保所有依赖已正确安装
4. 参考IMKQAS项目文档

---

*最后更新：2026年4月23日*
*版本：1.0*