# 合同类型与模板清单

| 合同类型编码 | 合同类型 | 模板文件 | 核心字段 | 优先级 |
| --- | --- | --- | --- | --- |
| SALES | 销售合同 | `sales-contract.docx` | 甲方、乙方、产品、金额、付款方式、交付日期、验收标准 | P0 |
| PURCHASE | 采购合同 | `purchase-contract.docx` | 甲方、乙方、采购内容、金额、交付方式、质保期 | P0 |
| SERVICE | 服务合同 | `service-contract.docx` | 服务方、委托方、服务内容、服务周期、费用、成果交付 | P0 |
| NDA | 保密协议 | `nda.docx` | 披露方、接收方、保密范围、保密期限、违约责任 | P0 |
| SUPPLEMENT | 补充协议 | `supplement.docx` | 原合同编号、变更事项、生效日期、双方主体 | P0 |
| LABOR | 劳务合同 | `labor-contract.docx` | 人员、服务周期、报酬、工作内容、责任边界 | P1 |
| LEASE | 租赁合同 | `lease-contract.docx` | 租赁物、租期、租金、押金、交付状态 | P1 |
| FRAMEWORK | 框架协议 | `framework-agreement.docx` | 合作范围、订单规则、结算方式、有效期 | P1 |

## 模板变量规范

- 变量格式统一使用 `{{变量名}}`。
- 变量必须在系统中登记，包括字段编码、字段名称、类型、是否必填、默认值。
- 模板发布后不可直接修改，修改必须生成新版本。

