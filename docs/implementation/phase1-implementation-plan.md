# IMKQAS医疗知识问答系统 - 阶段1基础框架建设计划

## 上下文
**项目**: IMKQAS（医疗知识智能问答系统）
**阶段**: 阶段1 - 基础框架（2周）
**目标**: 完成SpringBoot项目、数据库、基础API的基础框架建设，为后续RAG核心功能开发提供稳定、安全、可维护的技术平台。

## 假设与约束
- **团队规模**: 单人开发，任务需串行执行
- **代码策略**: 部分重构，在现有代码基础上优化设计
- **数据库迁移**: 选用Flyway
- **前后端**: 前后端分离架构，阶段1主要完成后端基础框架，同时规划前端技术栈

## 当前项目状态分析
### 已完成的组件
1. **基础结构**: Spring Boot 3.2.5 + MyBatis Plus 3.5.7 + MySQL
2. **实体类**: User, Document, DocumentChunk, Conversation, Message（包含完整MyBatis Plus注解）
3. **数据访问层**: Mapper接口（继承BaseMapper<T>）
4. **业务逻辑层**: Service接口与实现
5. **控制层**: Controller基础CRUD
6. **配置类**: MyBatisPlusConfig、MyMetaObjectHandler
7. **响应格式**: ApiResponse统一响应类

### 缺失的基础框架组件
1. 数据库迁移工具（Flyway/Liquibase）
2. 认证授权系统（JWT + Spring Security）
3. 统一异常处理
4. 日志配置优化
5. 单元测试框架
6. API文档（Swagger/OpenAPI）
7. 健康检查端点
8. 配置优化（多环境配置）
9. 代码规范检查
10. 缓存配置（Redis集成）

## 2周详细工作计划（10个工作日）

### 第1周：基础框架搭建（5天）

#### 第1天：项目初始化与代码审查
- **任务**: 项目结构优化、现有代码审查与开发环境配置
- **详细描述**:
  - 审查现有实体类、Mapper、Service、Controller代码，识别需要重构的部分
  - 完善项目目录结构（config, utils, aspect, handler, common等包）
  - 配置多环境application-{dev,test,prod}.yml
  - 添加.gitignore优化配置，排除敏感文件
  - 配置Maven多环境profile和插件
  - 集成Lombok配置检查
- **预计耗时**: 1天
- **交付物**:
  - 代码审查报告和重构清单
  - 完善的项目目录结构
  - 多环境配置文件
  - 优化的.gitignore
  - Maven profile配置
- **验收标准**:
  - 识别出需要重构的代码模块
  - 项目能在dev/test/prod环境正确启动
  - 配置文件按环境分离，敏感信息不提交到仓库

#### 第2天：数据库迁移与数据初始化
- **任务**: 数据库版本控制与初始化脚本
- **详细描述**:
  - 集成Flyway数据库迁移工具
  - 创建V1__init_schema.sql基础表结构
  - 创建V2__init_data.sql初始化数据
  - 配置Flyway多环境策略
  - 添加数据库连接池监控
- **预计耗时**: 1天
- **前置依赖**: 第1天任务完成
- **交付物**:
  - Flyway集成配置
  - 数据库迁移脚本
  - 初始化数据脚本
  - 数据库监控配置
- **验收标准**:
  - 应用启动时自动执行数据库迁移
  - 支持回滚和版本控制
  - 初始化数据正确插入

#### 第3天：统一异常处理与响应格式
- **任务**: 全局异常处理与API标准化
- **详细描述**:
  - 创建GlobalExceptionHandler统一异常处理
  - 定义业务异常类体系（BaseException, BusinessException, ValidationException等）
  - 优化ApiResponse类，支持错误码
  - 创建统一响应拦截器ResponseAdvice
  - 集成Validation参数校验
- **预计耗时**: 1天
- **前置依赖**: 第1天任务完成
- **交付物**:
  - 全局异常处理器
  - 业务异常类体系
  - 增强的ApiResponse
  - 响应拦截器
  - 参数校验配置
- **验收标准**:
  - 所有API返回统一格式
  - 异常信息友好且安全
  - 参数校验自动生效

