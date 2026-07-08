-- V8: 药物-人群禁忌规则表 + 文档分块禁忌标注字段
-- 方案：规则引擎 + 知识库的离线预处理层

-- 1. 药物-人群禁忌规则表
CREATE TABLE drug_population_contraindication (
    id BIGINT PRIMARY KEY,
    drug_name VARCHAR(200) NOT NULL COMMENT '药物通用名',
    population_name VARCHAR(100) NOT NULL COMMENT '人群名称',
    contraindication_type ENUM('ABSOLUTE','RELATIVE','CAUTION','UNCLEAR') NOT NULL
        COMMENT '禁忌类型: ABSOLUTE=绝对禁用, RELATIVE=相对禁忌, CAUTION=慎用, UNCLEAR=尚不明确',
    evidence_level ENUM('GUIDELINE','DRUG_LABEL','RCT','META_ANALYSIS','EXPERT_CONSENSUS','CASE_REPORT')
        NOT NULL COMMENT '证据等级',
    description TEXT COMMENT '禁忌说明',
    source VARCHAR(500) COMMENT '来源（说明书/指南名称）',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_drug_population (drug_name, population_name),
    INDEX idx_drug_name (drug_name),
    INDEX idx_population_name (population_name),
    INDEX idx_contraindication_type (contraindication_type)
) COMMENT '药物-人群禁忌规则表';

-- 2. 文档分块增加禁忌标注字段
ALTER TABLE document_chunks
    ADD COLUMN has_contraindication TINYINT(1) DEFAULT 0
        COMMENT '是否命中禁忌规则（0=否 1=是，用于快速过滤）',
    ADD COLUMN contraindication_info TEXT DEFAULT NULL
        COMMENT '禁忌标注详情JSON: [{drug, population, type, evidence, description}]';

-- 3. 预置种子数据（来自药品说明书和权威指南）

