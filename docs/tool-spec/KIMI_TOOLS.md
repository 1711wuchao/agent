# Kimi Agent Tool 规格

后端统一入口：

```http
POST /api/ai/tools/call
```

请求格式：

```json
{
  "toolName": "search_contract_template",
  "arguments": {}
}
```

响应格式：

```json
{
  "toolName": "search_contract_template",
  "success": true,
  "data": {},
  "message": "已返回可用合同模板"
}
```

## Tool 清单

### search_contract_template

查询当前可用合同模板。

```json
{
  "toolName": "search_contract_template",
  "arguments": {}
}
```

### get_template_required_fields

查询模板字段。

```json
{
  "toolName": "get_template_required_fields",
  "arguments": {
    "templateCode": "SALES"
  }
}
```

### create_contract_draft

创建合同草稿。

```json
{
  "toolName": "create_contract_draft",
  "arguments": {
    "contractType": "销售合同",
    "templateCode": "SALES",
    "title": "与上海某某公司的销售合同",
    "fields": {
      "partyA": "甲方公司",
      "partyB": "乙方公司",
      "amount": "100000",
      "paymentMethod": "分三期付款"
    }
  }
}
```

### generate_contract_docx

生成 Word 文件。

```json
{
  "toolName": "generate_contract_docx",
  "arguments": {
    "contractId": "CTR-12345678"
  }
}
```

### export_contract_pdf

导出 PDF 文件。

```json
{
  "toolName": "export_contract_pdf",
  "arguments": {
    "contractId": "CTR-12345678"
  }
}
```

### search_clause_knowledge

检索条款知识库。

```json
{
  "toolName": "search_clause_knowledge",
  "arguments": {
    "query": "销售合同付款条款",
    "contractType": "SALES"
  }
}
```

### review_contract_risk

合同风险审查。

```json
{
  "toolName": "review_contract_risk",
  "arguments": {
    "contractId": "CTR-12345678"
  }
}
```

