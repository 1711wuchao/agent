package com.company.contractagent.service;

import java.util.Map;

public final class TechContractDefaults {
    public static final String TECH_SPECIFICATION_NOTE = "说明：以上技术指标为双方初步约定，具体指标以项目启动后双方签字确认的《技术规格书》为准。";

    public static final String DEFAULT_TECHNICAL_GOAL = """
            本项目技术目标为完成合同标的相关软件、算法、站点或系统能力的开发、联调、部署和稳定运行。系统应满足甲方业务场景下的基本使用要求，具备稳定的数据处理、接口连通和现场运行能力。
            双方初步约定以下通用技术指标：
            1. 系统常规操作响应时间不超过 3 秒；
            2. 系统应支持不少于 100 名并发用户访问或使用；
            3. 系统可用率不低于 99.5%；
            4. 因系统自身原因导致的故障，乙方应在 4 小时内响应并启动恢复处理。
            """;

    public static final String DEFAULT_IMPLEMENTATION_PATH = "乙方应完成需求确认、方案设计、原型或功能确认、开发实施、接口联调、测试验证、部署交付、试运行支持和验收整改。";

    public static final String DEFAULT_ACCEPTANCE_MILESTONES = "验收节点包括需求确认、阶段演示、联调测试、试运行和最终验收。各节点的确认结果可作为最终验收依据。";

    public static final String DEFAULT_ACCEPTANCE_STANDARD = "验收标准包括功能完整性、运行稳定性、数据准确性、接口连通性、响应性能、安全性、可维护性及甲方确认的项目需求。";

    private TechContractDefaults() {
    }

    public static String technicalGoal(Object value) {
        String text = normalize(value);
        if (text.isBlank()) {
            return DEFAULT_TECHNICAL_GOAL + "\n" + TECH_SPECIFICATION_NOTE;
        }
        if (text.contains("《技术规格书》") || text.contains("技术规格书")) {
            return text;
        }
        return text + "\n" + TECH_SPECIFICATION_NOTE;
    }

    public static String valueOrDefault(Map<String, Object> fields, String key, String fallback) {
        Object value = fields.get(key);
        String text = normalize(value);
        return text.isBlank() ? fallback : text;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
