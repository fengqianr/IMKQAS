package com.student.service.snomed.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * SNOMED CT 本地降级映射表
 * 当Snowstorm服务不可用时使用本地映射进行术语化
 *
 * @author 系统
 * @version 1.0
 */
@Component
@Slf4j
public class SnomedFallbackMapper {

    /**
     * 口语化表达 -> SNOMED CT 术语映射
     * Key: 口语化表达
     * Value: SNOMED CT 概念信息 [conceptId, preferredTerm, termType]
     */
    private final Map<String, String[]> colloquialToSnomedMap = new HashMap<>();

    /**
     * 通用医学术语 -> SNOMED CT 术语映射
     */
    private final Map<String, String[]> medicalTermToSnomedMap = new HashMap<>();

    /**
     * 症状关键词 -> SNOMED CT 症状概念映射
     */
    private final Map<String, String[]> symptomKeywordMap = new HashMap<>();

    /**
     * 疾病关键词 -> SNOMED CT 疾病概念映射
     */
    private final Map<String, String[]> diseaseKeywordMap = new HashMap<>();

    /**
     * 标记是否已初始化
     */
    private volatile boolean initialized = false;

    /**
     * 默认构造函数（用于Spring注入）
     */
    public SnomedFallbackMapper() {
        // 延迟初始化
    }

    /**
     * 初始化本地映射表（供Spring调用或在测试中手动调用）
     */
    @PostConstruct
    public void init() {
        if (initialized) {
            return;
        }
        initColloquialMappings();
        initMedicalTermMappings();
        initSymptomMappings();
        initDiseaseMappings();
        initialized = true;

        log.info("SNOMED CT本地降级映射表初始化完成: 口语词={}, 医学术语={}, 症状={}, 疾病={}",
                colloquialToSnomedMap.size(), medicalTermToSnomedMap.size(),
                symptomKeywordMap.size(), diseaseKeywordMap.size());
    }

