package com.company.contractagent.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 产品购销合同条款变体池。
 *
 * 选择策略：
 * 1. 使用合同编号、甲乙方、产品名称、规格型号、金额区间生成稳定种子；
 * 2. 同一份合同重复生成时选择结果保持一致；
 * 3. 不同产品、不同金额、不同公司会命中不同条款表述；
 * 4. 仅调整不影响法律逻辑的条款表达与局部顺序。
 */
public final class PurchaseClauseVariantService {
    private PurchaseClauseVariantService() {
    }

    public record ClauseSection(String title, List<String> clauses) {
    }

    public static List<ClauseSection> buildSections(Map<String, Object> fields, String contractId) {
        Context context = Context.from(fields, contractId);
        List<ClauseSection> sections = new ArrayList<>();
        sections.add(new ClauseSection("包装、运输与交付", choose(PACKAGING_VARIANTS, context, "packaging")));
        sections.add(new ClauseSection("验收标准与验收流程", choose(ACCEPTANCE_VARIANTS, context, "acceptance")));
        sections.add(paymentSection(fields, context));
        sections.add(new ClauseSection("质量保证与售后服务", choose(WARRANTY_VARIANTS, context, "warranty")));

        ClauseSection changeForce = new ClauseSection("合同变更、解除与不可抗力", choose(CHANGE_FORCE_VARIANTS, context, "changeForce"));
        ClauseSection liability = new ClauseSection("违约责任", choose(LIABILITY_VARIANTS, context, "liability"));
        if (Math.floorMod(context.seed(), 2) == 0) {
            sections.add(changeForce);
            sections.add(liability);
        } else {
            sections.add(liability);
            sections.add(changeForce);
        }
        return sections;
    }

    public static String targetSummaryClause(Map<String, Object> fields, String contractId) {
        Context context = Context.from(fields, contractId);
        String product = context.productName().isBlank() ? "合同标的" : context.productName();
        return choose(List.of(
                List.of("双方确认，本合同表格所列" + product + "的品名、规格型号、数量、税率和价税合计，是交付、验收及结算的直接依据；未写入本合同的口头说明不作为变更产品信息的依据。"),
                List.of("本合同项下产品以第一条表格记载为准，甲方按表格中的规格型号、数量、税率和价税合计进行验收与结算，乙方交付内容应与该等记载保持一致。"),
                List.of("第一条表格构成本合同产品范围的核心内容。除双方另行书面确认外，任何补充说明均不得改变表格载明的品名、规格型号、数量、税率和价税合计。"),
                List.of("双方以本合同列明的产品信息作为后续发货、查验、验收和付款的基础，乙方不得以同类产品、近似规格或其他口头约定替代合同列明内容。")
        ), context, "target").get(0);
    }

    private static ClauseSection paymentSection(Map<String, Object> fields, Context context) {
        String amount = value(fields, "amount", "0");
        String taxRate = value(fields, "taxRate", "13%");
        List<List<String>> variants = List.of(
                List.of(
                        "1. 本合同价税合计为人民币：" + amount + "。税率：" + taxRate + "。",
                        "2. 合同付款分 2 期：合同签订后，甲方向乙方支付合同总金额 70%；项目验收通过后 90 至 100 天内，甲方向乙方结清合同总金额未结款项。",
                        "3. 乙方应根据合同价税合计、税率、品名、规格型号和数量向甲方开具合法有效发票，发票内容应与合同及实际交付内容保持一致。",
                        "4. 甲方付款前可要求乙方提交合同、验收资料、发票、收款账户信息及其他必要结算资料；乙方提交资料不完整的，付款期限相应顺延。",
                        "5. 双方确认，本合同项下款项均以价税合计口径进行结算，合同正文不列示被禁止列示的拆分金额表述。"
                ),
                List.of(
                        "1. 本合同结算口径为价税合计，金额为人民币：" + amount + "；适用税率为：" + taxRate + "。",
                        "2. 付款分为两期：合同签订后支付合同总金额的 70%；甲方验收通过后 90 至 100 天内支付剩余未结款项。",
                        "3. 乙方开具发票时，应使发票项目与合同中的品名、规格型号、数量、税率及实际交付内容一致。",
                        "4. 涉及付款节点的，乙方应配合提交发票、验收资料及收款账户信息；资料缺失导致无法付款的，相应期限顺延。",
                        "5. 双方不在合同正文中拆分列示被禁止列示的金额项目，结算以价税合计为准。"
                ),
                List.of(
                        "1. 双方确认合同价税合计为人民币：" + amount + "，税率为：" + taxRate + "。",
                        "2. 甲方在合同签订后支付合同总金额 70%；在项目验收通过后 90 至 100 天内完成剩余款项支付。",
                        "3. 乙方应按合同载明的产品信息和适用税率开具合法有效发票，并保证票面内容与实际交付一致。",
                        "4. 甲方有权在付款前核对合同、发票、验收资料和收款账户信息；乙方应及时补齐缺失资料。",
                        "5. 本合同项下付款、开票和结算均围绕价税合计金额执行。"
                )
        );
        return new ClauseSection("价款、税率、发票与付款", choose(variants, context, "payment"));
    }

