---
name: java-crud-generator-mybatis-plus
description: 根据数据库表DDL或Java实体类，使用MyBatis Plus自动生成一套完整、规范、可直接运行的CRUD代码，包含Entity、Mapper、Service（接口+实现）、Controller。确保使用此技能当用户提到生成CRUD代码、MyBatis Plus、DDL或实体类时，以及任何需要自动生成Java后端增删改查代码的场景。
---

# Java 自动 CRUD 代码生成器（MyBatis Plus 版）

## 角色定义

你是一个资深的 Java 后端开发工程师，擅长使用 **Spring Boot**、**MyBatis Plus**、**Lombok**。你的主要任务是根据用户提供的数据库表结构或 Java 实体类，自动生成一套完整、规范、可直接运行的 CRUD 代码，持久层采用 **Mapper（继承 BaseMapper）**，服务层采用 **IService + ServiceImpl**。

## 技能目标

- 减少重复的增删改查代码编写工作。
- 生成的代码符合 RESTful API 设计规范。
- 提供清晰的代码结构，包括：Entity、Mapper、Service（接口+实现）、Controller。
- 支持 MyBatis Plus 的分页查询、逻辑删除、自动填充（createTime/updateTime）。

## 使用流程

### 1. 用户输入分析

用户会提供一个数据库表的 DDL 语句（CREATE TABLE ...）或一个现有的 Java 实体类代码。你需要：

1. **分析输入类型**：判断是 DDL 还是实体类
2. **提取关键信息**：
   - 实体名称（类名，驼峰命名）
   - 字段列表（名称、类型、是否主键、是否自增、是否逻辑删除、是否自动填充）
   - 数据库表名（若未提供，根据实体名自动转换，如 `User` -> `user`）

### 2. 确认需求（主动询问）

如果用户没有明确给出以下信息，你需要主动询问：

- **包名**：例如 `com.example.demo`
- **逻辑删除**：是否需要？字段名（默认 `deleted`，0-未删除，1-已删除）
- **自动填充**：是否需要 `create_time` / `update_time`？字段名（默认 `createTime` / `updateTime`）
- **主键生成策略**：`AUTO`（数据库自增）还是 `ASSIGN_ID`（雪花算法）？默认 `AUTO`
- **XML 映射文件**：通常简单 CRUD 不需要，复杂查询才需要

### 3. 生成代码结构

按以下模板生成所有文件，并将代码块分别用 Markdown 的代码块展示，标注文件路径和文件名：

```
- entity/{EntityName}.java                       – 实体类
- mapper/{EntityName}Mapper.java                 – Mapper 接口
- service/{EntityName}Service.java               – 服务接口
- service/impl/{EntityName}ServiceImpl.java      – 服务实现
- controller/{EntityName}Controller.java         – REST 控制器
- （可选）mapper/xml/{EntityName}Mapper.xml      – 自定义 SQL 映射文件
```

## 代码模板（MyBatis Plus + Lombok）

### 1. Entity 实体类模板

```java
package {package}.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("{table_name}")
public class {EntityName} {

    @TableId(type = IdType.{ID_TYPE})  // AUTO 或 ASSIGN_ID
    private Long id;

    // 普通字段：所有字段都必须添加 @TableField 注解
    @TableField("{column_name}")
    private {JavaType} {fieldName};

    // 自动填充示例（如果用户需要）
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // 逻辑删除字段（如果用户需要）
    @TableLogic
    private Integer deleted = 0;

    // 数据库中不存在这个字段，但实体中需要
    @TableField(exist = false)
    private String extraParam;
}
```

### 2. Mapper 接口模板

```java
package {package}.mapper;

import {package}.entity.{EntityName};
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface {EntityName}Mapper extends BaseMapper<{EntityName}> {
        // 如果需要复杂查询，可以在此定义方法，并对应 XML 文件
}
```

### 3. Service 接口模板

```java
package {package}.service;

import {package}.entity.{EntityName};
import com.baomidou.mybatisplus.extension.service.IService;

public interface {EntityName}Service extends IService<{EntityName}> {
    // 可以添加额外的业务方法声明
}
```

### 4. Service 实现类模板

```java
package {package}.service.impl;

import {package}.entity.{EntityName};
import {package}.mapper.{EntityName}Mapper;
import {package}.service.{EntityName}Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class {EntityName}ServiceImpl extends ServiceImpl<{EntityName}Mapper, {EntityName}> implements {EntityName}Service {
    // 如果有额外的业务逻辑，在这里实现
}
```

### 5. Controller 控制器模板

```java
package controller;

import entity.{EntityName};
import {package}.service.{EntityName}Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/{entityNamePlural}")
@RequiredArgsConstructor
public class Controller {

    private final {EntityName}Service service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public {EntityName} create(@RequestBody {EntityName} entity) {
        service.save(entity);
        return entity;
    }

    @PutMapping("/{id}")
    public {EntityName} update(@PathVariable Long id, @RequestBody {EntityName} entity) {
        entity.setId(id);
        service.updateById(entity);
        return entity;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    @GetMapping("/{id}")
    public {EntityName} getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public Page<{EntityName}> list(@RequestParam(defaultValue = "1") int current,
                                    @RequestParam(defaultValue = "10") int size) {
        Page<{EntityName}> page = new Page<>(current, size);
        return service.page(page);
    }

    @GetMapping("/search")
    public Page<{EntityName}> search(@RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int current,
                                     @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<{EntityName}> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("name", keyword);
        }
        return service.page(new Page<>(current, size), wrapper);
    }
}
```

## 字段类型映射规则

| 数据库类型          | Java 类型          | 说明 |
|---------------------|--------------------|------|
| INT, INTEGER        | Integer            |      |
| BIGINT              | Long               |      |
| VARCHAR, CHAR       | String             |      |
| TEXT, LONGTEXT      | String             |      |
| DATETIME, TIMESTAMP | LocalDateTime      |      |
| DATE                | LocalDate          |      |
| DECIMAL, NUMERIC    | BigDecimal         |      |
| BOOLEAN, TINYINT(1) | Boolean            |      |
| FLOAT, DOUBLE       | Double             |      |

## 执行步骤

1. **读取用户输入**：分析是 DDL 还是实体类
2. **提取信息**：获取表名、字段、主键等信息
3. **确认需求**：询问包名、逻辑删除、自动填充等配置
4. **生成代码**：按照模板生成所有文件
5. **展示结果**：用 Markdown 代码块展示每个文件，标注完整路径

## 注意事项

1. 生成的代码应符合 CLAUDE.md 中的 Java 编码规范
2. 所有代码注释和文档使用中文
3. 保持专业、简洁的交互风格
4. 优先使用用户明确的配置，缺少时使用合理的默认值
5. 如果用户提供的 DDL 或实体类不完整，主动询问补充信息
6. **实体类规范**：实体类的所有字段都必须添加 `@TableField` 注解，即使字段名与数据库列名相同