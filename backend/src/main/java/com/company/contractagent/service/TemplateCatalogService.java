package com.company.contractagent.service;

import com.company.contractagent.domain.ContractTemplate;
import com.company.contractagent.domain.TemplateField;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TemplateCatalogService {
    private final List<ContractTemplate> templates = List.of(
            new ContractTemplate("PURCHASE_STANDARD", "产品购销合同-标准版", "purchase-standard.docx", "v1.1", true, purchaseFields()),
            new ContractTemplate("PURCHASE_HARDWARE", "产品购销合同-硬件产品版", "purchase-hardware.docx", "v1.1", true, purchaseFields()),
            new ContractTemplate("TECH_SOFTWARE", "技术开发合同-软件类", "tech-software.docx", "v1.1", true, techDevelopmentFields()),
            new ContractTemplate("TECH_ALGORITHM", "技术开发合同-算法类", "tech-algorithm.docx", "v1.1", true, techDevelopmentFields()),
            new ContractTemplate("TECH_SITE", "技术开发合同-站点类", "tech-site.docx", "v1.1", true, techDevelopmentFields()),
            new ContractTemplate("PURCHASE", "产品购销合同", "purchase-contract.docx", "v1.0", true, purchaseFields()),
            new ContractTemplate("TECH_DEVELOPMENT", "技术开发合同", "tech-development-contract.docx", "v1.0", true, techDevelopmentFields()),
            new ContractTemplate("SALES", "销售合同", "sales-contract.docx", "v1.0", true, purchaseFields()),
            new ContractTemplate("SERVICE", "服务合同", "service-contract.docx", "v1.0", true, serviceFields()),
            new ContractTemplate("NDA", "保密协议", "nda.docx", "v1.0", true, ndaFields()),
            new ContractTemplate("SUPPLEMENT", "补充协议", "supplement.docx", "v1.0", true, supplementFields())
    );

    public List<ContractTemplate> listTemplates() {
        return templates;
    }

    public Optional<ContractTemplate> findByCode(String code) {
        return templates.stream()
                .filter(template -> template.code().equalsIgnoreCase(code))
                .findFirst();
    }

    public Optional<ContractTemplate> findByName(String contractType) {
        return templates.stream()
                .filter(template -> template.name().equals(contractType))
                .findFirst();
    }

    private static List<TemplateField> purchaseFields() {
        return List.of(
                new TemplateField("partyA", "甲方/购买方名称", "text", true, "请输入购买方公司全称"),
                new TemplateField("partyB", "乙方/销售方名称", "text", true, "请输入销售方公司全称"),
                new TemplateField("productName", "品名", "text", true, "品名不得包含通用设备字样"),
                new TemplateField("specification", "规格型号", "text", true, "请输入规格型号"),
                new TemplateField("quantity", "数量", "text", true, "请输入数量"),
                new TemplateField("taxRate", "税率", "text", true, "例如：13%"),
                new TemplateField("amount", "价税合计", "money", true, "请输入含税合同总金额"),
                new TemplateField("invoiceDate", "开票日期", "date", true, "合同日期会按开票日期向前推三个月"),
                new TemplateField("acceptanceStandard", "验收标准", "textarea", false, "硬件产品默认补充国家验收标准"),
                new TemplateField("disputeResolution", "争议解决方式", "text", false, "例如：提交甲方所在地人民法院")
        );
    }

    private static List<TemplateField> techDevelopmentFields() {
        return List.of(
                new TemplateField("partyA", "甲方/委托方名称", "text", true, "请输入委托方公司全称"),
                new TemplateField("partyB", "乙方/开发方名称", "text", true, "请输入开发方公司全称"),
                new TemplateField("productName", "项目/产品名称", "text", true, "品名包含站点、软件或算法时使用技术开发合同"),
                new TemplateField("quantity", "数量", "text", true, "请输入数量"),
                new TemplateField("specification", "规格型号", "text", true, "请输入规格型号或版本"),
                new TemplateField("taxRate", "税率", "text", true, "例如：6%"),
                new TemplateField("amount", "价税合计", "money", true, "请输入含税合同总金额"),
                new TemplateField("invoiceDate", "开票日期", "date", true, "合同日期会按开票日期向前推三个月"),
                new TemplateField("technicalGoal", "技术目标", "textarea", false, "可不填，系统会补充通用技术指标"),
                new TemplateField("implementationPath", "技术实现路径", "textarea", false, "可不填，系统会补充通用实现路径"),
                new TemplateField("acceptanceMilestones", "验收节点", "textarea", false, "可不填，系统会补充通用验收节点"),
                new TemplateField("acceptanceStandard", "验收标准", "textarea", false, "可不填，系统会补充通用验收标准"),
                new TemplateField("disputeResolution", "争议解决方式", "text", false, "例如：提交甲方所在地人民法院")
        );
    }

    private static List<TemplateField> serviceFields() {
        return List.of(
                new TemplateField("client", "委托方", "text", true, "请输入委托方名称"),
                new TemplateField("provider", "服务方", "text", true, "请输入服务方名称"),
                new TemplateField("serviceScope", "服务内容", "textarea", true, "请输入服务范围"),
                new TemplateField("servicePeriod", "服务周期", "text", true, "例如：2026-07-01 至 2026-12-31"),
                new TemplateField("fee", "服务费用", "money", true, "请输入费用")
        );
    }

    private static List<TemplateField> ndaFields() {
        return List.of(
                new TemplateField("disclosingParty", "披露方", "text", true, "请输入披露方"),
                new TemplateField("receivingParty", "接收方", "text", true, "请输入接收方"),
                new TemplateField("confidentialScope", "保密范围", "textarea", true, "请输入保密信息范围"),
                new TemplateField("term", "保密期限", "text", true, "例如：三年"),
                new TemplateField("liability", "违约责任", "textarea", false, "请输入特殊违约责任")
        );
    }

    private static List<TemplateField> supplementFields() {
        return List.of(
                new TemplateField("originalContractNo", "原合同编号", "text", true, "请输入原合同编号"),
                new TemplateField("partyA", "甲方名称", "text", true, "请输入甲方公司全称"),
                new TemplateField("partyB", "乙方名称", "text", true, "请输入乙方公司全称"),
                new TemplateField("changeItems", "变更事项", "textarea", true, "请输入补充或变更内容"),
                new TemplateField("effectiveDate", "生效日期", "date", true, "请选择生效日期")
        );
    }
}