    private static final List<List<String>> PACKAGING_VARIANTS = List.of(
            List.of(
                    "1. 乙方应根据产品特性采取适合运输、搬运和短期存放的包装方式，确保产品在运输过程中不因包装不当发生损坏、受潮、污染、变形或部件缺失。",
                    "2. 产品随货资料应包括装箱清单、合格证明、必要的产品说明、安装说明、质保说明及甲方验收所需的其他资料。",
                    "3. 交付地点：甲方工厂内指定地点。除双方另有书面确认外，合同中不另行列示企业地址或法人信息。",
                    "4. 产品到达交付地点后，甲方可根据外包装状态、数量、规格型号和随货资料进行初步查验。初步查验不代表最终验收合格。",
                    "5. 因乙方包装、运输安排或交付资料不完整导致甲方无法验收或使用的，乙方应及时补正，并承担因此产生的合理费用。"
            ),
            List.of(
                    "1. 乙方应结合产品材质、规格型号和运输距离选择适当包装，包装应满足防震、防潮、防挤压和便于清点的基本要求。",
                    "2. 每批交付产品应随附装箱清单、合格证明及必要的安装或使用资料，便于甲方完成到货核对。",
                    "3. 交付地点统一为甲方工厂内指定地点，合同正文不列示企业地址、法人信息或其他非必要信息。",
                    "4. 甲方签收时可先核对包装状态、数量和随货资料；该签收行为不当然视为最终验收通过。",
                    "5. 包装破损、资料缺失或运输安排不当影响甲方查验的，乙方应及时补交、换发或采取其他补救措施。"
            ),
            List.of(
                    "1. 乙方负责将产品安全送达甲方工厂内指定地点，并保证包装方式能够满足通常运输、装卸和短期保管需要。",
                    "2. 对易受潮、易磕碰或含电子部件的产品，乙方应采取必要防护措施，避免因运输环境导致性能异常或部件缺失。",
                    "3. 随货资料至少应支持甲方核对品名、规格型号、数量、合格状态和基础使用要求。",
                    "4. 甲方可在到货后进行外观和数量核对；发现异常的，有权要求乙方说明原因并提出处理方案。",
                    "5. 乙方不得以已交由承运方为由免除其对包装、资料完整性及交付一致性的责任。"
            ),
            List.of(
                    "1. 产品包装应与合同标的的运输风险相匹配，并满足防潮、防尘、防压和搬运识别需要。",
                    "2. 乙方应在外包装或随货资料中标明必要识别信息，确保甲方能够对应合同中的产品名称和规格型号进行核验。",
                    "3. 交付地点为甲方工厂内指定地点，双方另有书面确认的除外。",
                    "4. 甲方到货查验重点包括包装状态、数量、规格型号和随货资料完整性；最终验收仍按合同约定执行。",
                    "5. 因包装或运输原因造成产品损坏、缺件或无法正常验收的，乙方应负责处理。"
            )
    );