#### 第4天：认证授权系统实现
- **任务**: JWT认证与权限控制
- **详细描述**:
  - 集成Spring Security 6.x
  - 实现JWT Token生成与验证
  - 创建SecurityConfig安全配置
  - 实现UserDetailsService
  - 添加权限注解支持（@PreAuthorize）
  - 创建登录/注册API
- **预计耗时**: 1天
- **前置依赖**: 第3天任务完成
- **交付物**:
  - Spring Security配置
  - JWT工具类
  - 认证过滤器
  - 权限控制配置
  - 登录注册API
- **验收标准**:
  - 支持JWT认证
  - 接口权限控制生效
  - 密码加密存储

#### 第5天：日志系统与监控配置
- **任务**: 日志优化与应用监控
- **详细描述**:
  - 配置Logback日志框架
  - 实现日志切面（AOP）
  - 集成Spring Boot Actuator
  - 添加健康检查端点
  - 配置指标监控（Prometheus）
  - 添加应用信息端点
- **预计耗时**: 1天
- **前置依赖**: 第4天任务完成
- **交付物**:
  - Logback配置文件
  - 日志切面
  - Actuator配置
  - 健康检查端点
  - 监控指标配置
- **验收标准**:
  - 日志按级别和包名分类
  - 接口调用日志完整
  - 监控端点可访问

### 第2周：质量保障与优化（5天）

#### 第6天：API文档与接口测试
- **任务**: API文档生成与接口测试
- **详细描述**:
  - 集成SpringDoc OpenAPI 3.0
  - 配置Swagger UI
  - 为所有Controller添加API注解
  - 创建Postman/Insomnia接口测试集合
  - 添加接口测试示例
- **预计耗时**: 1天
- **前置依赖**: 第5天任务完成
- **交付物**:
  - SpringDoc配置
  - Swagger UI文档
  - 接口测试集合
  - API注解完善
- **验收标准**:
  - Swagger UI可访问
  - API文档完整准确
  - 接口测试通过

#### 第7天：单元测试与集成测试
- **任务**: 测试框架建设
- **详细描述**:
  - 配置JUnit 5 + Mockito
  - 创建测试基类（TestBase）
  - 编写Service层单元测试
  - 编写Controller层集成测试
  - 添加测试覆盖率配置（Jacoco）
  - 创建测试数据工厂
- **预计耗时**: 1天
- **前置依赖**: 第6天任务完成
- **交付物**:
  - 测试框架配置
  - Service单元测试
  - Controller集成测试
  - 测试覆盖率报告
  - 测试数据工厂
- **验收标准**:
  - 核心业务逻辑测试覆盖
  - 测试通过率100%
  - 测试覆盖率>70%

#### 第8天：缓存配置与Redis集成
- **任务**: Redis基础集成与缓存配置
- **详细描述**:
  - 集成Spring Data Redis依赖
  - 配置Redis连接（单机模式，支持后续集群扩展）
  - 配置RedisTemplate和序列化方式
  - 实现简单的缓存管理器
  - 添加缓存健康检查
  - 配置连接池参数优化
- **预计耗时**: 1天
- **前置依赖**: 第7天任务完成
- **交付物**:
  - Redis配置和连接
  - RedisTemplate配置
  - 缓存管理器基础实现
  - 缓存健康检查端点
- **验收标准**:
  - Redis连接正常
  - 缓存基本功能可用
  - 健康检查端点显示Redis状态

#### 第9天：代码规范与质量保障
- **任务**: 代码规范检查与质量保障
- **详细描述**:
  - 集成Checkstyle代码规范检查，配置中文编码规范
  - 配置Maven插件，支持代码规范检查
  - 添加Git预提交钩子，自动运行代码检查
  - 创建代码审查清单，指导后续开发
  - 运行静态分析，修复严重问题
- **预计耗时**: 1天
- **前置依赖**: 第8天任务完成
- **交付物**:
  - Checkstyle配置文件和规则
  - Maven插件配置
  - Git预提交钩子脚本
  - 代码审查清单
- **验收标准**:
  - 代码规范检查通过
  - 无严重静态分析问题
  - 提交前自动运行代码检查

