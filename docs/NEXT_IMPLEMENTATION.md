# 下一步工程实现说明

## 后端持久化

当前 `ContractService` 使用内存 Map 保存草稿，便于 MVP 快速跑通。接 MySQL 时建议：

1. 新增 Entity：`ContractEntity`、`ContractFieldValueEntity`、`ContractTemplateEntity`。
2. 新增 Repository：`ContractRepository`、`TemplateRepository`。
3. 将 `ContractService` 的 Map 替换为 Repository。
4. 将 `schema.sql` 纳入 Flyway 或 Liquibase 管理。

## Word 生成

建议优先使用 `poi-tl`：

1. 法务维护 `.docx` 模板。
2. 模板变量使用 `{{变量名}}`。
3. 后端根据合同字段 Map 渲染模板。
4. 生成文件写入 MinIO 或本地存储。
5. `contract_file` 表记录文件路径和 checksum。

## PDF 导出

MVP 可选 LibreOffice headless：

```powershell
soffice --headless --convert-to pdf --outdir output input.docx
```

商用稳定性要求更高时，评估 OnlyOffice 或 Aspose。

## Kimi Agent 接入

Kimi Agent 只调用 `/api/ai/tools/call`，不要直接访问合同内部接口。后端需要在 Tool 层统一做：

- 权限校验
- 参数校验
- 操作日志
- Tool 调用日志
- 审批状态校验

