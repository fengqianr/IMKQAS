package com.imkqas.controller.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.imkqas.dto.ApiResponse;
import com.imkqas.entity.User;
import com.imkqas.entity.contraindication.DrugPopulationContraindication;
import com.imkqas.mapper.DrugPopulationContraindicationMapper;
import com.imkqas.service.rag.ContraindicationDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 禁忌规则管理控制器
 * 提供药物-人群禁忌规则的CRUD API（仅管理员可访问）
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/contraindications")
@RequiredArgsConstructor
@Tag(name = "禁忌规则管理", description = "药物-人群禁忌规则增删改查API")
public class ContraindicationController {

    private final DrugPopulationContraindicationMapper ruleMapper;
    private final ContraindicationDetectionService contraindicationDetectionService;

    @GetMapping
    @Operation(summary = "分页查询禁忌规则列表")
    public ApiResponse<ApiResponse.Pagination<List<DrugPopulationContraindication>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String drugName,
            @RequestParam(required = false) String populationName,
            @RequestParam(required = false) String contraindicationType,
            @RequestParam(required = false) Integer isActive) {
        LambdaQueryWrapper<DrugPopulationContraindication> query = new LambdaQueryWrapper<>();
        if (drugName != null && !drugName.isBlank()) {
            query.like(DrugPopulationContraindication::getDrugName, drugName.trim());
        }
        if (populationName != null && !populationName.isBlank()) {
            query.like(DrugPopulationContraindication::getPopulationName, populationName.trim());
        }
        if (contraindicationType != null && !contraindicationType.isBlank()) {
            query.eq(DrugPopulationContraindication::getContraindicationType, contraindicationType.trim());
        }
        if (isActive != null) {
            query.eq(DrugPopulationContraindication::getIsActive, isActive);
        }
        query.orderByDesc(DrugPopulationContraindication::getUpdatedAt);

        Page<DrugPopulationContraindication> result = ruleMapper.selectPage(
                new Page<>(page, size), query);
        return ApiResponse.pagination(result.getRecords(), result.getTotal(), page, size);
    }

    @PostMapping
    @Operation(summary = "新增禁忌规则")
    public ApiResponse<?> create(@RequestBody DrugPopulationContraindication rule) {
        if (rule.getDrugName() == null || rule.getDrugName().isBlank()
                || rule.getPopulationName() == null || rule.getPopulationName().isBlank()) {
            return ApiResponse.error("药物名称和人群名称不能为空");
        }
        if (rule.getContraindicationType() == null) {
            return ApiResponse.error("禁忌类型不能为空");
        }
        rule.setIsActive(rule.getIsActive() != null ? rule.getIsActive() : 1);
        ruleMapper.insert(rule);
        log.info("管理员新增禁忌规则: drug={}, population={}, type={}, reviewer={}",
                rule.getDrugName(), rule.getPopulationName(),
                rule.getContraindicationType(), getCurrentUsername());
        return ApiResponse.success("新增成功", rule);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑禁忌规则")
    public ApiResponse<?> update(@PathVariable Long id, @RequestBody DrugPopulationContraindication rule) {
        DrugPopulationContraindication existing = ruleMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("规则不存在");
        }
        rule.setId(id);
        ruleMapper.updateById(rule);
        log.info("管理员编辑禁忌规则: id={}, reviewer={}", id, getCurrentUsername());
        return ApiResponse.success("更新成功", ruleMapper.selectById(id));
    }

    @PutMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用规则")
    public ApiResponse<Void> toggleActive(@PathVariable Long id) {
        DrugPopulationContraindication rule = ruleMapper.selectById(id);
        if (rule == null) {
            return ApiResponse.error("规则不存在");
        }
        rule.setIsActive(rule.getIsActive() != null && rule.getIsActive() == 1 ? 0 : 1);
        ruleMapper.updateById(rule);
        log.info("管理员切换禁忌规则状态: id={}, isActive={}, reviewer={}",
                id, rule.getIsActive(), getCurrentUsername());
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除禁忌规则（软删除）")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        DrugPopulationContraindication rule = ruleMapper.selectById(id);
        if (rule == null) {
            return ApiResponse.error("规则不存在");
        }
        ruleMapper.deleteById(id);
        log.info("管理员删除禁忌规则: id={}, drug={}, population={}, reviewer={}",
                id, rule.getDrugName(), rule.getPopulationName(), getCurrentUsername());
        return ApiResponse.success();
    }

    @PostMapping("/batch-import")
    @Operation(summary = "批量导入禁忌规则")
    public ApiResponse<?> batchImport(
            @RequestBody List<DrugPopulationContraindication> rules) {
        if (rules == null || rules.isEmpty()) {
            return ApiResponse.error("导入列表不能为空");
        }
        int success = 0;
        int skipped = 0;
        for (DrugPopulationContraindication rule : rules) {
            if (rule.getDrugName() == null || rule.getPopulationName() == null) {
                skipped++;
                continue;
            }
            if (rule.getIsActive() == null) {
                rule.setIsActive(1);
            }
            try {
                ruleMapper.insert(rule);
                success++;
            } catch (Exception e) {
                log.warn("批量导入跳过重复规则: drug={}, population={}",
                        rule.getDrugName(), rule.getPopulationName());
                skipped++;
            }
        }
        log.info("管理员批量导入禁忌规则: 成功={}, 跳过={}, reviewer={}",
                success, skipped, getCurrentUsername());
        return ApiResponse.success("批量导入完成",
                Map.of("total", rules.size(), "success", success, "skipped", skipped));
    }

    @PostMapping("/reload-cache")
    @Operation(summary = "刷新禁忌规则缓存")
    public ApiResponse<Void> reloadCache() {
        contraindicationDetectionService.refreshCache();
        log.info("管理员手动刷新禁忌规则缓存: reviewer={}", getCurrentUsername());
        return ApiResponse.success();
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())
                && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }
}
