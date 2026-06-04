# 技术选型确认

| 模块 | 技术 | 说明 |
| --- | --- | --- |
| 前端 | Vue3 + Vite + ElementPlus | 企业后台表单和列表开发效率高 |
| 后端 | Spring Boot 3 | 适合合同业务、权限、审批、文件生成 |
| 数据库 | MySQL 8 | 存储合同、模板、版本、审批、日志 |
| 缓存 | Redis | 会话、任务状态、限流、短期结果缓存 |
| 模板 | Word `.docx` | 便于法务维护模板 |
| PDF | LibreOffice / OnlyOffice / Aspose | MVP 可先用 LibreOffice，商用稳定性可评估 Aspose |
| AI | Kimi Agent + 自建 Tool | Agent 负责理解和调用，合同系统负责落库和生成 |
| 向量库 | Chroma MVP，Milvus 规模化 | Chroma 便于快速落地，Milvus 适合大规模生产 |
| 文件存储 | MinIO / OSS / S3 | 建议先用 MinIO |

