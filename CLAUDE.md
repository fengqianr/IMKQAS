# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作提供指导。

**重要提示**：请始终使用简体中文与用户对话，并在回答时保持专业、简洁。所有生成的代码注释、文档和交互都应使用中文。

## 项目概述

- **项目类型**：基于 Spring Boot + MyBatis Plus 的 Java Web 项目
- **Java 版本**：21（在 pom.xml 中配置）
- **主类**：`com.student.ImkqasApplication`（Spring Boot 启动类）
- **项目状态**：医疗知识问答系统（IMKQAS），包含用户、文档、对话等完整业务模块

## 常用开发任务

### 构建和运行

- **编译项目**：`mvn compile`
- **运行测试**：`mvn test`（目前没有测试）。运行单个测试：`mvn test -Dtest=测试类名`
- **打包项目**：`mvn package`（在 `target/` 目录生成 JAR 文件）
- **运行主类**：`mvn exec:java -Dexec.mainClass="com.student.Main"`
- **清理构建**：`mvn clean`
- **跳过测试**：`mvn clean install -DskipTests`

### 项目结构

```
IMKQAS/
├── pom.xml                    # Maven 项目配置文件
├── src/
│   ├── main/
│   │   ├── java/             # Java 源代码目录
│   │   │   └── com/student/  # 包目录结构
│   │   │       └── Main.java # 主类文件
│   │   └── resources/        # 资源文件目录（如配置文件）
│   └── test/
│       ├── java/             # 测试代码目录
│       └── resources/        # 测试资源文件
└── target/                   # 构建输出目录（被 .gitignore 忽略）
```

### 添加依赖

编辑 `pom.xml`，在 `<dependencies>` 部分添加依赖项。示例：

```xml
<dependencies>
  <dependency>
    <groupId>org.example</groupId>
    <artifactId>library</artifactId>
    <version>1.0</version>
  </dependency>
</dependencies>
```

### IDE 集成

- 项目包含 IntelliJ IDEA 配置（`.idea/` 目录）
- 如果需要重新生成 IDEA 项目文件：`mvn idea:idea`
- VS Code 用户可以打开文件夹作为 Maven 项目

## 数据访问

### MyBatis Plus 配置

1. **实体类注解**：使用 MyBatis Plus 注解，如 `@TableName`、`@TableId`、`@TableField`、`@TableLogic` 等
2. **Mapper 接口**：继承 `BaseMapper<T>`，使用 `@Mapper` 注解
3. **Service 层**：继承 `IService<T>` 和 `ServiceImpl<M, T>`
4. **自动填充**：配置 `MetaObjectHandler` 处理 `createTime` 和 `updateTime` 字段
5. **逻辑删除**：使用 `@TableLogic` 注解，默认值 `0` 表示未删除，`1` 表示已删除
6. **分页插件**：已配置 `MybatisPlusInterceptor` 支持分页查询

### 数据库配置

在 `application.yml` 中配置数据源连接信息，当前使用 MySQL 数据库。

## Java 编码规范

### 命名约定

1. **包名**：全小写，使用逆域名格式（如 `com.student.utils`）
2. **类名**：大驼峰命名法（如 `StudentService`、`UserController`）
3. **接口名**：大驼峰命名法，通常以 `able`、`er` 结尾（如 `Runnable`、`Formatter`）
4. **方法名**：小驼峰命名法（如 `getStudentInfo`、`saveUserData`）
5. **变量名**：小驼峰命名法（如 `studentName`、`userCount`）
6. **常量名**：全大写，单词间用下划线分隔（如 `MAX_SIZE`、`DEFAULT_TIMEOUT`）

### 编码规则

1. **文件编码**：使用 UTF-8 编码
2. **缩进**：使用 4 个空格，不要使用 Tab
3. **行宽**：建议不超过 120 个字符
4. **大括号**：使用 K&R 风格（左大括号不换行）
   ```java
   public void method() {
       // 代码
   }
   ```
