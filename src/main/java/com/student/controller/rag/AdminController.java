package com.student.controller.rag;

import com.student.dto.ApiResponse;
import com.student.dto.rag.AdminStatsVO;
import com.student.dto.rag.ApproveRequest;
import com.student.dto.rag.UnmappedTermVO;
import com.student.entity.User;
import com.student.service.rag.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员审核控制器
 * 提供未映射词条审核的REST API（仅管理员可访问）
 * 权限控制由 SecurityConfig 中 URL 级别 .hasRole("ADMIN") 处理
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "管理员审核", description = "未映射词条审核管理API")
public class AdminController {

    private final AdminService adminService;

    private final com.student.service.rag.SynonymExpansionService synonymExpansionService;

    @GetMapping("/unmapped-terms")
    @Operation(summary = "分页获取待审核词条")
    public ApiResponse.Pagination<List<UnmappedTermVO>> getUnmappedTerms(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status) {
        log.info("管理员查询待审核词条: page={}, size={}, status={}", page, size, status);
        return adminService.getPendingTerms(page, size, status);
    }

    @GetMapping("/unmapped-terms/stats")
    @Operation(summary = "获取审核统计数据")
    public ApiResponse<AdminStatsVO> getStats() {
        log.info("管理员查询审核统计");
        return ApiResponse.success(adminService.getStats());
    }

    @PostMapping("/unmapped-terms/{id}/approve")
    @Operation(summary = "单个审核通过")
    public ApiResponse<Void> approveTerm(
            @Parameter(description = "词条ID") @PathVariable Long id,
            @Parameter(description = "审核请求（含标准术语）") @RequestBody Map<String, String> body) {
        String standardTerm = body.get("standardTerm");
        if (standardTerm == null || standardTerm.isBlank()) {
            return ApiResponse.error("标准术语不能为空");
        }

        String reviewer = getCurrentUsername();
        adminService.approveTerm(id, standardTerm, reviewer);
        log.info("管理员审核通过词条: id={}, standardTerm={}, reviewer={}", id, standardTerm, reviewer);
        return ApiResponse.success();
    }

    @PostMapping("/unmapped-terms/batch-approve")
    @Operation(summary = "批量审核通过")
    public ApiResponse<?> batchApprove(
            @Parameter(description = "批量审核请求") @RequestBody List<ApproveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return ApiResponse.error("审核请求列表不能为空");
        }

        String reviewer = getCurrentUsername();
        int successCount = adminService.batchApprove(requests, reviewer);
        log.info("管理员批量审核通过: 总数={}, 成功={}, reviewer={}", requests.size(), successCount, reviewer);

        Map<String, Object> result = Map.of("total", requests.size(), "success", successCount,
                "failed", requests.size() - successCount);
        return ApiResponse.success("批量审核完成", result);
    }

    @PostMapping("/unmapped-terms/{id}/reject")
    @Operation(summary = "拒绝词条")
    public ApiResponse<Void> rejectTerm(
            @Parameter(description = "词条ID") @PathVariable Long id,
            @Parameter(description = "拒绝原因") @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        String reviewer = getCurrentUsername();
        adminService.rejectTerm(id, reason, reviewer);
        log.info("管理员拒绝词条: id={}, reviewer={}", id, reviewer);
        return ApiResponse.success();
    }

    @GetMapping("/stats/unmapped-rate")
    @Operation(summary = "获取未映射率")
    public ApiResponse<AdminStatsVO> getUnmappedRate() {
        return ApiResponse.success(adminService.getStats());
    }

    @DeleteMapping("/mappings/cleanup")
    @Operation(summary = "清理指定标准术语的映射记录")
    public ApiResponse<Map<String, Object>> cleanupMappings(
            @Parameter(description = "要清理的标准术语") @RequestParam(defaultValue = "测试标准术语") String standardTerm) {
        log.info("管理员清理映射记录: standardTerm={}", standardTerm);
        int deleted = synonymExpansionService.cleanupByStandardTerm(standardTerm);
        return ApiResponse.success("清理完成", Map.of("standardTerm", standardTerm, "deletedCount", deleted));
    }

    @PostMapping("/mappings/batch-upsert")
    @Operation(summary = "批量写入映射（跳过已存在）")
    public ApiResponse<Map<String, Object>> batchUpsertMappings(
            @RequestBody List<Map<String, Object>> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return ApiResponse.error("映射列表不能为空", Map.of());
        }
        int inserted = synonymExpansionService.batchUpsertMappings(mappings);
        return ApiResponse.success("批量写入完成", Map.of("total", mappings.size(), "inserted", inserted));
    }

    /**
     * 获取当前登录用户名
     */
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
