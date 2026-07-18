package com.imkqas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.imkqas.entity.synonym.UnmappedTermRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;

/**
 * 未映射词条记录 Mapper
 *
 * @author 系统
 * @version 1.0
 */
@Mapper
public interface UnmappedTermRecordMapper extends BaseMapper<UnmappedTermRecord> {

    @Select("SELECT * FROM unmapped_term_queue WHERE term = #{term} AND status = 'PENDING' AND deleted = 0 LIMIT 1")
    UnmappedTermRecord findPendingByTerm(@Param("term") String term);

    @Select("SELECT * FROM unmapped_term_queue WHERE status = 'PENDING' AND deleted = 0 ORDER BY occurrence_count DESC, last_seen_at DESC LIMIT #{limit}")
    List<UnmappedTermRecord> findPendingTerms(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM unmapped_term_queue WHERE status = 'PENDING' AND deleted = 0")
    long countPending();

    @Update("UPDATE unmapped_term_queue SET occurrence_count = occurrence_count + 1, last_seen_at = NOW() WHERE id = #{id}")
    int incrementOccurrence(@Param("id") Long id);

    @Select("SELECT * FROM unmapped_term_queue WHERE status = #{status} AND deleted = 0 ORDER BY occurrence_count DESC, last_seen_at DESC")
    List<UnmappedTermRecord> selectPageByStatus(@Param("status") String status);

    @Update("UPDATE unmapped_term_queue SET status = #{status}, reviewer = #{reviewer}, review_note = #{reviewNote}, updated_at = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("reviewer") String reviewer, @Param("reviewNote") String reviewNote);

    @Select("SELECT COUNT(*) FROM unmapped_term_queue WHERE status = 'APPROVED' AND DATE(updated_at) = CURDATE() AND deleted = 0")
    long countApprovedToday();

    @Select("SELECT term, occurrence_count FROM unmapped_term_queue WHERE status = 'PENDING' AND deleted = 0 ORDER BY occurrence_count DESC LIMIT #{limit}")
    List<Map<String, Object>> findTopUnmappedTerms(@Param("limit") int limit);
}