5. **MyBatis Plus 注解**：
   - 所有实体类字段必须添加 `@TableField` 注解，即使字段名与数据库列名相同
   - 主键字段使用 `@TableId` 注解
   - 逻辑删除字段使用 `@TableLogic` 注解
   - 自动填充字段使用 `@TableField(fill = FieldFill.*)` 注解
6. **导入顺序**：按以下顺序分组，每组间空一行：
   - 静态导入
   - Java 标准库导入
   - 第三方库导入
   - 项目内部导入

### 注释规范

1. **类注释**：每个类应该有 Javadoc 注释
   ```java
   /**
    * 学生信息管理类
    * 提供学生信息的增删改查功能
    * 
    * @author 作者名
    * @version 1.0
    */
   
   public class StudentService {}
   ```
2. **方法注释**：公共方法应该有 Javadoc 注释，说明参数、返回值和异常
3. **行内注释**：在复杂逻辑处添加中文注释，说明代码意图

### 代码组织

1. **类成员顺序**：
   - 静态常量
   - 静态变量
   - 实例变量
   - 构造函数
   - 公共方法
   - 受保护方法
   - 私有方法
   - 内部类

2. **访问修饰符**：使用最严格的访问级别（优先 private，然后是 protected，最后是 public）

## 开发流程规范

### 每日任务验收流程

为保障项目质量，每次任务结束时必须执行以下验收流程：

1. **编译验证**：
   - 运行 `mvn compile` 确保代码编译通过
   - 检查是否有编译错误、警告或不兼容的API使用
   - 特别关注第三方库集成时的API变更（如Milvus SDK 2.3.6、MinIO SDK 8.5.10）

2. **功能验证**：
   - 验证新实现的代码是否符合任务要求
   - 检查代码逻辑是否正确，边界条件是否处理
   - 确保异常处理和降级机制正常工作

3. **代码质量检查**：
   - 遵循本文件中定义的Java编码规范
   - 检查MyBatis Plus注解使用是否正确
   - 验证实体类字段是否添加 `@TableField` 等必要注解
   - 确保代码注释完整（类注释、方法注释、复杂逻辑行内注释）

4. **验收标准**：
   - 代码必须能够正常编译
   - 实现的功能必须满足任务要求
   - 代码必须符合项目编码规范
   - 第三方库集成必须使用正确版本的API

5. **不满足要求的处理**：
   - 如果编译失败，必须修复所有编译错误
   - 如果功能不完整，必须补充实现直到满足要求
   - 如果代码质量不达标，必须按照规范重构
   - 如果第三方库API使用错误，必须查阅对应版本文档修正

**重要原则**：每个任务必须经过上述验收流程确认合格后才能标记为完成。验收不通过的任务需要继续修改，直至满足所有要求。

## 测试规范

1. **测试类命名**：`被测试类名 + Test`（如 `StudentServiceTest`）
2. **测试方法命名**：`test + 被测试方法名 + 测试场景`（如 `testSaveStudentWithValidData`）
3. **测试结构**：使用 Arrange-Act-Assert 模式
4. **测试位置**：所有测试放在 `src/test/java` 目录下，保持与主代码相同的包结构
5. **测试报告维护**：测试完成或修改后，必须及时更新 `docs/testing-report.md` 文件，保持测试状态和统计信息的准确性

## Spring Boot 3测试配置经验

基于IMKQAS项目测试修复经验，总结以下Spring Boot 3.2.5测试配置最佳实践：

### 1. 控制器测试注解选择

**优先使用 `@WebMvcTest(Controller.class)`**：对于纯控制器测试，优先使用`@WebMvcTest`加载最小上下文，而非`@SpringBootTest`。

**当`@WebMvcTest`不可行时**：如果遇到MyBatis Mapper依赖问题（如"Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"），改用：
```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"spring.security.enabled=false"})
```

