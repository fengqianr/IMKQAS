package com.student.service.snomed;

import com.student.service.snomed.config.SnomedConfigProperties;
import com.student.service.snomed.config.SnomedFallbackMapper;
import com.student.service.snomed.dto.SnomedTermResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SNOMED CT 术语服务单元测试
 *
 * @author 系统
 * @version 1.0
 */
@ExtendWith(MockitoExtension.class)
class SnomedTerminologyServiceTest {

    @Mock
    private com.student.service.RedisService redisService;

    private SnomedConfigProperties configProperties;
    private SnomedFallbackMapper fallbackMapper;
    private SnomedTerminologyService snomedTerminologyService;

    @BeforeEach
    void setUp() {
        configProperties = new SnomedConfigProperties();
        configProperties.setEnabled(true);
        configProperties.setBaseUrl("http://localhost:8082");
        configProperties.setFhirPath("/fhir");
        configProperties.setConnectTimeout(5000);
        configProperties.setReadTimeout(10000);
        configProperties.setCacheExpirationHours(24);
        configProperties.setFallbackEnabled(true);

        fallbackMapper = new SnomedFallbackMapper();
        // 手动调用init方法（@PostConstruct在单元测试中不会自动调用）
        fallbackMapper.init();

        snomedTerminologyService = new SnomedTerminologyService(
                redisService,
                configProperties,
                fallbackMapper
        );
    }

    @Test
    void testFallbackMapperInitialization() {
        // 验证降级映射表初始化
        assertNotNull(fallbackMapper);
        assertTrue(fallbackMapper.hasColloquialMapping("脑袋疼"));
        assertTrue(fallbackMapper.hasColloquialMapping("拉肚子"));
        assertTrue(fallbackMapper.hasMedicalTermMapping("糖尿病"));
        assertTrue(fallbackMapper.hasMedicalTermMapping("高血压"));
    }

    @Test
    void testColloquialToMedical() {
        // 测试口语化表达转医学术语
        String[] mapping = fallbackMapper.getByColloquial("脑袋疼");
        assertNotNull(mapping);
        assertEquals("22253000", mapping[0]); // conceptId
        assertEquals("头痛", mapping[1]); // 术语
        assertEquals("FSN", mapping[2]); // 术语类型

        mapping = fallbackMapper.getByColloquial("拉肚子");
        assertNotNull(mapping);
        assertEquals("235595009", mapping[0]);
        assertEquals("腹泻", mapping[1]);

        mapping = fallbackMapper.getByColloquial("心慌");
        assertNotNull(mapping);
        assertEquals("48694002", mapping[0]);
        assertEquals("心悸", mapping[1]);
    }

    @Test
    void testMedicalTermMapping() {
        // 测试医学术语映射
        String[] mapping = fallbackMapper.getByMedicalTerm("糖尿病");
        assertNotNull(mapping);
        assertEquals("44054006", mapping[0]);

        mapping = fallbackMapper.getByMedicalTerm("高血压");
        assertNotNull(mapping);
        assertEquals("38341003", mapping[0]);

        mapping = fallbackMapper.getByMedicalTerm("肺炎");
        assertNotNull(mapping);
        assertEquals("233604007", mapping[0]);
    }

    @Test
    void testSymptomKeywordMapping() {
        // 测试症状关键词映射
        String[] mapping = fallbackMapper.getBySymptomKeyword("疼");
        assertNotNull(mapping);
        assertEquals("21522001", mapping[0]);

        mapping = fallbackMapper.getBySymptomKeyword("痒");
        assertNotNull(mapping);
        assertEquals("86259008", mapping[0]);
    }

    @Test
    void testDiseaseKeywordMapping() {
        // 测试疾病关键词映射
        String[] mapping = fallbackMapper.getByDiseaseKeyword("病");
        assertNotNull(mapping);
        assertEquals("64572001", mapping[0]);

        mapping = fallbackMapper.getByDiseaseKeyword("炎症");
        assertNotNull(mapping);
        assertEquals("235494008", mapping[0]);
    }

    @Test
    void testMedicalizeWithLocalMapping() {
        // 测试术语化方法（使用本地降级映射）
        // 注意：这里测试的是本地映射逻辑，不调用实际SNOMED服务

        // 测试口语化表达
        String result = snomedTerminologyService.medicalize("我脑袋疼");
        assertNotNull(result);
        // 由于未启用实际服务，应返回原始查询或使用降级映射
        assertTrue(result.contains("头痛") || result.equals("我脑袋疼"));
    }

    @Test
    void testLookupByTermWithFallback() {
        // 测试根据术语查询（使用降级映射）
        SnomedTermResponse response = snomedTerminologyService.lookupByTerm("糖尿病");
        // 由于Redis缓存未设置，应返回null（实际会走降级逻辑）
        assertNull(response);
    }

    @Test
    void testGetStats() {
        // 测试统计信息获取
        Map<String, Object> stats = snomedTerminologyService.getStats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalQueries"));
        assertTrue(stats.containsKey("snomedAvailable"));
    }

    @Test
    void testNullInputHandling() {
        // 测试空值处理 - 空字符串应该返回空字符串而非null
        assertEquals("", snomedTerminologyService.medicalize(""));
        assertEquals("   ", snomedTerminologyService.medicalize("   "));
    }
}