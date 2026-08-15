package com.imkqas.controller;

import com.imkqas.entity.User;
import com.imkqas.service.common.UserService;
import com.imkqas.service.his.FhirPatientService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imkqas.dto.user.HealthProfileRequest;
import com.imkqas.dto.user.IdentityRequest;
import com.imkqas.dto.user.IdentityResponse;
import com.imkqas.exception.BusinessException;
import com.imkqas.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.List;

/**
 * 用户控制器
 * 提供用户相关的RESTful API接口
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;
    private final FhirPatientService fhirPatientService;
    // Spring 管理的 ObjectMapper（带 JavaTimeModule），用于序列化 LocalDate 等身份字段；
    // 不能用 new ObjectMapper()（无 JavaTimeModule 会抛 InvalidDefinitionException）
    private final ObjectMapper objectMapper;

    /**
     * 创建用户（仅管理员可操作）
     * @param entity 用户实体
     * @return 创建的用户
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public User create(@RequestBody User entity) {
        service.save(entity);
        return entity;
    }

    /**
     * 更新用户（仅管理员可操作）
     *
     * 白名单部分更新：仅接受 username/phone/role/password，绝不清空健康档案与身份 JSON。
     * healthProfile/identity 字段为 FieldStrategy.IGNORED，整实体覆盖（updateById(entity)）
     * 会把请求体缺失的这两列置空——管理员编辑一次账号即丢失患者档案。因此改为
     * 「查询现有用户 → 白名单字段合并 → updateById(existing)」，existing 携带完整原值。
     *
     * @param id     用户ID
     * @param entity 用户实体（仅 username/phone/role/password 生效，password 留空不修改）
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public User update(@PathVariable Long id, @RequestBody User entity) {
        User existing = service.getById(id);
        if (existing == null) {
            throw new BusinessException("用户不存在: " + id);
        }
        String oldPhone = existing.getPhone();
        // 白名单合并：仅允许管理员修改以下账号字段
        if (entity.getUsername() != null && !entity.getUsername().isBlank()) {
            existing.setUsername(entity.getUsername());
        }
        if (entity.getPhone() != null && !entity.getPhone().isBlank()) {
            existing.setPhone(entity.getPhone());
        }
        if (entity.getRole() != null) {
            existing.setRole(entity.getRole());
        }
        // 密码留空（null 或空串）则不修改，保留旧密码
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            existing.setPassword(entity.getPassword());
        }
        service.updateById(existing);

        // 手机号变更时同步 FHIR 患者缓存，保证医生端按新手机号可检索到患者
        if (entity.getPhone() != null && !entity.getPhone().isBlank()
                && !entity.getPhone().equals(oldPhone)) {
            try {
                fhirPatientService.updatePhone(id, entity.getPhone());
            } catch (Exception e) {
                // 缓存同步失败不影响账号保存主流程，仅记录错误日志
                log.error("同步FHIR患者缓存手机号失败: userId={}", id, e);
            }
        }
        return existing;
    }

    /**
     * 删除用户（仅管理员可操作）
     * @param id 用户ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        service.removeById(id);
    }

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户实体
     */
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return service.getById(id);
    }

    /**
     * 分页查询用户列表
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<ApiResponse.Pagination<List<User>>> list(@RequestParam(defaultValue = "1") int current,
                                                                @RequestParam(defaultValue = "10") int size) {
        Page<User> page = new Page<>(current, size);
        Page<User> result = service.page(page);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 搜索用户
     * @param keyword 搜索关键词
     * @param current 当前页码，默认1
     * @param size 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/search")
    public ApiResponse<ApiResponse.Pagination<List<User>>> search(@RequestParam(required = false) String keyword,
                                                                  @RequestParam(defaultValue = "1") int current,
                                                                  @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("username", keyword)
                   .or().like("phone", keyword);
        }
        Page<User> result = service.page(new Page<>(current, size), wrapper);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /**
     * 更新用户健康档案
     *
     * 人口学字段（姓名/性别/年龄）以个人中心（identity）为准：请求缺省时用 identity 兜底，
     * 保证健康档案 JSON 与身份信息一致，医生端展示姓名/性别/年龄不矛盾（联动后健康档案
     * 仅维护病史，姓名/性别/年龄由个人中心自动回显）。
     *
     * @param userId  用户ID
     * @param request 健康档案请求（人口学字段可选）
     * @return 更新结果
     */
    @PutMapping("/{userId}/health-profile")
    public ResponseEntity<?> updateHealthProfile(
            @PathVariable Long userId,
            @Valid @RequestBody HealthProfileRequest request) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // 人口学字段三级兜底：请求值 > identity（个人中心） > 原健康档案，
        // 保证姓名/性别/年龄始终与个人中心一致，且不丢失历史档案中的既有值
        // （如 identity 未填出生日期时，保留原档案手填的年龄）。
        IdentityResponse identity = parseIdentity(user.getIdentity());
        Map<String, Object> oldProfile = parseHealthProfile(user.getHealthProfile());
        if (!StringUtils.hasText(request.getName())) {
            String fallback = identity != null ? identity.getName() : null;
            if (!StringUtils.hasText(fallback) && oldProfile != null && oldProfile.get("name") != null) {
                fallback = String.valueOf(oldProfile.get("name"));
            }
            request.setName(fallback);
        }
        if (!StringUtils.hasText(request.getGender())) {
            String fallback = identity != null ? identity.getGender() : null;
            if (!StringUtils.hasText(fallback) && oldProfile != null && oldProfile.get("gender") != null) {
                fallback = String.valueOf(oldProfile.get("gender"));
            }
            request.setGender(fallback);
        }
        if (request.getAge() == null) {
            Integer age = null;
            if (identity != null && identity.getBirthDate() != null) {
                // 年龄由出生日期计算，避免与个人中心展示的年龄矛盾
                age = Period.between(identity.getBirthDate(), LocalDate.now()).getYears();
            } else if (oldProfile != null && oldProfile.get("age") instanceof Number number) {
                age = number.intValue();
            }
            request.setAge(age);
        }

        // 将健康档案转换为JSON字符串。
        // 健康档案字段均为 String/Integer/List<String>（无 LocalDate），用独立 ObjectMapper 即可，
        // 且兼容无 Spring ObjectMapper 注入的测试环境。
        ObjectMapper mapper = new ObjectMapper();
        try {
            String healthProfileJson = mapper.writeValueAsString(request);
            user.updateHealthProfile(healthProfileJson);
            service.updateById(user);

            // 同步创建/更新 FHIR 患者缓存，供医生端按姓名检索患者；
            // 姓名/性别兜底后仍缺失时跳过同步，避免建立全空缓存行
            if (StringUtils.hasText(request.getName()) || StringUtils.hasText(request.getGender())) {
                try {
                    fhirPatientService.upsertLocalPatient(
                            userId, user.getPhone(), request.getName(), request.getGender());
                } catch (Exception e) {
                    // 患者缓存同步失败不影响健康档案保存主流程，仅记录错误日志
                    log.error("同步FHIR患者缓存失败: userId={}, name={}", userId, request.getName(), e);
                }
            }

            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "健康档案更新成功"
            ));
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "健康档案格式错误"
            ));
        }
    }

    /**
     * 解析健康档案 JSON 为 Map；为空或解析失败时返回 null。
     *
     * @param healthProfileJson users.health_profile JSON
     * @return 健康档案字段映射；不可用时返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseHealthProfile(String healthProfileJson) {
        if (healthProfileJson == null || healthProfileJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(healthProfileJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("健康档案解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析用户身份 JSON 为 IdentityResponse；为空或解析失败时返回 null。
     *
     * @param identityJson users.identity JSON
     * @return 身份信息；不可用时返回 null
     */
    private IdentityResponse parseIdentity(String identityJson) {
        if (identityJson == null || identityJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(identityJson, IdentityResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("身份信息解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取用户健康档案
     * @param userId 用户ID
     * @return 健康档案
     */
    @GetMapping("/{userId}/health-profile")
    public ResponseEntity<?> getHealthProfile(@PathVariable Long userId) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        if (user.getHealthProfile() == null || user.getHealthProfile().isEmpty()) {
            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", false,
                "message", "用户未设置健康档案"
            ));
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Object healthProfile = mapper.readValue(user.getHealthProfile(), Object.class);

            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", true,
                "healthProfile", healthProfile
            ));
        } catch (JsonProcessingException e) {
            return ResponseEntity.ok().body(Map.of(
                "hasHealthProfile", false,
                "message", "健康档案解析失败"
            ));
        }
    }

    /**
     * 删除用户健康档案
     * @param userId 用户ID
     * @return 删除结果
     */
    @DeleteMapping("/{userId}/health-profile")
    public ResponseEntity<?> deleteHealthProfile(@PathVariable Long userId) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.updateHealthProfile(null);
        service.updateById(user);

        return ResponseEntity.ok().body(Map.of(
            "success", true,
            "message", "健康档案删除成功"
        ));
    }

    /**
     * 获取用户个人身份信息
     * @param userId 用户ID
     * @return 身份信息（含只读 phone）
     */
    @GetMapping("/{userId}/identity")
    public ResponseEntity<?> getIdentity(@PathVariable Long userId) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        if (user.getIdentity() == null || user.getIdentity().isEmpty()) {
            return ResponseEntity.ok().body(Map.of(
                "hasIdentity", false,
                "message", "用户未设置身份信息"
            ));
        }
        try {
            IdentityResponse res = objectMapper.readValue(user.getIdentity(), IdentityResponse.class);
            res.setPhone(user.getPhone()); // phone 只读回填，取自 users.phone
            return ResponseEntity.ok().body(Map.of(
                "hasIdentity", true,
                "identity", res
            ));
        } catch (JsonProcessingException e) {
            log.error("身份信息解析失败: userId={}", userId, e);
            return ResponseEntity.ok().body(Map.of(
                "hasIdentity", false,
                "message", "身份信息解析失败"
            ));
        }
    }

    /**
     * 保存用户个人身份信息并同步 FHIR 患者缓存
     * @param userId 用户ID
     * @param request 身份信息请求（不含 phone）
     * @return 保存结果
     */
    @PutMapping("/{userId}/identity")
    public ResponseEntity<?> updateIdentity(@PathVariable Long userId,
                                            @Valid @RequestBody IdentityRequest request) {
        User user = service.getById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            IdentityResponse res = new IdentityResponse();
            res.setName(request.getName());
            res.setGender(request.getGender());
            res.setBirthDate(request.getBirthDate());
            res.setIdCard(request.getIdCard());
            res.setAddress(request.getAddress());
            res.setPhone(user.getPhone());
            user.setIdentity(objectMapper.writeValueAsString(res));
            service.updateById(user);
            // 同步 FHIR 患者缓存；证件号冲突抛 BusinessException，交由全局处理器返回 400
            fhirPatientService.upsertLocalPatient(userId, user.getPhone(), request.getName(),
                    request.getGender(), request.getBirthDate(), request.getIdCard(), request.getAddress());
            return ResponseEntity.ok().body(Map.of(
                "success", true,
                "message", "身份信息保存成功"
            ));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存身份信息失败: userId={}", userId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "保存失败，请稍后重试"
            ));
        }
    }
}