### 2. Mockito模拟配置

**Mockito严格模式**：使用`@MockitoSettings(strictness = Strictness.STRICT_STUBS)`避免不必要的存根调用。

**基类模式**：创建`BaseMockitoTest`基类统一配置Mockito扩展：
```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
public abstract class BaseMockitoTest {}
```

### 3. @MockBean使用注意事项

**避免`@Primary`注解**：不要在`@MockBean`上使用`@Primary`注解，会导致编译错误"批注接口不适用于此类型的声明"。

**指定bean名称**：使用`@MockBean(name = "beanName")`显式指定要替换的bean：
```java
@MockBean(name = "triageService")
private TriageService triageService;
```

**模拟验证**：添加`verify(service).method()`调用确认模拟是否生效。

**实际bean替换问题**：当`@MockBean`无法正确替换实际bean时（如来自`@Configuration`类的bean），使用`@TestConfiguration`内部类：
```java
@TestConfiguration
static class TestConfig {
    @Primary
    @Bean
    public TriageService triageService() {
        return Mockito.mock(TriageService.class);
    }
}
```

### 4. 安全过滤器处理

**禁用安全过滤器**：控制器测试中安全过滤器可能干扰请求处理：
- 使用`@AutoConfigureMockMvc(addFilters = false)`禁用所有过滤器
- 或使用`@TestPropertySource(properties = {"spring.security.enabled=false"})`完全禁用安全

**安全组件模拟**：需要模拟JWT相关组件时添加`@MockBean`：
```java
@MockBean
private JwtAuthenticationFilter jwtAuthenticationFilter;

@MockBean
private JwtUtil jwtUtil;

@MockBean
private UserDetailsServiceImpl userDetailsService;
```

### 5. 断言注意事项

**数据模型验证**：验证断言时检查数据模型的实际值：
- `EmergencyLevel.CRITICAL`的描述为"危急"，而非"危重"
- 使用`assertEquals("危急", emergencyLevel.getDescription())`

**批量处理结果**：批量处理方法如`batchAnalyze()`返回结果数量可能与预期不同：
- 验证实际行为而非假设
- 例如：部分失败时仍返回结果列表，但包含降级结果

**JSON路径断言**：使用`MockMvcResultHandlers.print()`调试JSON响应，确保JSON路径存在：
```java
.andDo(MockMvcResultHandlers.print())
.andExpect(jsonPath("$.symptoms").value("发烧咳嗽"))
```

### 6. 异步测试处理

**ExecutorService模拟**：对于异步测试，模拟`ExecutorService`立即执行任务：
```java
doAnswer(invocation -> {
    Runnable task = invocation.getArgument(0);
    task.run();
    return null;
}).when(executorService).execute(any(Runnable.class));
```

**异步超时测试**：测试异步超时场景时，返回未完成的`CompletableFuture`：
```java
when(executorService.submit(any(Callable.class))).thenAnswer(invocation -> {
    return new CompletableFuture<>(); // 永不完成的future
});
```

### 7. 常见错误解决

| 错误 | 原因 | 解决方案 |
|------|------|----------|
| `No value at JSON path "$.symptoms"` | JSON响应为空，控制器未正确处理请求 | 检查模拟服务是否注入成功，添加请求日志 |
| `JwtAuthenticationFilter`依赖未满足 | 安全组件在测试上下文中缺失 | 添加`@MockBean`模拟安全相关组件 |
| `@MockBean`未替换实际bean | 配置类bean优先级高于`@MockBean` | 使用`@TestConfiguration`内部类提供`@Primary` bean |
| `@WebMvcTest`导致Mapper依赖错误 | MyBatis Mapper需要完整Spring上下文 | 改用`@SpringBootTest`并禁用安全 |

### 8. 测试执行验证

**编译验证**：每次测试修改后运行`mvn test`验证所有测试通过。

