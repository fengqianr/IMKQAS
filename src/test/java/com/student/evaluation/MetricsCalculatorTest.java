package com.student.evaluation;

import com.student.utils.evaluation.MetricsCalculator;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评估指标计算工具类单元测试
 *
 * @author 系统
 * @version 1.0
 */
class MetricsCalculatorTest {

    @Test
    void testRecallAtK_PerfectRecall() {
        List<Long> retrieved = List.of(1L, 2L, 3L, 4L, 5L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);

        assertEquals(1.0, MetricsCalculator.recallAtK(retrieved, groundTruth, 5), 0.001);
        assertEquals(2.0 / 3.0, MetricsCalculator.recallAtK(retrieved, groundTruth, 2), 0.001);
    }

    @Test
    void testRecallAtK_EmptyGroundTruth() {
        List<Long> retrieved = List.of(1L, 2L, 3L);
        assertEquals(0.0, MetricsCalculator.recallAtK(retrieved, Collections.emptySet(), 3), 0.001);
    }

    @Test
    void testRecallAtK_NoHit() {
        List<Long> retrieved = List.of(10L, 20L, 30L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        assertEquals(0.0, MetricsCalculator.recallAtK(retrieved, groundTruth, 5), 0.001);
    }

    @Test
    void testPrecisionAtK() {
        List<Long> retrieved = List.of(1L, 2L, 10L, 20L, 30L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);

        assertEquals(2.0 / 5.0, MetricsCalculator.precisionAtK(retrieved, groundTruth, 5), 0.001);
        assertEquals(2.0 / 3.0, MetricsCalculator.precisionAtK(retrieved, groundTruth, 3), 0.001);
        // top-3 = [1, 2, 10], intersection with {1,2,3} = {1,2}, precision = 2/3
    }

    @Test
    void testMRR() {
        List<Integer> ranks = List.of(1, 3, -1, 2);
        double expected = (1.0 / 1 + 1.0 / 3 + 0.0 + 1.0 / 2) / 4;
        assertEquals(expected, MetricsCalculator.mrr(ranks), 0.001);
    }

    @Test
    void testMRR_Empty() {
        assertEquals(0.0, MetricsCalculator.mrr(Collections.emptyList()), 0.001);
    }

    @Test
    void testFindFirstRelevantRank() {
        List<Long> retrieved = List.of(10L, 20L, 1L, 30L);
        Set<Long> groundTruth = Set.of(1L, 5L);

        assertEquals(3, MetricsCalculator.findFirstRelevantRank(retrieved, groundTruth));
    }

    @Test
    void testFindFirstRelevantRank_NoHit() {
        List<Long> retrieved = List.of(10L, 20L, 30L);
        Set<Long> groundTruth = Set.of(1L, 5L);

        assertEquals(-1, MetricsCalculator.findFirstRelevantRank(retrieved, groundTruth));
    }

    @Test
    void testNDCG() {
        List<Long> retrieved = List.of(1L, 2L, 3L);
        Map<Long, Double> labels = Map.of(1L, 1.0, 2L, 0.5, 3L, 0.0);

        double ndcg = MetricsCalculator.ndcgAtK(retrieved, labels, 3);
        assertTrue(ndcg >= 0.0 && ndcg <= 1.0, "NDCG should be in [0,1]: " + ndcg);
    }

    @Test
    void testDCG() {
        List<Double> scores = List.of(1.0, 0.5, 0.0);
        double dcg = MetricsCalculator.dcgAtK(scores, 3);
        assertTrue(dcg > 0, "DCG should be positive");
    }

    @Test
    void testHitRate() {
        List<Boolean> hits = List.of(true, false, true, true, false);
        assertEquals(0.6, MetricsCalculator.hitRateAtK(hits), 0.001);
    }

    @Test
    void testComplementarity() {
        Set<Long> vec = Set.of(1L, 2L, 3L, 4L, 5L);
        Set<Long> kw = Set.of(3L, 4L, 5L, 6L, 7L);

        double complementarity = MetricsCalculator.complementarity(vec, kw);
        assertTrue(complementarity >= 1.0 && complementarity <= 2.0,
                "Complementarity should be in [1,2]: " + complementarity);
    }

    @Test
    void testOverlapRate() {
        Set<Long> vec = Set.of(1L, 2L, 3L, 4L, 5L);
        Set<Long> kw = Set.of(3L, 4L, 5L, 6L, 7L);

        double overlap = MetricsCalculator.overlapRate(vec, kw);
        assertEquals(3.0 / 5.0, overlap, 0.001);
    }

    @Test
    void testVectorUniqueContribution() {
        Set<Long> vec = Set.of(1L, 2L, 3L);
        Set<Long> kw = Set.of(3L, 4L, 5L);
        Set<Long> groundTruth = Set.of(1L, 3L, 5L);

        double contrib = MetricsCalculator.vectorUniqueContribution(vec, kw, groundTruth);
        assertEquals(1.0 / 3.0, contrib, 0.001);
    }

    @Test
    void testAverage() {
        List<Double> values = List.of(1.0, 2.0, 3.0, 4.0);
        assertEquals(2.5, MetricsCalculator.average(values), 0.001);
    }

    @Test
    void testAverage_Empty() {
        assertEquals(0.0, MetricsCalculator.average(Collections.emptyList()), 0.001);
    }

    @Test
    void testPercentile() {
        List<Long> values = List.of(10L, 20L, 30L, 40L, 50L, 60L, 70L, 80L, 90L, 100L);

        assertEquals(50.0, MetricsCalculator.percentile(values, 50), 0.01);
        assertEquals(100.0, MetricsCalculator.percentile(values, 95), 0.01);
        // ceil(95/100 * 10) = 10 → index 9 → value 100
    }

    @Test
    void testConfidenceDistribution() {
        List<Double> confidences = List.of(0.1, 0.2, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9);

        int[] buckets = MetricsCalculator.confidenceDistribution(confidences);
        assertEquals(2, buckets[0]); // [0, 0.35): 0.1, 0.2
        assertEquals(2, buckets[1]); // [0.35, 0.6): 0.4, 0.5
        assertEquals(2, buckets[2]); // [0.6, 0.8): 0.6, 0.7
        assertEquals(2, buckets[3]); // [0.8, 1.0]: 0.8, 0.9
    }

    @Test
    void testWeightedAverage() {
        List<Double> scores = List.of(0.8, 0.6, 0.9);
        List<Double> weights = List.of(0.4, 0.2, 0.4);

        double expected = 0.8 * 0.4 + 0.6 * 0.2 + 0.9 * 0.4;
        assertEquals(expected, MetricsCalculator.weightedAverage(scores, weights), 0.001);
    }
}
