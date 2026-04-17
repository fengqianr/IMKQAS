package com.student.controller.drug;

import com.student.entity.drug.Drug;
import com.student.entity.drug.DrugInteraction;
import com.student.service.drug.DrugQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 药物查询控制器
 * 提供药品信息查询、药物相互作用检查等API接口
 *
 * @author 系统
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/drugs")
@RequiredArgsConstructor
@Tag(name = "药物查询", description = "药品信息查询和相互作用检查API")
public class DrugController {

    private final DrugQueryService drugQueryService;

    @GetMapping("/search")
    @Operation(summary = "根据药品名称搜索药品", description = "根据药品通用名、商品名或别名搜索药品信息")
    public ResponseEntity<List<Drug>> searchDrugs(
            @Parameter(description = "药品名称（通用名、商品名、别名等）", required = true)
            @RequestParam String name) {
        log.info("搜索药品: {}", name);
        List<Drug> drugs = drugQueryService.searchDrugsByName(name);
        return ResponseEntity.ok(drugs);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取药品详细信息", description = "根据药品ID获取药品的详细信息")
    public ResponseEntity<Drug> getDrugById(
            @Parameter(description = "药品ID", required = true)
            @PathVariable Long id) {
        log.info("查询药品详情: {}", id);
        Drug drug = drugQueryService.getDrugById(id);
        if (drug == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(drug);
    }

    @GetMapping("/interactions")
    @Operation(summary = "检查两种药品的相互作用", description = "检查两种药品之间是否存在相互作用")
    public ResponseEntity<DrugInteraction> checkInteraction(
            @Parameter(description = "药品A ID", required = true)
            @RequestParam Long drugAId,
            @Parameter(description = "药品B ID", required = true)
            @RequestParam Long drugBId) {
        log.info("检查药物相互作用: drugAId={}, drugBId={}", drugAId, drugBId);
        DrugInteraction interaction = drugQueryService.checkDrugInteraction(drugAId, drugBId);
        if (interaction == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(interaction);
    }

    @PostMapping("/interactions/batch")
    @Operation(summary = "批量检查药物相互作用", description = "检查多种药品之间的相互作用")
    public ResponseEntity<List<DrugInteraction>> checkMultipleInteractions(
            @Parameter(description = "药品ID列表", required = true)
            @RequestBody List<Long> drugIds) {
        log.info("批量检查药物相互作用: {}", drugIds);
        List<DrugInteraction> interactions = drugQueryService.checkMultipleDrugInteractions(drugIds);
        return ResponseEntity.ok(interactions);
    }

    @GetMapping("/classes")
    @Operation(summary = "获取药品分类列表", description = "获取所有药品分类")
    public ResponseEntity<List<String>> getDrugClasses() {
        log.info("获取药品分类列表");
        List<String> classes = drugQueryService.getDrugClasses();
        return ResponseEntity.ok(classes);
    }

    @GetMapping("/classes/{drugClass}")
    @Operation(summary = "根据分类查询药品", description = "根据药品分类查询该分类下的所有药品")
    public ResponseEntity<List<Drug>> getDrugsByClass(
            @Parameter(description = "药品分类", required = true)
            @PathVariable String drugClass) {
        log.info("根据分类查询药品: {}", drugClass);
        List<Drug> drugs = drugQueryService.getDrugsByClass(drugClass);
        return ResponseEntity.ok(drugs);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "药物查询服务健康检查")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Drug query service is healthy");
    }
}