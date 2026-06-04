package com.company.contractagent.service;

import com.company.contractagent.domain.ContractDraft;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.TextAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTrPr;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContractDocumentService {
    private static final List<String> BANNED_WORDS = List.of("参考", "参考文献", "通用设备", "不含税金额", "税额");
    private static final String TECH_PROJECT_NAME = "本合同项下软硬件系统开发";
    private final ObjectMapper objectMapper;

    public ContractDocumentService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path generateDocx(ContractDraft draft, Path outputFile) {
        try {
            Files.createDirectories(outputFile.getParent());
            try (XWPFDocument document = new XWPFDocument(); OutputStream outputStream = Files.newOutputStream(outputFile)) {
                configureDocument(document);
                if (isTechDevelopment(draft)) {
                    buildTechDevelopmentContract(document, draft);
                } else {
                    buildPurchaseContract(document, draft);
                }
                document.write(outputStream);
            }
            return outputFile;
        } catch (IOException exception) {
            throw new IllegalStateException("生成 Word 文件失败：" + exception.getMessage(), exception);
        }
    }

    private void buildPurchaseContract(XWPFDocument document, ContractDraft draft) {
        Map<String, Object> fields = draft.fields();
        String partyA = value(fields, "partyA", "甲方");
        String partyB = value(fields, "partyB", "乙方");
        Set<String> renderedClauses = new HashSet<>();

        title(document, "产品购销合同");
        centered(document, "合同编号：" + draft.id());
        blank(document);
        clause(document, "甲方（购买方）：" + partyA);
        clause(document, "乙方（销售方）：" + partyB);
        clauseOnce(document, renderedClauses, "甲乙双方依据平等、自愿、公平和诚实信用原则，就产品购销事项协商一致，订立本合同，并共同遵照执行。");

        heading(document, "第一条 合同标的");
        productTable(document, fields);
        clauseOnce(document, renderedClauses, PurchaseClauseVariantService.targetSummaryClause(fields, draft.id()));
        renderPurchaseVariantSections(document, fields, draft.id(), renderedClauses);

        commonEnding(document, fields, partyA, partyB);
    }

    private void buildTechDevelopmentContract(XWPFDocument document, ContractDraft draft) {
        Map<String, Object> fields = draft.fields();
        String partyA = value(fields, "partyA", value(fields, "client", "甲方"));
        String partyB = value(fields, "partyB", value(fields, "provider", "乙方"));

        title(document, "技术开发合同");
        centered(document, "合同编号：" + draft.id());
        blank(document);
        clause(document, "甲方（委托方）：" + partyA);
        clause(document, "乙方（开发方）：" + partyB);
        clause(document, "甲乙双方就技术开发项目协商一致，订立本合同，并共同遵照执行。");

        heading(document, "第一条 项目背景与项目内容");
        clause(document, "项目名称：" + TECH_PROJECT_NAME + "。");
        productTable(document, fields);
        clause(document, "本项目涉及软件、算法、站点或相关系统能力建设，属于技术开发范畴。乙方应根据甲方业务场景、运行环境和验收要求完成开发、交付和整改。");

        heading(document, "第二条 技术目标");
        multilineClause(document, TechContractDefaults.technicalGoal(fields.get("technicalGoal")));
        clause(document, "乙方不得擅自降低功能范围、技术指标、性能要求或验收条件。确需调整的，应经甲方书面确认。");

        heading(document, "第三条 技术实现路径");
        multilineClause(document, TechContractDefaults.valueOrDefault(fields, "implementationPath", TechContractDefaults.DEFAULT_IMPLEMENTATION_PATH));
        clause(document, "乙方应围绕合同约定的技术目标开展开发工作，未经甲方确认，不得擅自减少核心功能、降低性能指标或改变关键技术实现方式。");

        heading(document, "第四条 项目范围与开发边界");
        clause(document, "1. 本项目开发范围包括需求确认、方案设计、功能开发、接口联调、测试验证、部署交付、试运行支持和验收整改。");
        clause(document, "2. 对于甲方新增需求、第三方系统变化、现场环境变化或政策规范变化引起的范围调整，双方应通过书面方式确认影响和处理方案。");
        clause(document, "3. 乙方应保留必要的需求记录、设计记录、开发记录、测试记录、联调记录、部署记录和整改记录。");

        heading(document, "第五条 开发计划与阶段成果");
        clause(document, "1. 项目计划可划分为需求确认、原型或方案确认、核心功能开发、联调测试、试运行、验收整改和最终交付阶段。");
        clause(document, "2. 每一阶段完成后，乙方应向甲方提交阶段成果、问题清单、处理计划和下一阶段工作安排。");
        clause(document, "3. 甲方有权根据阶段成果对项目进度、功能实现、接口连通、数据准确性和运行稳定性提出确认意见或整改意见。");
        clause(document, "4. 乙方应根据甲方合理反馈完成必要修改，确保项目成果满足合同约定的技术目标和验收标准。");

        heading(document, "第六条 交付成果");
        clause(document, "1. 乙方应交付满足技术目标的软件、算法、站点配置、接口说明、部署说明、测试记录及必要的操作资料。");
        clause(document, "2. 交付地点：甲方工厂内指定地点。");
        clause(document, "3. 乙方交付成果不得侵犯第三方合法权益。");
        clause(document, "4. 乙方应交付与项目成果相匹配的部署说明、接口说明、测试说明、验收说明、操作说明及其他必要资料。");
        clause(document, "5. 文档内容应能够支持甲方完成使用、验收、运维和后续问题定位，不得仅以口头说明替代必要书面资料。");

        heading(document, "第七条 部署、联调与试运行");
        clause(document, "1. 乙方应根据甲方现场或系统环境完成部署、参数配置、接口调试和必要的数据初始化工作。");
        clause(document, "2. 涉及软件、算法、站点或平台能力的，应完成核心功能验证、异常场景验证、性能验证、安全验证和数据准确性验证。");
        clause(document, "3. 试运行期间发现的问题，乙方应根据问题严重程度及时修复，并向甲方说明原因、处理结果和预防措施。");
        clause(document, "4. 试运行期间的问题修复不当然视为新增需求，属于合同约定范围内缺陷或不符合验收标准的，乙方应负责整改。");

        heading(document, "第八条 验收节点");
        multilineClause(document, TechContractDefaults.valueOrDefault(fields, "acceptanceMilestones", TechContractDefaults.DEFAULT_ACCEPTANCE_MILESTONES));
        techAcceptanceTable(document, fields);
        clause(document, "乙方应在每个验收节点提交对应成果和说明材料，甲方可根据合同、需求确认内容和实际运行情况提出验收意见。");

        heading(document, "第九条 验收标准");
        multilineClause(document, TechContractDefaults.valueOrDefault(fields, "acceptanceStandard", TechContractDefaults.DEFAULT_ACCEPTANCE_STANDARD));
        clause(document, "项目未达到验收标准的，甲方有权要求乙方限期整改。整改完成后，甲方可重新组织验收。");

        heading(document, "第十条 价款、税率与付款");
        clause(document, "1. 本合同价税合计为人民币：" + value(fields, "amount", value(fields, "fee", "0")) + "。税率：" + value(fields, "taxRate", "6%") + "。");
        clause(document, "2. 合同付款分 2 期：合同签订后，甲方向乙方支付合同总金额 70%；项目验收通过后 90 至 100 天内，甲方向乙方结清合同总金额未结款项。");
        paymentScheduleTable(document, fields);
        clause(document, "3. 乙方收款前应按甲方要求开具合法有效发票，并提交与付款节点相匹配的交付或验收资料。");

        heading(document, "第十一条 知识产权与成果使用");
        clause(document, "1. 双方应按照合同约定使用项目成果、技术资料、配置资料和相关文档。");
        clause(document, "2. 乙方保证其交付成果不侵犯第三方知识产权、商业秘密或其他合法权益。因乙方交付成果引发第三方权利主张的，乙方应负责处理并承担相应责任。");
        clause(document, "3. 甲方为使用、运维和验收项目成果所需，有权在合同约定范围内使用乙方交付的成果和资料。");

        heading(document, "第十二条 数据安全与保密");
        clause(document, "1. 乙方在项目实施过程中接触甲方数据、账号、接口、配置和业务资料的，应采取合理安全措施，防止泄露、篡改、丢失或被未经授权访问。");
        clause(document, "2. 乙方不得将甲方数据用于本合同以外用途，不得擅自复制、留存、转交或向第三方披露。");
        clause(document, "3. 双方对项目资料、技术方案、数据和商业信息承担保密义务。");
        clause(document, "4. 项目交付时，乙方应配合甲方完成账号、权限、配置、接口和部署资料的交接。");

        heading(document, "第十三条 培训、维护与技术支持");
        clause(document, "1. 乙方应根据项目需要向甲方提供必要的操作说明、维护说明和使用培训，确保甲方能够合理使用交付成果。");
        clause(document, "2. 验收通过后，乙方应在约定维护期内提供问题响应、缺陷修复、配置指导和必要的远程技术支持。");
        clause(document, "3. 对因乙方开发缺陷导致的问题，乙方应负责修复；对甲方新增需求或使用环境变化导致的调整，双方可另行确认处理方式。");

        heading(document, "第十四条 违约责任");
        clause(document, "1. 任一方违反本合同约定，应承担继续履行、采取补救措施或赔偿损失等违约责任。");
        clause(document, "2. 乙方未按约定完成开发、交付、部署、联调、整改或验收支持的，甲方有权要求乙方限期整改并承担相应违约责任。");
        clause(document, "3. 因乙方原因导致项目成果无法达到验收标准或无法正常使用的，乙方应继续整改并承担因此产生的合理损失。");
        techExtendedModules(document);

        commonEnding(document, fields, partyA, partyB);
    }

    private void renderPurchaseVariantSections(
            XWPFDocument document,
            Map<String, Object> fields,
            String contractId,
            Set<String> renderedClauses
    ) {
        List<PurchaseClauseVariantService.ClauseSection> sections = PurchaseClauseVariantService.buildSections(fields, contractId);
        int articleIndex = 2;
        for (PurchaseClauseVariantService.ClauseSection section : sections) {
            heading(document, articleTitle(articleIndex++, section.title()));
            List<String> clauses = section.clauses();
            for (int i = 0; i < clauses.size(); i++) {
                clauseOnce(document, renderedClauses, clauses.get(i));
                if ("价款、税率、发票与付款".equals(section.title()) && i == 1) {
                    paymentScheduleTable(document, fields);
                }
            }
        }
    }

    private String articleTitle(int articleIndex, String title) {
        return "第" + chineseNumber(articleIndex) + "条 " + title;
    }

    private String chineseNumber(int number) {
        return switch (number) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            case 5 -> "五";
            case 6 -> "六";
            case 7 -> "七";
            case 8 -> "八";
            case 9 -> "九";
            case 10 -> "十";
            default -> String.valueOf(number);
        };
    }

    private void clauseOnce(XWPFDocument document, Set<String> renderedClauses, String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        if (normalized.isBlank() || !renderedClauses.add(normalized)) {
            return;
        }
        clause(document, text);
    }

    private void techExtendedModules(XWPFDocument document) {
        heading(document, "第十五条 需求确认与需求变更");
        clause(document, "1. 甲方提出的业务目标、功能要求、接口要求、数据要求、运行环境要求和验收要求，是乙方开展技术开发工作的基础。");
        clause(document, "2. 乙方应在需求确认阶段对项目范围、关键功能、输入输出、接口边界、数据口径、权限控制和异常处理方式进行梳理。");
        clause(document, "3. 需求确认后，如甲方提出新增功能、调整业务流程、改变数据来源或新增第三方系统对接，双方应评估对开发周期、交付成果和验收标准的影响。");
        clause(document, "4. 需求变更应采用书面方式确认；未经确认的口头沟通不得作为扩大乙方开发义务或减少乙方交付义务的依据。");

        heading(document, "第十六条 测试验证要求");
        clause(document, "1. 乙方应根据技术目标和验收标准制定测试方案，测试内容应覆盖核心功能、边界场景、异常场景、接口连通、数据准确性和运行稳定性。");
        clause(document, "2. 涉及算法、软件或站点功能的，应至少完成单元测试、集成测试、联调测试、试运行验证和必要的回归测试。");
        clause(document, "3. 测试过程中发现的问题，乙方应记录问题现象、影响范围、原因分析、处理措施和复测结果。");
        clause(document, "4. 乙方提交验收前，应完成内部测试并确认不存在影响甲方正常验收和使用的重大缺陷。");

        heading(document, "第十七条 接口、数据与运行环境");
        clause(document, "1. 涉及与甲方系统、设备、平台或第三方系统对接的，乙方应明确接口协议、调用方式、数据字段、传输频率、异常处理和日志记录方式。");
        clause(document, "2. 甲方应在合理范围内提供项目实施所需的现场条件、系统环境、测试账号、接口资料或业务说明。");
        clause(document, "3. 因甲方现场环境、第三方系统或外部接口变化导致项目调整的，双方应协商确定处理方式。");
        clause(document, "4. 乙方不得在未经甲方确认的情况下擅自修改甲方系统配置、数据结构、账号权限或运行参数。");

        heading(document, "第十八条 上线、回退与运行保障");
        clause(document, "1. 项目上线或部署前，乙方应配合甲方确认上线条件、部署步骤、验证方式、风险点和必要的回退安排。");
        clause(document, "2. 上线过程中出现异常的，乙方应及时定位原因，并根据影响程度采取修复、回退、临时处置或其他保障措施。");
        clause(document, "3. 试运行期间，乙方应持续跟踪项目运行状态，对影响功能使用、数据准确或系统稳定的问题进行处理。");
        clause(document, "4. 项目最终验收前，乙方应配合甲方完成上线验证、运行记录确认和遗留问题闭环。");

        heading(document, "第十九条 项目文档与资料交接");
        clause(document, "1. 乙方应根据项目特点向甲方提交必要文档，包括需求说明、设计说明、接口说明、部署说明、测试说明、操作说明、验收说明和维护说明。");
        clause(document, "2. 文档应真实反映项目交付成果，不得与实际部署版本、接口配置、功能范围或验收结果明显不一致。");
        clause(document, "3. 甲方发现文档缺失、内容不完整或无法支持使用维护的，有权要求乙方补充完善。");
        clause(document, "4. 项目资料交接完成不免除乙方对已交付成果缺陷、未达验收标准事项和保密义务的责任。");

        heading(document, "第二十条 验收整改与遗留问题");
        clause(document, "1. 甲方验收过程中提出的问题，乙方应区分缺陷、配置问题、数据问题、环境问题和新增需求，并提出处理建议。");
        clause(document, "2. 属于合同范围内的缺陷、不符合技术目标或不满足验收标准的事项，乙方应负责整改。");
        clause(document, "3. 对不影响核心功能和正常使用的遗留问题，双方可确认后续处理计划，但不得影响合同约定的主要验收结论。");
        clause(document, "4. 乙方完成整改后，应提交整改说明和复测结果，甲方可根据实际情况组织复验。");

        heading(document, "第二十一条 培训与知识转移");
        clause(document, "1. 乙方应根据项目使用方式向甲方提供必要培训，培训内容包括系统登录、功能操作、常见问题处理、数据查看和基础维护事项。");
        clause(document, "2. 培训可以采用现场、远程会议、操作文档或录屏说明等形式，但应能够满足甲方合理使用项目成果的需要。");
        clause(document, "3. 对涉及后台配置、接口联调或运维操作的内容，乙方应向甲方指定人员进行必要说明。");
        clause(document, "4. 甲方人员变化不影响乙方已完成培训义务，但乙方可在合理范围内提供必要补充说明。");

        heading(document, "第二十二条 维护响应与缺陷处理");
        clause(document, "1. 维护期内，甲方发现项目成果存在缺陷或运行异常的，可向乙方提出处理要求。");
        clause(document, "2. 乙方应根据问题影响程度及时响应，对影响核心功能、数据准确性或系统稳定性的问题优先处理。");
        clause(document, "3. 因乙方开发、配置、部署或交付资料错误导致的问题，乙方应负责修复。");
        clause(document, "4. 因甲方新增需求、运行环境变化、第三方系统变化或非乙方原因造成的问题，双方可协商确定处理方式。");

        heading(document, "第二十三条 审计、日志与问题追踪");
        clause(document, "1. 项目成果涉及系统操作、接口调用或数据处理的，应根据项目需要保留必要日志或问题追踪信息。");
        clause(document, "2. 乙方在排查问题时接触甲方日志、账号、接口、数据或业务信息的，应遵守本合同保密和数据安全约定。");
        clause(document, "3. 甲方要求乙方说明问题原因、处理过程和修复结果的，乙方应在合理范围内配合。");

        heading(document, "第二十四条 版本管理与交付一致性");
        clause(document, "1. 乙方应对交付版本进行必要管理，确保部署版本、测试版本、验收版本和交付文档之间保持可追溯。");
        clause(document, "2. 未经甲方确认，乙方不得在验收前后擅自替换核心版本、删除关键功能或改变已确认的验收条件。");
        clause(document, "3. 乙方提交最终验收时，应说明交付版本、主要功能、接口范围、部署环境和已知问题处理情况。");
    }

    private void commonEnding(XWPFDocument document, Map<String, Object> fields, String partyA, String partyB) {
        heading(document, "争议解决");
        clause(document, value(fields, "disputeResolution", "因本合同产生的争议，双方应先友好协商；协商不成的，任一方可向甲方所在地有管辖权的人民法院提起诉讼。"));

        heading(document, "其他");
        clause(document, "1. 本合同自双方盖章之日起生效。");
        clause(document, "2. 本合同未尽事宜，双方可另行签署书面补充文件。");
        clause(document, "3. 本合同正文后为盖章页，盖章页不设置表格。");
        clause(document, "4. 本合同不设置法人签字页。");

        XWPFParagraph pageBreak = document.createParagraph();
        pageBreak.createRun().addBreak(BreakType.PAGE);
        title(document, "盖章页");
        blank(document);
        clause(document, "甲方（盖章）：" + partyA);
        blank(document);
        blank(document);
        clause(document, "乙方（盖章）：" + partyB);
        blank(document);
        clause(document, "日期：        年     月     日");
    }

    private void productTable(XWPFDocument document, Map<String, Object> fields) {
        List<Map<String, Object>> items = contractItems(fields);
        XWPFTable table = document.createTable(items.size() + 2, 5);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        applyTableBorders(table);
        XWPFTableRow header = table.getRow(0);
        repeatHeader(header);
        setCell(header.getCell(0), "品名", true);
        setCell(header.getCell(1), "规格型号", true);
        setCell(header.getCell(2), "数量", true);
        setCell(header.getCell(3), "税率", true);
        setCell(header.getCell(4), "价税合计", true);

        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCell(row.getCell(0), sanitizeProductName(value(item, "productName", "合同产品")));
            setCell(row.getCell(1), sanitize(value(item, "specification", "按双方确认规格执行")));
            setCell(row.getCell(2), sanitize(value(item, "quantity", "1")));
            setCell(row.getCell(3), sanitize(value(item, "taxRate", value(fields, "taxRate", "13%"))));
            setCell(row.getCell(4), sanitize(value(item, "amount", value(fields, "amount", value(fields, "fee", "0")))));
        }

        XWPFTableRow totalRow = table.getRow(items.size() + 1);
        setCell(totalRow.getCell(0), "合计", true);
        setCell(totalRow.getCell(1), "", true);
        setCell(totalRow.getCell(2), "", true);
        setCell(totalRow.getCell(3), "", true);
        setCell(totalRow.getCell(4), amountText(totalAmount(items, fields)), true);
    }

    private List<Map<String, Object>> contractItems(Map<String, Object> fields) {
        Object itemsJson = fields.get("itemsJson");
        if (itemsJson != null && !String.valueOf(itemsJson).isBlank()) {
            try {
                return objectMapper.readValue(String.valueOf(itemsJson), new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productName", value(fields, "productName", "合同产品"));
        item.put("specification", value(fields, "specification", "按双方确认规格执行"));
        item.put("quantity", value(fields, "quantity", "1"));
        item.put("taxRate", value(fields, "taxRate", "13%"));
        item.put("amount", value(fields, "amount", value(fields, "fee", "0")));
        return new ArrayList<>(List.of(item));
    }

    private void paymentScheduleTable(XWPFDocument document, Map<String, Object> fields) {
        XWPFTable table = document.createTable(3, 4);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        applyTableBorders(table);
        XWPFTableRow header = table.getRow(0);
        repeatHeader(header);
        setCell(header.getCell(0), "付款期次", true);
        setCell(header.getCell(1), "付款条件", true);
        setCell(header.getCell(2), "付款比例", true);
        setCell(header.getCell(3), "付款说明", true);

        XWPFTableRow first = table.getRow(1);
        setCell(first.getCell(0), "第一期");
        setCell(first.getCell(1), "合同签订后");
        setCell(first.getCell(2), "70%");
        setCell(first.getCell(3), "按合同价税合计计算");

        XWPFTableRow second = table.getRow(2);
        setCell(second.getCell(0), "第二期");
        setCell(second.getCell(1), "项目验收通过后 90 至 100 天内");
        setCell(second.getCell(2), "未结款项");
        setCell(second.getCell(3), "结清合同总金额未付款项，合同价税合计：" + sanitize(value(fields, "amount", value(fields, "fee", "0"))));
    }

    private void techAcceptanceTable(XWPFDocument document, Map<String, Object> fields) {
        XWPFTable table = document.createTable(5, 4);
        table.setWidth("100%");
        table.setTableAlignment(TableRowAlign.CENTER);
        applyTableBorders(table);
        XWPFTableRow header = table.getRow(0);
        repeatHeader(header);
        setCell(header.getCell(0), "验收节点", true);
        setCell(header.getCell(1), "主要内容", true);
        setCell(header.getCell(2), "交付资料", true);
        setCell(header.getCell(3), "确认方式", true);

        setAcceptanceRow(table.getRow(1), "需求确认", "确认技术目标、功能范围、接口边界和数据口径", "需求确认记录、方案说明", "甲方确认");
        setAcceptanceRow(table.getRow(2), "阶段演示", "展示核心功能、算法或软件阶段成果", "演示记录、问题清单", "双方确认问题清单");
        setAcceptanceRow(table.getRow(3), "联调测试", "完成接口连通、数据准确性和异常场景验证", "测试记录、联调记录", "甲方确认测试结果");
        setAcceptanceRow(table.getRow(4), "最终验收", sanitize(TechContractDefaults.valueOrDefault(fields, "acceptanceStandard", "功能完整、接口连通、数据准确、运行稳定")), "验收报告、部署说明、操作说明", "验收通过后进入尾款周期");
    }

    private static void setAcceptanceRow(XWPFTableRow row, String node, String content, String material, String method) {
        setCell(row.getCell(0), node);
        setCell(row.getCell(1), content);
        setCell(row.getCell(2), material);
        setCell(row.getCell(3), method);
    }

    private static void applyTableBorders(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 6, 0, "000000");
    }

    private static void repeatHeader(XWPFTableRow row) {
        CTTrPr trPr = row.getCtRow().isSetTrPr() ? row.getCtRow().getTrPr() : row.getCtRow().addNewTrPr();
        trPr.addNewTblHeader();
    }

    private static void setCell(XWPFTableCell cell, String text) {
        setCell(cell, text, false);
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("SimSun");
        run.setFontSize(10);
        run.setBold(bold);
        run.setText(text);
    }

    private static void configureDocument(XWPFDocument document) {
        document.getProperties().getCoreProperties().setTitle("合同文件");
    }

    private static void title(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(180);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("SimHei");
        run.setFontSize(20);
        run.setText(text);
    }

    private static void heading(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(160);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontFamily("SimHei");
        run.setFontSize(13);
        run.setText(text);
    }

    private static void centered(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(80);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("SimSun");
        run.setFontSize(10);
        run.setText(sanitize(text));
    }

    private static void clause(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setVerticalAlignment(TextAlignment.AUTO);
        paragraph.setFirstLineIndent(420);
        paragraph.setSpacingBetween(1.5);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("SimSun");
        run.setFontSize(11);
        run.setText(sanitize(text));
    }

    private static void multilineClause(XWPFDocument document, String text) {
        String[] lines = sanitize(text).split("\\R+");
        for (String line : lines) {
            if (!line.isBlank()) {
                clause(document, line);
            }
        }
    }

    private static void blank(XWPFDocument document) {
        document.createParagraph().createRun().setText("");
    }

    private static boolean isTechDevelopment(ContractDraft draft) {
        String templateCode = draft.templateCode() == null ? "" : draft.templateCode().toUpperCase(Locale.ROOT);
        String productName = value(draft.fields(), "productName", "");
        return templateCode.contains("TECH")
                || productName.contains("站点")
                || productName.contains("软件")
                || productName.contains("算法");
    }

    private static String value(Map<String, Object> fields, String key, String fallback) {
        Object value = fields.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private static BigDecimal totalAmount(List<Map<String, Object>> items, Map<String, Object> fields) {
        BigDecimal total = items.stream()
                .map(item -> money(value(item, "amount", "0")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            return total;
        }
        return money(value(fields, "amount", value(fields, "fee", "0")));
    }

    private static BigDecimal money(String value) {
        String text = value == null ? "" : value.replace(",", "").replace("¥", "").trim();
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal amount = new BigDecimal(matcher.group(1));
            if (text.contains("万")) {
                amount = amount.multiply(BigDecimal.valueOf(10_000));
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String amountText(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + "元";
    }

    private static String sanitizeProductName(String value) {
        return sanitize(value).replace("通用设备", "专用产品");
    }

    private static String sanitize(String value) {
        String result = value == null ? "" : value;
        for (String bannedWord : BANNED_WORDS) {
            result = result.replace(bannedWord, "");
        }
        return result.trim();
    }
}