    private static final List<List<String>> ACCEPTANCE_VARIANTS = List.of(
            List.of(
                    "1. 甲方收到产品后，有权按照合同约定的品名、规格型号、数量、税率、价税合计、质量要求及国家现行验收标准进行验收。",
                    "2. 硬件产品验收应符合国家现行质量、计量、安全、电气、环保及行业验收标准；无强制标准的，按双方确认的技术参数、产品说明及合同约定执行。",
                    "3. 验收内容包括外观、数量、规格型号、运行状态、随机资料、合格证明、技术参数、安装适配情况及双方确认的其他内容。",
                    "4. 如产品存在数量短缺、规格型号不符、资料缺失、运行异常或质量不符合约定等情形，甲方有权要求乙方补齐、更换、维修、重新交付或采取其他补救措施。",
                    "5. 乙方完成整改后，甲方有权重新组织验收；重新验收合格前，甲方可暂缓支付与该部分产品相关的未付款项。"
            ),
            List.of(
                    "1. 甲方验收时以合同表格、随货资料、合格证明及国家现行验收标准作为主要依据。",
                    "2. 对属于硬件产品的合同标的，应满足国家现行质量、安全、电气、计量、环保及相关行业标准；没有强制标准的，按合同约定和产品说明执行。",
                    "3. 验收可覆盖数量清点、规格型号核对、外观检查、基础运行状态、资料完整性和安装适配情况。",
                    "4. 发现不符合约定的，甲方可要求乙方在合理期限内补齐、维修、更换或重新交付。",
                    "5. 整改完成并经甲方重新验收合格前，相关产品不得作为最终验收合格处理。"
            ),
            List.of(
                    "1. 到货验收分为到货核对和最终验收，甲方可根据合同约定分别确认数量、规格型号、资料和质量状态。",
                    "2. 产品质量及安全要求应符合国家现行验收标准和行业通常要求，乙方不得交付低于合同约定用途的产品。",
                    "3. 验收过程中需要测试、安装或联调的，乙方应按甲方合理要求提供必要配合。",
                    "4. 产品存在规格型号不符、缺件、资料不完整或无法正常使用等问题的，甲方有权要求乙方整改。",
                    "5. 因整改导致验收延后的，不影响甲方依据合同付款节点进行审核。"
            ),
            List.of(
                    "1. 甲方有权围绕合同载明的产品名称、规格型号、数量、税率和价税合计开展验收确认。",
                    "2. 硬件类产品应符合国家现行质量、安全、电气、环保及行业验收标准，并达到通常使用所需的稳定性要求。",
                    "3. 随货合格证明、产品说明和必要技术资料属于验收资料的一部分，乙方应保证真实、完整。",
                    "4. 对影响安装、使用或验收结论的问题，乙方应及时说明并采取补救措施。",
                    "5. 甲方重新验收合格后，双方再按合同付款节点办理后续结算。"
            )
    );

    private static final List<List<String>> WARRANTY_VARIANTS = List.of(
            List.of(
                    "1. 乙方保证所供产品不存在权利瑕疵和质量瑕疵，能够满足合同约定用途。",
                    "2. 乙方应在质保期内对非甲方原因造成的产品故障提供维修、更换、远程支持或现场支持。",
                    "3. 甲方提出质量问题后，乙方应及时响应并给出处理方案；影响甲方正常使用的，乙方应优先安排处理。",
                    "4. 经多次维修仍不能满足合同约定用途的，甲方有权要求乙方更换同等或更高配置产品，或要求乙方承担相应违约责任。"
            ),
            List.of(
                    "1. 乙方应保证交付产品来源合法、质量合格，并与合同列明的规格型号和用途相匹配。",
                    "2. 因产品自身质量问题影响甲方使用的，乙方应提供维修、更换、技术支持或其他合理补救。",
                    "3. 甲方反馈故障或质量异常后，乙方应在合理时间内响应，说明处理路径和预计完成时间。",
                    "4. 经处理后仍不能达到合同约定用途的，甲方可要求更换产品或依法追究乙方责任。"
            ),
            List.of(
                    "1. 乙方交付的产品应具备正常使用所需的质量、性能和配套资料。",
                    "2. 质保期内出现非甲方原因导致的故障、损坏或性能异常，乙方应负责处理。",
                    "3. 对影响生产、安装或项目验收的问题，乙方应优先安排维修、更换或现场支持。",
                    "4. 因同一问题反复出现导致甲方无法正常使用的，乙方应提出根本性整改方案。"
            ),
            List.of(
                    "1. 乙方应确保产品不存在影响甲方正常验收和使用的质量缺陷。",
                    "2. 售后服务可根据问题性质采取远程指导、寄修、更换、现场支持或补发配件等方式。",
                    "3. 甲方应配合提供故障现象、使用环境和必要记录，乙方据此判断原因并处理。",
                    "4. 因乙方产品质量问题产生的合理整改责任，由乙方承担。"
            )
    );

    private static final List<List<String>> CHANGE_FORCE_VARIANTS = List.of(
            List.of(
                    "1. 合同履行过程中，如需变更产品范围、数量、规格型号、交付安排或验收要求，应由双方书面确认后执行。",
                    "2. 一方严重违反合同义务，经守约方催告后仍未在合理期限内整改的，守约方有权依法解除合同并要求违约方承担责任。",
                    "3. 因不可抗力导致合同无法按期履行的，受影响方应及时通知另一方，并在合理期限内提供说明材料；双方应协商确定后续履行安排。"
            ),
            List.of(
                    "1. 产品范围、数量、规格型号、交付时间或验收条件发生调整的，应经双方书面确认。",
                    "2. 未经书面确认的单方变更，不作为增加或减少任何一方合同义务的依据。",
                    "3. 不可抗力影响履约的，受影响方应及时通知对方，并在影响消除后继续协商可行的履行安排。"
            ),
            List.of(
                    "1. 双方可根据项目实际情况对交付批次、验收安排或资料提交方式作出书面调整。",
                    "2. 一方根本违约并导致合同目的无法实现的，守约方可依法解除合同并主张责任。",
                    "3. 因自然灾害、重大公共事件或其他不可抗力导致履约迟延的，双方应根据影响程度协商处理。"
            )
    );

