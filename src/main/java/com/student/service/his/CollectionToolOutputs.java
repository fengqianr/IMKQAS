package com.student.service.his;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 采集子Agent工具调用输出类型定义
 * 状态机通过这些类型判断下一步动作
 *
 * @author 系统
 * @version 1.0
 */
public class CollectionToolOutputs {

    /**
     * LLM调用产生的统一输出包装
     */
    public enum OutputType {
        ASK_QUESTION,
        RECORD_ANSWER,
        CLARIFY,
        COMPLETE,
        EMERGENCY_INTERRUPT
    }

    /**
     * 提问输出：LLM要求展示下一个问题
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AskQuestionOutput {
        private String linkId;
        private String text;
        private int currentIndex;
        private int totalQuestions;
        private List<QuestionnaireTemplate.AnswerOption> options;
    }

    /**
     * 答案记录输出：LLM已理解用户回答并提取了编码
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordAnswerOutput {
        private String linkId;
        private String code;
        private String display;
        private int value;
        private String rawInput;
        private double confidence;
        private String contextSummary;
        /** 答案来源：llm / rule_parser */
        private String source;
    }

    /**
     * 追问输出：LLM认为需要澄清
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClarifyOutput {
        private String linkId;
        private String clarifyingText;
        private List<QuestionnaireTemplate.AnswerOption> options;
    }

    /**
     * 完成输出：全部问题已回答
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteOutput {
        private String message;
        private int totalScore;
        private int maxScore;
        private String severity;
        private String interpretation;
        /** 分析Agent生成的评估摘要 */
        private String analysisSummary;
        /** 关联分析报告ID */
        private String analysisId;
    }

    /**
     * 紧急中断输出：检测到危险信号
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyInterruptOutput {
        private String reason;
        private String userMessage;
    }

    /**
     * LLM调用的统一返回结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentResult {
        private OutputType type;
        private AskQuestionOutput askQuestion;
        private RecordAnswerOutput recordAnswer;
        private ClarifyOutput clarify;
        private CompleteOutput complete;
        private EmergencyInterruptOutput emergencyInterrupt;
        private String rawLlmResponse;
        private long latencyMs;
        /** 当前降级层级：llm / rule_parser / manual_form */
        private String degradationLevel;

        public static AgentResult askQuestion(AskQuestionOutput output) {
            return AgentResult.builder().type(OutputType.ASK_QUESTION).askQuestion(output).build();
        }

        public static AgentResult recordAnswer(RecordAnswerOutput output) {
            return AgentResult.builder().type(OutputType.RECORD_ANSWER).recordAnswer(output).build();
        }

        public static AgentResult clarify(ClarifyOutput output) {
            return AgentResult.builder().type(OutputType.CLARIFY).clarify(output).build();
        }

        public static AgentResult complete(CompleteOutput output) {
            return AgentResult.builder().type(OutputType.COMPLETE).complete(output).build();
        }

        public static AgentResult emergency(EmergencyInterruptOutput output) {
            return AgentResult.builder().type(OutputType.EMERGENCY_INTERRUPT)
                    .emergencyInterrupt(output).build();
        }
    }
}