#### 第10天：部署配置、文档完善与前端规划
- **任务**: 部署准备、项目文档与前端技术栈规划
- **详细描述**:
  - 创建Dockerfile多阶段构建
  - 编写docker-compose.yml（包含MySQL、Redis等依赖服务）
  - 创建部署脚本（deploy.sh）
  - 完善项目README.md，包含项目结构、启动指南
  - 编写API使用文档，基于Swagger UI
  - 创建开发指南，涵盖编码规范和开发流程
  - 规划前端技术栈：Vue3 + Element Plus + TypeScript + Vite
  - 阶段1总结与验收
- **预计耗时**: 1天
- **前置依赖**: 第9天任务完成
- **交付物**:
  - Docker配置
  - 部署脚本
  - 完整项目文档
  - 前端技术栈规划文档
  - 阶段1验收报告
- **验收标准**:
  - 支持Docker部署
  - 文档完整可用
  - 前端技术栈规划明确

## 关键文件路径
- `D:\Java\代码\IMKQAS\pom.xml` - 依赖管理，需添加Flyway、Spring Security、SpringDoc等依赖
- `D:\Java\代码\IMKQAS\src\main\resources\application.yml` - 主配置文件，需拆分为多环境配置
- `D:\Java\代码\IMKQAS\src\main\java\com\student\ImkqasApplication.java` - 应用入口
- `D:\Java\代码\IMKQAS\src\main\java\com\student\dto\ApiResponse.java` - 响应格式，需增强错误码支持
- `D:\Java\代码\IMKQAS\src\main\java\com\student\config\MyBatisPlusConfig.java` - 数据访问配置

## 技术选型
1. **数据库迁移**: Flyway（轻量级，与Spring Boot集成好）
2. **安全认证**: Spring Security 6.x + JWT（jjwt库）
3. **API文档**: SpringDoc OpenAPI 3.0（替代Swagger2）
4. **缓存**: Spring Data Redis + RedisTemplate
5. **测试**: JUnit 5 + Mockito + Testcontainers（集成测试）
6. **监控**: Spring Boot Actuator + Micrometer + Prometheus
7. **代码质量**: Checkstyle + SpotBugs + PMD
8. **日志**: Logback + SLF4J
9. **部署**: Docker + Docker Compose
10. **前端技术栈**: Vue3 + TypeScript + Element Plus + Vite + Axios（规划）

## 单人开发实践建议
- **每日计划与复盘**: 每天开始前规划任务，结束后复盘进度
- **代码自查**: 提交前自我审查，遵循编码规范
- **分支策略**: feature分支开发，main分支保护，定期合并
- **文档同步**: 代码变更时同步更新文档
- **测试驱动**: 关键功能先写测试，再实现功能
- **版本控制**: 小步提交，清晰的提交信息

## 风险评估与缓解措施
| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 数据库迁移失败 | 中 | 高 | 1. 开发环境充分测试 2. 准备回滚脚本 3. 分阶段执行迁移 |
| 安全配置复杂 | 高 | 中 | 1. 参考Spring Security官方示例 2. 分步骤实现 3. 充分测试认证流程 |
| 性能问题 | 低 | 中 | 1. 早期集成性能监控 2. 使用连接池 3. 添加缓存层 |
| 时间不足 | 高 | 高 | 1. 优先完成核心功能（认证、异常处理、API文档） 2. 非核心功能可延后 3. 每日跟踪进度，灵活调整计划 |
| 技术债务积累 | 中 | 中 | 1. 遵循编码规范 2. 及时重构 3. 保持测试覆盖率 |

## 验收标准总结
1. **功能完整性**: 所有基础框架组件可用
2. **代码质量**: 通过代码规范检查，测试覆盖率>70%
3. **文档完整性**: API文档、部署文档、开发指南齐全
4. **可部署性**: 支持Docker部署，多环境配置
5. **安全性**: 认证授权系统完善，无安全漏洞
6. **可维护性**: 代码结构清晰，配置可管理

## 后续步骤
1. 评审本计划，确认技术选型和任务分解
2. 根据团队规模调整并行任务
3. 开始第1天任务实施
4. 每日跟踪进度，及时调整计划