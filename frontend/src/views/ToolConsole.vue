<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>Agent Tool 调试台</h1>
        <p>模拟 Kimi Agent 调用后端 Tool。</p>
      </div>
      <el-button type="primary" :icon="Connection" @click="callTool">调用 Tool</el-button>
    </div>

    <div class="tool-grid">
      <section class="panel">
        <div class="panel-title">请求</div>
        <el-form label-position="top">
          <el-form-item label="Tool 名称">
            <el-select v-model="toolName">
              <el-option label="search_contract_template" value="search_contract_template" />
              <el-option label="get_template_required_fields" value="get_template_required_fields" />
              <el-option label="detect_missing_required_fields" value="detect_missing_required_fields" />
              <el-option label="create_contract_draft" value="create_contract_draft" />
              <el-option label="update_contract_fields" value="update_contract_fields" />
              <el-option label="search_clause_knowledge" value="search_clause_knowledge" />
              <el-option label="review_contract_risk" value="review_contract_risk" />
              <el-option label="generate_contract_docx" value="generate_contract_docx" />
              <el-option label="export_contract_pdf" value="export_contract_pdf" />
            </el-select>
          </el-form-item>
          <el-form-item label="X-Agent-Token">
            <el-input v-model="agentToken" placeholder="Kimi Tool 调用密钥" show-password />
          </el-form-item>
          <el-form-item label="参数 JSON">
            <el-input v-model="argumentsText" type="textarea" :rows="16" />
          </el-form-item>
        </el-form>
      </section>

      <section class="panel">
        <div class="panel-title">响应</div>
        <pre class="json-output">{{ responseText }}</pre>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection } from '@element-plus/icons-vue'
import { callKimiTool } from '../api/contracts'

const toolName = ref('search_contract_template')
const agentToken = ref('')
const argumentsText = ref('{\n  "templateCode": "SALES",\n  "query": "付款条款"\n}')
const response = ref(null)

const responseText = computed(() => response.value ? JSON.stringify(response.value, null, 2) : '等待调用...')

async function callTool() {
  try {
    const result = await callKimiTool({
      toolName: toolName.value,
      arguments: JSON.parse(argumentsText.value)
    }, agentToken.value)
    response.value = result.data
  } catch (error) {
    ElMessage.error(error.message)
  }
}
</script>
