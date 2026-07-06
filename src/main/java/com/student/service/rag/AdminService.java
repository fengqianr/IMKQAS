package com.student.service.rag;

import com.student.dto.ApiResponse;
import com.student.dto.rag.AdminStatsVO;
import com.student.dto.rag.ApproveRequest;
import com.student.dto.rag.UnmappedTermVO;

import java.util.List;

/**
 * 管理员审核服务接口
 * 提供未映射词条的分页查询、审核通过、拒绝、统计等功能
 *
 * @author 系统
 * @version 1.0
 */
public interface AdminService {

    /**
     * 分页获取待审核词条
     *
     * @param page 页码
     * @param size 每页大小
     * @param status 筛选状态（PENDING/APPROVED/REJECTED，null表示全部）
     * @return 分页结果
     */
    ApiResponse.Pagination<List<UnmappedTermVO>> getPendingTerms(int page, int size, String status);

    /**
     * 单个审核通过
     *
     * @param id 词条ID
     * @param standardTerm 标准术语
     * @param reviewer 审核人用户名
     */
    void approveTerm(Long id, String standardTerm, String reviewer);

    /**
     * 批量审核通过
     *
     * @param requests 审核请求列表
     * @param reviewer 审核人用户名
     * @return 成功数量
     */
    int batchApprove(List<ApproveRequest> requests, String reviewer);

    /**
     * 拒绝词条（软删除）
     *
     * @param id 词条ID
     * @param reason 拒绝原因
     * @param reviewer 审核人用户名
     */
    void rejectTerm(Long id, String reason, String reviewer);

    /**
     * 获取审核统计数据
     *
     * @return 统计数据
     */
    AdminStatsVO getStats();
}