-- 孕妇绝对禁忌
INSERT INTO drug_population_contraindication (id, drug_name, population_name, contraindication_type, evidence_level, description, source) VALUES
(1, '利巴韦林', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '利巴韦林有致畸作用，孕妇及可能怀孕的女性禁用', '利巴韦林注射液说明书'),
(2, '异维A酸', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '异维A酸高度致畸，妊娠期及备孕期女性绝对禁用', '异维A酸软胶囊说明书'),
(3, '沙利度胺', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '沙利度胺有强致畸性，妊娠期女性绝对禁用', '沙利度胺片说明书'),
(4, '甲氨蝶呤', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '甲氨蝶呤具致畸性和胚胎毒性，孕妇禁用', '甲氨蝶呤片说明书'),
(5, '阿托伐他汀', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '他汀类药物可能影响胎儿发育，孕妇禁用', '阿托伐他汀钙片说明书'),
(6, '瑞舒伐他汀', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '他汀类药物孕妇禁用', '瑞舒伐他汀钙片说明书'),
(7, '华法林', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', '华法林可致胎儿华法林综合征，妊娠早期禁用', '华法林钠片说明书'),
(8, '卡托普利', '孕妇', 'ABSOLUTE', 'GUIDELINE', 'ACEI类药物可致胎儿肾发育异常和羊水过少，妊娠中晚期禁用', 'ACOG临床指南'),
(9, '依那普利', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', 'ACEI类药物孕妇禁用，可致胎儿损伤', '依那普利片说明书'),
(10, '缬沙坦', '孕妇', 'ABSOLUTE', 'DRUG_LABEL', 'ARB类药物可致胎儿肾损害，孕妇禁用', '缬沙坦胶囊说明书');

-- 孕妇慎用/相对禁忌
INSERT INTO drug_population_contraindication (id, drug_name, population_name, contraindication_type, evidence_level, description, source) VALUES
(11, '阿司匹林', '孕妇', 'CAUTION', 'GUIDELINE', '妊娠晚期大剂量可致胎儿动脉导管早闭，需慎用；低剂量需遵医嘱', 'ACOG临床指南'),
(12, '布洛芬', '孕妇', 'CAUTION', 'DRUG_LABEL', '妊娠晚期使用可致胎儿动脉导管早闭和羊水过少', '布洛芬缓释胶囊说明书'),
(13, '对乙酰氨基酚', '孕妇', 'CAUTION', 'DRUG_LABEL', '长期大量使用可能影响胎儿发育，短期常规剂量相对安全', '对乙酰氨基酚片说明书'),
(14, '甲硝唑', '孕妇', 'CAUTION', 'DRUG_LABEL', '妊娠早期应避免使用，中晚期权衡利弊后可使用', '甲硝唑片说明书'),
(15, '左氧氟沙星', '孕妇', 'RELATIVE', 'DRUG_LABEL', '喹诺酮类可能影响胎儿软骨发育，妊娠期一般不宜使用', '左氧氟沙星注射液说明书');

-- 儿童禁忌
INSERT INTO drug_population_contraindication (id, drug_name, population_name, contraindication_type, evidence_level, description, source) VALUES
(16, '四环素', '儿童', 'ABSOLUTE', 'DRUG_LABEL', '四环素可致牙齿黄染和骨骼发育抑制，8岁以下儿童禁用', '盐酸四环素片说明书'),
(17, '多西环素', '儿童', 'ABSOLUTE', 'DRUG_LABEL', '四环素类药物可致恒齿变色，8岁以下儿童禁用', '多西环素片说明书'),
(18, '左氧氟沙星', '儿童', 'RELATIVE', 'DRUG_LABEL', '喹诺酮类药物可能影响软骨发育，18岁以下一般不宜使用', '左氧氟沙星注射液说明书'),
(19, '阿司匹林', '儿童', 'ABSOLUTE', 'GUIDELINE', '病毒感染患儿使用阿司匹林可能引发Reye综合征，16岁以下慎用或禁用', 'AAP临床指南'),
(20, '可待因', '儿童', 'ABSOLUTE', 'DRUG_LABEL', '12岁以下儿童禁用，有呼吸抑制风险', '磷酸可待因片说明书');

-- 老人禁忌/慎用
INSERT INTO drug_population_contraindication (id, drug_name, population_name, contraindication_type, evidence_level, description, source) VALUES
(21, '地西泮', '老人', 'CAUTION', 'GUIDELINE', '老年患者对苯二氮卓类敏感性增加，应减量使用，增加跌倒风险', 'Beers标准(AGS)'),
(22, '阿米替林', '老人', 'CAUTION', 'GUIDELINE', '三环类抗抑郁药有较强抗胆碱能作用，老年患者应避免使用', 'Beers标准(AGS)'),
(23, '哌替啶', '老人', 'CAUTION', 'GUIDELINE', '代谢产物蓄积可致神经毒性和癫痫发作，老年患者应避免使用', 'Beers标准(AGS)'),
(24, '吲哚美辛', '老人', 'CAUTION', 'GUIDELINE', 'NSAIDs中CNS不良反应最突出，老年患者应避免长期使用', 'Beers标准(AGS)');

-- 哺乳期禁忌
INSERT INTO drug_population_contraindication (id, drug_name, population_name, contraindication_type, evidence_level, description, source) VALUES
(25, '利巴韦林', '哺乳期', 'ABSOLUTE', 'DRUG_LABEL', '利巴韦林可经乳汁排泄，哺乳期妇女禁用', '利巴韦林注射液说明书'),
(26, '甲氨蝶呤', '哺乳期', 'ABSOLUTE', 'DRUG_LABEL', '甲氨蝶呤可经乳汁排泄，哺乳期禁用', '甲氨蝶呤片说明书'),
(27, '环磷酰胺', '哺乳期', 'ABSOLUTE', 'DRUG_LABEL', '环磷酰胺可经乳汁排泄，哺乳期禁用', '环磷酰胺注射液说明书'),
(28, '碘造影剂', '哺乳期', 'CAUTION', 'GUIDELINE', '碘造影剂可经乳汁排泄，使用后建议暂停哺乳24-48小时', 'ACR造影剂指南');
