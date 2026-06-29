package com.student.service.his;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSE事件适配器 —— 将CollectionToolOutputs.AgentResult转为SSE事件发送给前端
 * 只负责事件格式化和发送，不参与业务逻辑
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@Component
public class CollectionSseEmitter {

    private static final long DEFAULT_TIMEOUT = 180_000L;

    /**
     * 发送问题事件
     */
    public void sendQuestion(SseEmitter emitter, CollectionToolOutputs.AskQuestionOutput output,
                             String sessionId) {
        log.info("SSE发送问题: linkId={}, text={}, progress={}/{}, sessionId={}",
                output.getLinkId(), output.getText(),
                output.getCurrentIndex() + 1, output.getTotalQuestions(), sessionId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "question");
        event.put("linkId", output.getLinkId());
        event.put("text", output.getText());
        event.put("currentIndex", output.getCurrentIndex());
        event.put("totalQuestions", output.getTotalQuestions());
        event.put("progress", (output.getCurrentIndex() + 1) + "/" + output.getTotalQuestions());
        event.put("sessionId", sessionId);
        if (output.getOptions() != null) {
            event.put("options", output.getOptions());
        }
        send(emitter, "question", event);
    }

    /**
     * 发送追问事件
     */
    public void sendClarify(SseEmitter emitter, CollectionToolOutputs.ClarifyOutput output,
                            String sessionId) {
        log.info("SSE发送追问: linkId={}, text={}, sessionId={}",
                output.getLinkId(), output.getClarifyingText(), sessionId);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "clarify");
        event.put("linkId", output.getLinkId());
        event.put("text", output.getClarifyingText());
        event.put("sessionId", sessionId);
        if (output.getOptions() != null) {
            event.put("options", output.getOptions());
        }
        send(emitter, "clarify", event);
    }

    /**
     * 发送完成事件
     */
    public void sendComplete(SseEmitter emitter, CollectionToolOutputs.CompleteOutput output,
                             int totalScore, int maxScore, String severity, String interpretation,
                             String analysisSummary) {
        log.info("SSE发送完成: totalScore={}/{}, severity={}, hasAnalysis={}",
                totalScore, maxScore, severity, analysisSummary != null);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "completion");
        event.put("message", output.getMessage());
        event.put("totalScore", totalScore);
        event.put("maxScore", maxScore);
        event.put("severity", severity);
        event.put("interpretation", interpretation);
        if (analysisSummary != null) {
            event.put("analysisSummary", analysisSummary);
        }
        if (output.getAnalysisId() != null) {
            event.put("analysisId", output.getAnalysisId());
        }
        send(emitter, "completion", event);
    }

    /**
     * 发送安全警报事件
     */
    public void sendSafetyAlert(SseEmitter emitter, CollectionToolOutputs.EmergencyInterruptOutput output) {
        log.warn("SSE发送安全警报: reason={}", output.getReason());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "safety_alert");
        event.put("reason", output.getReason());
        event.put("message", output.getUserMessage());
        send(emitter, "safety_alert", event);
    }

    /**
     * 发送进度事件
     */
    public void sendProgress(SseEmitter emitter, int current, int total) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "progress");
        event.put("current", current);
        event.put("total", total);
        event.put("percent", total > 0 ? current * 100 / total : 0);
        send(emitter, "progress", event);
    }

    /**
     * 发送错误事件
     */
    public void sendError(SseEmitter emitter, String message) {
        log.error("SSE发送错误: {}", message);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "error");
        event.put("message", message);
        send(emitter, "error", event);
    }

    /**
     * 发送降级通知
     */
    public void sendDegradationNotice(SseEmitter emitter, String level, String reason) {
        log.warn("SSE发送降级通知: level={}, reason={}", level, reason);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "degradation");
        event.put("level", level);
        event.put("reason", reason);
        send(emitter, "degradation", event);
    }

    /**
     * 发送结束标记
     */
    public void sendDone(SseEmitter emitter) {
        log.info("SSE发送结束标记");
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "done");
        send(emitter, "done", event);
    }

    /**
     * 创建带标准超时和回调的SseEmitter
     */
    public SseEmitter createEmitter() {
        return createEmitter(DEFAULT_TIMEOUT);
    }

    /**
     * 创建带自定义超时的SseEmitter
     */
    public SseEmitter createEmitter(long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);

        emitter.onCompletion(() -> log.info("SSE连接完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时: {}ms", timeoutMs);
            completeSilently(emitter);
        });
        emitter.onError(ex -> {
            log.warn("SSE连接异常: {}", ex.getMessage());
            completeSilently(emitter);
        });

        log.info("SSE连接已建立: timeout={}ms", timeoutMs);
        return emitter;
    }

    /**
     * 安全完成SSE连接
     */
    public void complete(SseEmitter emitter) {
        try {
            emitter.complete();
            log.info("SSE连接正常关闭");
        } catch (Exception ignored) {
        }
    }

    /**
     * 安全完成并发送错误
     */
    public void completeWithError(SseEmitter emitter, Throwable ex) {
        log.error("SSE连接异常关闭: {}", ex.getMessage() != null ? ex.getMessage() : "未知错误");
        try {
            sendError(emitter, ex.getMessage() != null ? ex.getMessage() : "未知错误");
        } catch (Exception ignored) {
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    // ==================== 私有方法 ====================

    private void send(SseEmitter emitter, String eventId, Object data) {
        try {
            emitter.send(SseEmitter.event().id(eventId).data(data));
        } catch (IOException e) {
            log.warn("SSE发送失败: eventId={}, error={}", eventId, e.getMessage());
        }
    }

    private void completeSilently(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