    /**
     * 确保已初始化（延迟加载）
     */
    private void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    init();
                }
            }
        }
    }

    /**
     * 初始化口语化表达映射
     */
    private void initColloquialMappings() {
        // 格式: 口语表达 -> [conceptId, 首选术语, 术语类型]
        String[][] colloquialMappings = {
                // 头部症状
                {"脑袋疼", "22253000", "头痛", "FSN"},
                {"头疼", "22253000", "头痛", "SYNONYM"},
                {"头庝", "22253000", "头痛", "SYNONYM"},
                {"头昏", "44027006", "头晕", "FSN"},
                {"头晕", "44027006", "头晕", "SYNONYM"},
                {"头昏眼花", "44027006", "头晕", "SYNONYM"},

                // 胸部症状
                {"心慌", "48694002", "心悸", "FSN"},
                {"心口疼", "21522001", "胸痛", "FSN"},
                {"胸闷", "199003", "胸部不适", "FSN"},
                {"喘不上气", "267036007", "呼吸困难", "FSN"},
                {"呼吸困难", "267036007", "呼吸困难", "SYNONYM"},
                {"气短", "267036007", "呼吸困难", "SYNONYM"},

                // 腹部症状
                {"肚子疼", "21522001", "腹痛", "SYNONYM"},
                {"肚子痛", "21522001", "腹痛", "SYNONYM"},
                {"胃疼", "21522001", "腹痛", "SYNONYM"},
                {"胃痛", "21522001", "腹痛", "SYNONYM"},
                {"拉肚子", "235595009", "腹泻", "FSN"},
                {"腹泻", "235595009", "腹泻", "SYNONYM"},
                {"便秘", "14760008", "便秘", "FSN"},
                {"大便不通", "14760008", "便秘", "SYNONYM"},

                // 全身症状
                {"发烧", "386661008", "发热", "FSN"},
                {"发高烧", "386661008", "发热", "SYNONYM"},
                {"发热", "386661008", "发热", "SYNONYM"},
                {"发烧了", "386661008", "发热", "SYNONYM"},
                {"高烧", "386661008", "发热", "SYNONYM"},
                {"体温高", "386661008", "发热", "SYNONYM"},
                {"感冒了", "195967001", "急性鼻咽炎", "FSN"},
                {"感冒", "195967001", "急性鼻咽炎", "SYNONYM"},
                {"咳嗽", "49727002", "咳嗽", "FSN"},
                {"咳痰", "49727002", "咳嗽", "SYNONYM"},
                {"干咳", "274437002", "干咳", "FSN"},

                // 睡眠问题
                {"睡不着", "73430006", "失眠", "FSN"},
                {"失眠", "73430006", "失眠", "SYNONYM"},
                {"睡眠不好", "73430006", "失眠", "SYNONYM"},
                {"没胃口", "238136002", "食欲不振", "FSN"},
                {"食欲不振", "238136002", "食欲不振", "SYNONYM"},
                {"不想吃饭", "238136002", "食欲不振", "SYNONYM"},

                // 皮肤症状
                {"皮肤痒", "86259008", "瘙痒", "FSN"},
                {"痒", "86259008", "瘙痒", "SYNONYM"},
                {"起疹子", "39579001", "皮疹", "FSN"},
                {"皮疹", "39579001", "皮疹", "SYNONYM"},

                // 四肢症状
                {"腿疼", "221360009", "四肢疼痛", "FSN"},
                {"脚肿", "371632003", "肢体水肿", "FSN"},
                {"手脚麻", "217366009", "感觉异常", "FSN"},
                {"手脚麻木", "217366009", "感觉异常", "SYNONYM"}
        };

        for (String[] mapping : colloquialMappings) {
            colloquialToSnomedMap.put(mapping[0], new String[]{mapping[1], mapping[2], mapping[3]});
        }
    }

    /**
     * 初始化医学术语映射
     */
    private void initMedicalTermMappings() {
        String[][] termMappings = {
                // 常见疾病
                {"糖尿病", "44054006", "糖尿病 mellitus (disorder)", "FSN"},
                {"高血压", "38341003", "高血压", "FSN"},
                {"心脏病", "194828000", "心血管疾病", "FSN"},
                {"肺炎", "233604007", "肺炎", "FSN"},
                {"支气管炎", "197480006", "支气管炎", "FSN"},
                {"哮喘", "195967001", "哮喘", "FSN"},
                {"胃炎", "235494008", "胃炎", "FSN"},
                {"肝炎", "50711007", "肝炎", "FSN"},
                {"肾炎", "36101008", "肾炎", "FSN"},
                {"关节炎", "20104000", "关节炎", "FSN"},

                // 常见症状
                {"头痛", "22253000", "头痛", "FSN"},
                {"胸痛", "21522001", "腹痛", "FSN"},
                {"腹痛", "21522001", "腹痛", "SYNONYM"},
                {"发热", "386661008", "发热", "FSN"},
                {"咳嗽", "49727002", "咳嗽", "FSN"},
                {"腹泻", "235595009", "腹泻", "FSN"},
                {"便秘", "14760008", "便秘", "FSN"},
                {"呕吐", "422400008", "呕吐", "FSN"},
                {"恶心", "21522001", "恶心", "FSN"},
                {"头晕", "44027006", "头晕", "FSN"},

                // 检查项目
                {"血常规", "265764009", "血液检查", "FSN"},
                {"尿常规", "271296005", "尿液分析", "FSN"},
                {"心电图", "180270006", "心电图", "FSN"},
                {"B超", "363679005", "超声检查", "FSN"},
                {"CT", "77461001", "计算机断层扫描", "FSN"},
                {"核磁", "113091000", "磁共振成像", "FSN"},
                {"X光", "363679005", "X线检查", "FSN"}
        };

        for (String[] mapping : termMappings) {
            medicalTermToSnomedMap.put(mapping[0], new String[]{mapping[1], mapping[2], mapping[3]});
        }
    }

    /**
     * 初始化症状关键词映射
     */
    private void initSymptomMappings() {
        String[][] symptomMappings = {
                {"疼", "21522001", "疼痛", "SYNONYM"},
                {"痛", "21522001", "疼痛", "SYNONYM"},
                {"痒", "86259008", "瘙痒", "SYNONYM"},
                {"肿", "371632003", "水肿", "SYNONYM"},
                {"胀", "387713003", "胀满感", "SYNONYM"},
                {"红", "50352002", "充血", "SYNONYM"},
                {"热", "386661008", "发热", "SYNONYM"},
                {"晕", "44027006", "头晕", "SYNONYM"},
                {"吐", "422400008", "呕吐", "SYNONYM"},
                {"恶心", "21522001", "恶心", "FSN"},
                {"乏力", "267036007", "疲劳", "FSN"},
                {"疲劳", "267036007", "疲劳", "SYNONYM"},
                {"虚弱", "267036007", "疲劳", "SYNONYM"},
                {"失眠", "73430006", "失眠", "FSN"},
                {"多梦", "197480006", "多梦", "FSN"},
                {"焦虑", "197480002", "焦虑", "FSN"},
                {"抑郁", "35489007", "抑郁", "FSN"}
        };

        for (String[] mapping : symptomMappings) {
            symptomKeywordMap.put(mapping[0], new String[]{mapping[1], mapping[2], mapping[3]});
        }
    }

    /**
     * 初始化疾病关键词映射
     */
    private void initDiseaseMappings() {
        String[][] diseaseMappings = {
                {"病", "64572001", "疾病", "SYNONYM"},
                {"疾病", "64572001", "疾病", "FSN"},
                {"炎症", "235494008", "炎症", "FSN"},
                {"感染", "15628003", "感染性疾病", "FSN"},
                {"肿瘤", "108369006", "肿瘤", "FSN"},
                {"癌", "363358000", "恶性肿瘤", "FSN"},
                {"溃疡", "399211005", "溃疡", "FSN"},
                {"结石", "236435004", "结石", "FSN"},
                {"硬化", "19943005", "硬化", "FSN"}
        };

        for (String[] mapping : diseaseMappings) {
            diseaseKeywordMap.put(mapping[0], new String[]{mapping[1], mapping[2], mapping[3]});
        }
    }

    /**
     * 根据口语化表达获取SNOMED CT术语信息
     *
     * @param colloquial 口语化表达
     * @return SNOMED CT概念信息数组 [conceptId, preferredTerm, termType]，如果未找到返回null
     */
    public String[] getByColloquial(String colloquial) {
        ensureInitialized();
        return colloquialToSnomedMap.get(colloquial);
    }

    /**
     * 根据医学术语获取SNOMED CT术语信息
     *
     * @param term 医学术语
     * @return SNOMED CT概念信息数组 [conceptId, preferredTerm, termType]，如果未找到返回null
     */
    public String[] getByMedicalTerm(String term) {
        ensureInitialized();
        return medicalTermToSnomedMap.get(term);
    }

    /**
     * 根据症状关键词获取SNOMED CT术语信息
     *
     * @param keyword 症状关键词
     * @return SNOMED CT概念信息数组 [conceptId, preferredTerm, termType]，如果未找到返回null
     */
    public String[] getBySymptomKeyword(String keyword) {
        ensureInitialized();
        return symptomKeywordMap.get(keyword);
    }

    /**
     * 根据疾病关键词获取SNOMED CT术语信息
     *
     * @param keyword 疾病关键词
     * @return SNOMED CT概念信息数组 [conceptId, preferredTerm, termType]，如果未找到返回null
     */
    public String[] getByDiseaseKeyword(String keyword) {
        ensureInitialized();
        return diseaseKeywordMap.get(keyword);
    }

    /**
     * 获取所有口语化表达映射
     *
     * @return 口语化表达映射表
     */
    public Map<String, String[]> getAllColloquialMappings() {
        ensureInitialized();
        return Collections.unmodifiableMap(colloquialToSnomedMap);
    }

    /**
     * 获取所有医学术语映射
     *
     * @return 医学术语映射表
     */
    public Map<String, String[]> getAllMedicalTermMappings() {
        ensureInitialized();
        return Collections.unmodifiableMap(medicalTermToSnomedMap);
    }

    /**
     * 检查是否包含指定的口语化表达
     *
     * @param colloquial 口语化表达
     * @return 是否包含
     */
    public boolean hasColloquialMapping(String colloquial) {
        ensureInitialized();
        return colloquialToSnomedMap.containsKey(colloquial);
    }

    /**
     * 检查是否包含指定的医学术语
     *
     * @param term 医学术语
     * @return 是否包含
     */
    public boolean hasMedicalTermMapping(String term) {
        ensureInitialized();
        return medicalTermToSnomedMap.containsKey(term);
    }
}