    private static final List<List<String>> LIABILITY_VARIANTS = List.of(
            List.of(
                    "1. 任一方违反本合同约定，应承担继续履行、采取补救措施或赔偿损失等违约责任。",
                    "2. 乙方逾期交付、交付不合格或未按约定完成整改的，甲方有权要求乙方承担相应违约责任。",
                    "3. 甲方无正当理由逾期付款的，应在乙方催告后合理期限内完成支付。"
            ),
            List.of(
                    "1. 任何一方未按合同约定履行义务的，应根据违约情形承担补救、继续履行或赔偿等责任。",
                    "2. 乙方交付迟延、产品不合格、资料缺失或整改不到位的，甲方可要求乙方限期处理。",
                    "3. 甲方无正当理由迟延付款的，应在收到乙方合理催告后及时完成付款。"
            ),
            List.of(
                    "1. 违约责任以实际违约行为、影响范围和合同履行状态为判断基础。",
                    "2. 因乙方原因导致甲方无法按期验收或使用产品的，乙方应承担相应补救责任。",
                    "3. 因甲方原因导致付款延迟的，甲方应在相关障碍消除后按合同约定办理付款。"
            ),
            List.of(
                    "1. 一方违反合同约定造成对方损失的，应依法承担相应责任。",
                    "2. 乙方未按合同约定交付、整改或配合验收的，甲方有权要求其继续履行并采取补救措施。",
                    "3. 甲方逾期付款且无正当理由的，应在乙方通知后合理期限内完成支付。"
            )
    );

    private static List<String> choose(List<List<String>> variants, Context context, String key) {
        int index = Math.floorMod(Objects.hash(context.seed(), key), variants.size());
        List<String> clauses = new ArrayList<>(variants.get(index));
        String productClause = productSpecificClause(context, key);
        if (!productClause.isBlank()) {
            clauses.add(productClause);
        }
        return clauses;
    }

    private static String productSpecificClause(Context context, String key) {
        if (!"packaging".equals(key) && !"acceptance".equals(key) && !"warranty".equals(key)) {
            return "";
        }
        String product = context.productName();
        String haystack = (context.productName() + " " + context.specification()).toLowerCase(Locale.ROOT);
        if (haystack.contains("无人机")) {
            return "6. 涉及无人机或无人机配件的，乙方应注意防潮、防震和接口适配要求，避免因存放或运输环境影响飞控、供电或连接部件的稳定性。";
        }
        if (haystack.contains("电源") || haystack.contains("电池") || haystack.contains("供电")) {
            return "6. 涉及供电、电源或电池相关产品的，乙方应保证绝缘、防潮、接口匹配和安全标识满足通常验收要求。";
        }
        if (haystack.contains("传感") || haystack.contains("模块") || haystack.contains("控制")) {
            return "6. 涉及模块、控制或传感类产品的，乙方应保证接口、规格型号和基础运行状态能够支持甲方现场安装和验收。";
        }
        if (!product.isBlank() && context.highAmount()) {
            return "6. 鉴于本合同标的金额较高，乙方应在交付前完成必要的出厂核对，并确保" + product + "与合同约定保持一致。";
        }
        return "";
    }

    private static String value(Map<String, Object> fields, String key, String fallback) {
        Object value = fields.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private record Context(String contractId, String partyA, String partyB, String productName, String specification,
                           String amountBucket, boolean highAmount, int seed) {
        static Context from(Map<String, Object> fields, String contractId) {
            String partyA = value(fields, "partyA", "");
            String partyB = value(fields, "partyB", "");
            String productName = value(fields, "productName", "");
            String specification = value(fields, "specification", "");
            BigDecimal amount = parseAmount(value(fields, "amount", "0"));
            boolean highAmount = amount.compareTo(BigDecimal.valueOf(500_000)) >= 0;
            String amountBucket = amount.compareTo(BigDecimal.valueOf(100_000)) < 0 ? "LOW"
                    : highAmount ? "HIGH" : "MID";
            int seed = Objects.hash(contractId, partyA, partyB, productName, specification, amountBucket);
            return new Context(contractId, partyA, partyB, productName, specification, amountBucket, highAmount, seed);
        }
    }

    private static BigDecimal parseAmount(String value) {
        String normalized = value == null ? "" : value.replace(",", "").replace("¥", "").trim();
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(normalized);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal amount = new BigDecimal(matcher.group(1));
            if (normalized.contains("万")) {
                amount = amount.multiply(BigDecimal.valueOf(10_000));
            }
            return amount;
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }
}
