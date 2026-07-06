package com.student.service.rag.impl;

import com.student.dto.ApiResponse;
import com.student.dto.rag.AdminStatsVO;
import com.student.dto.rag.ApproveRequest;
import com.student.dto.rag.UnmappedTermVO;
import com.student.entity.synonym.UnmappedTermRecord;
import com.student.mapper.UnmappedTermRecordMapper;
import com.student.service.rag.AdminService;
import com.student.service.rag.SynonymExpansionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员审核服务实现
 *
 * @author 系统
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UnmappedTermRecordMapper unmappedTermMapper;
    private final SynonymExpansionService synonymExpansionService;

    @Override
    public ApiResponse.Pagination<List<UnmappedTermVO>> getPendingTerms(int page, int size, String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            status = "PENDING";
        }

        // 查询所有符合条件的记录，手动分页
        List<UnmappedTermRecord> allRecords = unmappedTermMapper.selectPageByStatus(status);
        long total = allRecords.size();
        int totalPages = (int) Math.ceil((double) total / size);

        // 手动分页
        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, allRecords.size());
        List<UnmappedTermRecord> pageRecords;
        if (fromIndex >= allRecords.size()) {
            pageRecords = Collections.emptyList();
        } else {
            pageRecords = allRecords.subList(fromIndex, toIndex);
        }

        List<UnmappedTermVO> voList = pageRecords.stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        ApiResponse.Pagination<List<UnmappedTermVO>> pagination = new ApiResponse.Pagination<>();
        pagination.setData(voList);
        pagination.setTotal(total);
        pagination.setPage(page);
        pagination.setSize(size);
        pagination.setTotalPages(totalPages);
        return pagination;
    }

    @Override
    @Transactional
    public void approveTerm(Long id, String standardTerm, String reviewer) {
        UnmappedTermRecord record = unmappedTermMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("词条不存在: id=" + id);
        }

        // 调用同义词扩展服务完成审核
        synonymExpansionService.approveMapping(record.getTerm(), standardTerm, reviewer);

        log.info("管理员审核通过词条: id={}, {} -> {}, reviewer={}", id, record.getTerm(), standardTerm, reviewer);
    }

    @Override
    @Transactional
    public int batchApprove(List<ApproveRequest> requests, String reviewer) {
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        for (ApproveRequest request : requests) {
            try {
                approveTerm(request.getId(), request.getStandardTerm(), reviewer);
                successCount++;
            } catch (Exception e) {
                errors.add("id=" + request.getId() + ": " + e.getMessage());
                log.warn("批量审核词条失败: id={}, error={}", request.getId(), e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("批量审核部分失败: 成功={}, 失败={}, 详情={}", successCount, errors.size(), errors);
        }
        return successCount;
    }

    @Override
    public void rejectTerm(Long id, String reason, String reviewer) {
        UnmappedTermRecord record = unmappedTermMapper.selectById(id);
        if (record == null) {
            throw new IllegalArgumentException("词条不存在: id=" + id);
        }

        unmappedTermMapper.updateStatus(id, "REJECTED", reviewer,
                reason != null ? reason : "审核拒绝");

        log.info("管理员拒绝词条: id={}, term={}, reviewer={}, reason={}", id, record.getTerm(), reviewer, reason);
    }

    @Override
    public AdminStatsVO getStats() {
        SynonymExpansionService.ExpansionStats expansionStats = synonymExpansionService.getStats();

        AdminStatsVO stats = new AdminStatsVO();
        stats.setPendingCount(expansionStats.getPendingReviewCount());
        stats.setApprovedTodayCount(unmappedTermMapper.countApprovedToday());
        stats.setUnmappedRate(expansionStats.getUnmappedRate());
        stats.setAlertThreshold(5.0); // 与配置对齐
        stats.setAlertTriggered(expansionStats.isAlertTriggered());
        stats.setTotalAlertCount(0); // 从Redis读取

        // Top未映射词条
        List<Map<String, Object>> topTerms = unmappedTermMapper.findTopUnmappedTerms(5);
        List<String> topTermNames = topTerms.stream()
                .map(m -> String.valueOf(m.get("term")))
                .collect(Collectors.toList());
        stats.setTopUnmappedTerms(topTermNames);

        // 近24h趋势（暂返回空，可从OnlineMetricsSnapshot查询）
        stats.setHourlyTrend(Collections.emptyList());

        return stats;
    }

    /**
     * 实体转VO
     */
    private UnmappedTermVO toVO(UnmappedTermRecord record) {
        UnmappedTermVO vo = new UnmappedTermVO();
        vo.setId(record.getId());
        vo.setTerm(record.getTerm());
        vo.setContextQuery(record.getContextQuery());
        vo.setGuessedEntityType(record.getGuessedEntityType());
        vo.setLlmGuess(record.getLlmGuess());
        vo.setLlmConfidence(record.getLlmConfidence());
        vo.setOccurrenceCount(record.getOccurrenceCount());
        vo.setStatus(record.getStatus());
        vo.setReviewer(record.getReviewer());
        vo.setReviewNote(record.getReviewNote());
        vo.setFirstSeenAt(record.getFirstSeenAt());
        vo.setLastSeenAt(record.getLastSeenAt());
        return vo;
    }
}
