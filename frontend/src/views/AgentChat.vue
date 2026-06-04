<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>合同 Agent</h1>
        <p>Kimi 负责任务规划，合同系统负责模板、草稿、审查和导出。</p>
      </div>
      <el-tag v-if="sessionId" type="info">{{ sessionId }}</el-tag>
    </div>

    <div class="agent-grid">
      <section class="panel agent-chat-panel">
        <div class="chat-log">
          <div
            v-for="item in messages"
            :key="item.id"
            class="chat-row"
            :class="item.role"
          >
            <div class="chat-bubble">
              <strong>{{ item.role === 'user' ? '你' : 'Agent' }}</strong>
              <p>{{ item.content }}</p>
            </div>
          </div>
        </div>

        <div class="chat-composer">
          <el-input
            v-model="messageText"
            type="textarea"
            :rows="4"
            resize="none"
            placeholder="例如：帮我生成一份销售合同，甲方：上海星河科技有限公司，乙方：北京云启贸易有限公司，金额 50 万，2026-07-01 交付，分两期付款。"
            @keydown.ctrl.enter="sendMessage"
          />
          <el-button type="primary" :icon="Promotion" :loading="loading" @click="sendMessage">发送</el-button>
        </div>
      </section>

      <section class="agent-side">
        <div class="panel">
          <div class="panel-title">任务状态</div>
          <div class="status-line">
            <span>阶段</span>
            <el-tag :type="stageTagType">{{ lastResponse?.stage || 'WAITING' }}</el-tag>
          </div>
          <div class="status-line">
            <span>模板</span>
            <strong>{{ lastResponse?.selectedTemplate?.name || '-' }}</strong>
          </div>
          <div class="status-line">
            <span>草稿</span>
            <strong>{{ lastResponse?.currentDraft?.id || '-' }}</strong>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">缺失字段</div>
          <el-empty v-if="missingFields.length === 0" description="无缺失字段" :image-size="72" />
          <el-table v-else :data="missingFields" size="small">
            <el-table-column prop="label" label="字段" />
            <el-table-column prop="type" label="类型" width="90" />
          </el-table>
        </div>

        <div class="panel">
          <div class="panel-title">已收集字段</div>
          <el-empty v-if="fieldEntries.length === 0" description="暂无字段" :image-size="72" />
          <div v-else class="field-list">
            <div v-for="item in fieldEntries" :key="item.key" class="field-item">
              <span>{{ item.key }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">风险审查</div>
          <el-empty v-if="!riskReport.riskLevel" description="暂无审查结果" :image-size="72" />
          <template v-else>
            <el-tag :type="riskTagType">{{ riskReport.riskLevel }}</el-tag>
            <ul class="risk-list">
              <li v-for="finding in riskReport.findings || []" :key="finding">{{ finding }}</li>
            </ul>
          </template>
        </div>

        <div class="panel">
          <div class="panel-title">生成文件</div>
          <el-empty v-if="!generatedFiles.docx && !generatedFiles.pdf" description="暂无文件" :image-size="72" />
          <div v-else class="file-list">
            <div v-if="generatedFiles.docx" class="file-item">
              <span>Word</span>
              <strong>{{ generatedFiles.docx.fileName }}</strong>
            </div>
            <div v-if="generatedFiles.pdf" class="file-item">
              <span>PDF</span>
              <strong>{{ generatedFiles.pdf.fileName }}</strong>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-title">Tool 调用</div>
          <el-empty v-if="toolCalls.length === 0" description="暂无调用" :image-size="72" />
          <el-timeline v-else>
            <el-timeline-item
              v-for="tool in toolCalls"
              :key="`${tool.toolName}-${tool.message}`"
              :type="tool.success ? 'success' : 'danger'"
            >
              <strong>{{ tool.toolName }}</strong>
              <p>{{ tool.message }}</p>
            </el-timeline-item>
          </el-timeline>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { callContractAgent } from '../api/contracts'

let messageId = 0
const sessionId = ref('')
const messageText = ref('')
const loading = ref(false)
const lastResponse = ref(null)
const messages = ref([
  {
    id: ++messageId,
    role: 'assistant',
    content: '请告诉我合同类型和已知字段，我会先匹配模板，再检查缺失字段。'
  }
])

const missingFields = computed(() => lastResponse.value?.missingFields || [])
const riskReport = computed(() => lastResponse.value?.riskReport || {})
const generatedFiles = computed(() => lastResponse.value?.generatedFiles || {})
const toolCalls = computed(() => lastResponse.value?.toolCalls || [])
const fieldEntries = computed(() => Object.entries(lastResponse.value?.collectedFields || {}).map(([key, value]) => ({ key, value })))

const stageTagType = computed(() => {
  const stage = lastResponse.value?.stage
  if (stage === 'DONE') return 'success'
  if (stage === 'RISK_CONFIRMATION') return 'danger'
  if (stage === 'NEED_FIELDS' || stage === 'NEED_CONTRACT_TYPE') return 'warning'
  return 'info'
})

const riskTagType = computed(() => {
  if (riskReport.value.riskLevel === 'HIGH') return 'danger'
  if (riskReport.value.riskLevel === 'MEDIUM') return 'warning'
  return 'success'
})

async function sendMessage() {
  const text = messageText.value.trim()
  if (!text) {
    ElMessage.warning('请输入合同需求')
    return
  }

  messages.value.push({
    id: ++messageId,
    role: 'user',
    content: text
  })
  messageText.value = ''
  loading.value = true

  try {
    const result = await callContractAgent({
      sessionId: sessionId.value || null,
      message: text,
      fields: {}
    })
    lastResponse.value = result.data
    sessionId.value = result.data.sessionId
    messages.value.push({
      id: ++messageId,
      role: 'assistant',
      content: result.data.assistantMessage
    })
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}
</script>
