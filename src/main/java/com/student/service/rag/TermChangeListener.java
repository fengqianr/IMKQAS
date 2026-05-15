package com.student.service.rag;

/**
 * 术语变更监听器接口
 * <p>
 * 触发机制（三种）：
 * 1. 人工触发：后台提供"清除术语缓存"按钮，调用 invalidateCache()
 * 2. 消息订阅：订阅术语库变更事件（预留MQ接口，当前用定时轮询模拟）
 * 3. 定时兜底：每天/每小时对比术语映射表版本号或Hash，若变化则主动失效
 * <p>
 * 失效策略：
 * - 优先精确删除（按标准化术语key）
 * - 必要时递增知识库版本触发全量自然失效
 *
 * @author 系统
 * @version 1.0
 */
public interface TermChangeListener {

    /**
     * 手动触发缓存失效（后台"清除术语缓存"按钮调用）
     *
     * @param standardTerm 标准化术语，为null时全量失效
     */
    void manualInvalidate(String standardTerm);

    /**
     * 消息驱动的术语变更处理（预留MQ接口）
     *
     * @param termChangeEvent 术语变更事件
     */
    void onTermChanged(TermChangeEvent termChangeEvent);

    /**
     * 定时检查术语映射版本（兜底机制）
     * 对比术语映射表Hash，若变化则主动失效相关缓存
     */
    void scheduledVersionCheck();

    /**
     * 获取当前术语映射表的版本/Hash
     */
    String getTermMappingHash();

    // ========== 内部数据类型 ==========

    /**
     * 术语变更事件
     */
    class TermChangeEvent {
        private final String term;
        private final String oldValue;
        private final String newValue;
        private final ChangeType changeType;
        private final long timestamp;

        public TermChangeEvent(String term, String oldValue, String newValue,
                               ChangeType changeType) {
            this.term = term;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.changeType = changeType;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTerm() { return term; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
        public ChangeType getChangeType() { return changeType; }
        public long getTimestamp() { return timestamp; }
    }

    enum ChangeType {
        ADDED,      // 新增术语
        UPDATED,    // 更新术语
        DELETED     // 删除术语
    }
}
