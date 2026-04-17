package com.student.controller.qa;

import com.student.service.rag.QaService;
import com.student.service.triage.TriageService;
import com.student.service.drug.DrugQueryService;
import com.student.dto.triage.TriageRequest;
import com.student.model.triage.DepartmentTriageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

/**
 * 问答控制器
 * 提供智能问答、科室导诊、药物查询等统一API入口
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
@Validated
@Tag(name = "智能问答", description = "智能问答、科室导诊、药物查询等统一API")
public class QaController {

    private final QaService qaService;
    private final TriageService triageService;
    private final DrugQueryService drugQueryService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式问答", description = "基于SSE（Server-Sent Events）的流式问答接口")
    public SseEmitter streamAnswer(
            @Parameter(description = "用户查询问题", required = true)
            @RequestParam String query,
            @Parameter(description = "用户ID", required = false)
            @RequestParam(required = false) Long userId,
            @Parameter(description = "对话ID", required = false)
            @RequestParam(required = false) Long conversationId) {
        log.info("流式问答请求: query={}, userId={}, conversationId={}", query, userId, conversationId);

        SseEmitter emitter = new SseEmitter(60000L); // 60秒超时

        CompletableFuture.runAsync(() -> {
            try {
                // 暂时使用非流式问答，后续实现真正的流式输出
                QaService.QaResponse response = qaService.answer(query, userId, conversationId);

                // 模拟流式输出：将回答拆分为多个事件
                String answer = response.getAnswer();
                String[] chunks = answer.split("(?<=[。！？；.?!;])");

                for (int i = 0; i < chunks.length; i++) {
                    if (!chunks[i].trim().isEmpty()) {
                        String chunk = chunks[i].trim();
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(i))
                                .data(chunk)
                                .comment("Chunk " + (i + 1) + "/" + chunks.length));
                        Thread.sleep(100); // 模拟流式延迟
                    }
                }

                emitter.send(SseEmitter.event()
                        .id("complete")
                        .data("")
                        .comment("问答完成"));
                emitter.complete();
            } catch (Exception e) {
                log.error("流式问答异常", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @PostMapping("/ask")
    @Operation(summary = "同步问答", description = "同步问答接口，返回完整的问答结果")
    public ResponseEntity<QaService.QaResponse> ask(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "问答请求", required = true)
            @RequestBody Map<String, Object> requestBody) {
        String query = (String) requestBody.get("question");
        Long userId = requestBody.get("userId") != null ? ((Number) requestBody.get("userId")).longValue() : null;
        Long conversationId = requestBody.get("conversationId") != null ? ((Number) requestBody.get("conversationId")).longValue() : null;

        log.info("同步问答请求: query={}, userId={}, conversationId={}", query, userId, conversationId);

        QaService.QaResponse response = qaService.answer(query, userId, conversationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/triage")
    @Operation(summary = "科室导诊", description = "根据症状描述推荐合适的就诊科室")
    public ResponseEntity<DepartmentTriageResult> triage(
            @Parameter(description = "症状描述", required = true)
            @NotBlank(message = "症状描述不能为空")
            @RequestParam String symptoms) {
        log.info("科室导诊请求: symptoms={}", symptoms);

        // 创建分流请求
        TriageRequest request = new TriageRequest();
        request.setSymptoms(symptoms);
        // userId可为null，表示匿名用户
        request.setUserId(null);
        request.setIncludeEmergencyCheck(true);

        // 调用科室导诊服务
        DepartmentTriageResult result = triageService.triage(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/drug")
    @Operation(summary = "药物查询", description = "根据药品名称查询药品信息")
    public ResponseEntity<?> searchDrug(
            @Parameter(description = "药品名称（通用名、商品名、别名等）", required = true)
            @NotBlank(message = "药品名称不能为空")
            @RequestParam String name) {
        log.info("药物查询请求: name={}", name);

        // 调用药物查询服务
        return ResponseEntity.ok(drugQueryService.searchDrugsByName(name));
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "问答服务健康检查")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("QaController is healthy");
    }
}