**单测试执行**：使用`mvn test -Dtest=测试类名`执行特定测试类。

**测试覆盖**：确保新测试覆盖正常、异常和边界场景。

## Git 规范

1. **提交消息**：使用中文提交消息，格式为：`类型: 描述`
   - 类型：feat（新功能）、fix（修复）、docs（文档）、style（格式）、refactor（重构）、test（测试）、chore（构建）
   - 示例：`feat: 添加学生信息查询功能`
2. **忽略文件**：构建输出、IDE 配置、操作系统特定文件已被 `.gitignore` 忽略

## 第三方库集成

### Milvus SDK 2.3.6

项目使用 Milvus SDK 2.3.6 进行向量数据库操作。重要 API 变更和用法：

1. **结果类型**：`InsertResults` 和 `DeleteResults` 已合并为 `MutationResult`
   - 使用 `MutationResultWrapper` 解析插入和删除操作的结果
   - 示例：
     ```java
     R<MutationResult> insertResponse = milvusClient.insert(insertParam);
     MutationResultWrapper wrapper = new MutationResultWrapper(insertResponse.getData());
     List<Long> ids = wrapper.getLongIDs();
     ```

2. **字段构建**：`InsertParam.Field` 使用构造函数而非 `newBuilder()` 模式
   - 旧版：`InsertParam.Field.newBuilder().withName("field").withData(data).build()`
   - 新版：`new InsertParam.Field("field_name", data)`

3. **搜索结果解析**：`SearchResultsWrapper` 构造函数接收 `SearchResults` 而非 `SearchResultData`
   - 示例：`new SearchResultsWrapper(searchResponse.getData().getResults())`

4. **集合统计**：从 `GetCollectionStatisticsResponse` 提取计数需遍历 `statsList`
   - 示例：
     ```java
     for (var stat : countResponse.getData().getStatsList()) {
         if ("row_count".equals(stat.getKey())) {
             count = Long.parseLong(stat.getValue());
             break;
         }
     }
     ```

### MinIO SDK 8.5.10

项目使用 MinIO SDK 8.5.10 进行对象存储操作。重要 API 变更：

1. **参数构建**：从函数式接口改为 Builder 模式
   - 需要导入对应的 Args 类：`BucketExistsArgs`、`MakeBucketArgs`、`SetBucketPolicyArgs`
   - 示例：
     ```java
     // 旧版（函数式接口）：minioClient.bucketExists(args -> args.bucket(bucketName))
     // 新版（Builder模式）：minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())
     ```

2. **方法调用**：`bucketExists()`、`makeBucket()`、`setBucketPolicy()` 等方法都需要使用 Builder 模式构建参数

### 通用指导

1. **版本兼容性**：修改第三方库版本时，务必查阅对应版本的官方文档和 API 变更记录
2. **导入检查**：确保导入正确的类，避免使用已废弃的 API
3. **编译测试**：修改依赖版本后立即运行 `mvn compile` 验证兼容性
4. **错误排查**：遇到编译错误时，优先检查 API 签名是否匹配当前版本

## 注意事项

1. 项目为医疗知识问答系统（IMKQAS），包含完整的用户、文档、对话、消息等业务模块
2. `pom.xml` 已声明 Spring Boot、MyBatis Plus、MySQL、Lombok 等依赖
3. **重要依赖版本**：
   - Milvus SDK: 2.3.6（注意 API 变更，见"第三方库集成"章节）
   - MinIO SDK: 8.5.10（注意 Builder 模式 API，见"第三方库集成"章节）
   - MyBatis Plus: 3.5.7
   - Spring Boot: 3.2.5
   - Java: 21
4. 测试目录存在但当前没有测试文件，需要补充单元测试
5. 所有生成的代码都应包含中文注释和文档
6. 与用户交互时使用专业、简洁的中文
7. 数据访问使用 MyBatis Plus，不再使用 JPA/jakarta 相关注解