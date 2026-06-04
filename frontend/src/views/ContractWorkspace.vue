<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>合同工作台</h1>
        <p>选择合同模板，补齐字段，生成真实 Word 合同文件。</p>
      </div>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
    </div>

    <div class="workspace-grid">
      <section class="panel">
        <div class="panel-title">新建合同</div>
        <el-form label-position="top" :model="form">
          <el-form-item label="合同模板">
            <el-select v-model="form.templateCode" placeholder="请选择模板" @change="handleTemplateChange">
              <el-option
                v-for="template in templates"
                :key="template.code"
                :label="`${template.name} ${template.version}`"
                :value="template.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="合同标题">
            <el-input v-model="form.title" placeholder="例如：某项目产品购销合同" />
          </el-form-item>

          <template v-for="field in selectedTemplate?.fields || []" :key="field.code">
            <el-form-item :label="field.required ? `${field.label} *` : field.label">
              <el-input
                v-if="field.type === 'textarea'"
                v-model="form.fields[field.code]"
                type="textarea"
                :rows="3"
                :placeholder="field.placeholder"
              />
              <el-date-picker
                v-else-if="field.type === 'date'"
                v-model="form.fields[field.code]"
                value-format="YYYY-MM-DD"
                type="date"
                :placeholder="field.placeholder"
              />
              <el-input v-else v-model="form.fields[field.code]" :placeholder="field.placeholder" />
            </el-form-item>
          </template>

          <el-button type="primary" :icon="DocumentAdd" @click="submitDraft">创建草稿</el-button>
        </el-form>
      </section>

      <section class="panel">
        <div class="panel-title">合同列表</div>
        <el-table :data="contracts" height="620">
          <el-table-column prop="id" label="编号" width="140" />
          <el-table-column prop="title" label="标题" />
          <el-table-column prop="contractType" label="类型" width="130" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column label="操作" width="260">
            <template #default="{ row }">
              <el-button size="small" :icon="Document" @click="handleGenerateDocx(row)">Word</el-button>
              <el-button size="small" :icon="Download" @click="handleExportPdf(row)">PDF</el-button>
              <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, DocumentAdd, Download, Refresh } from '@element-plus/icons-vue'
import {
  contractFileUrl,
  createContractDraft,
  deleteContract,
  exportPdf,
  generateDocx,
  listContracts,
  listTemplates
} from '../api/contracts'

const templates = ref([])
const contracts = ref([])
const form = reactive({
  templateCode: '',
  title: '',
  fields: {}
})

const selectedTemplate = computed(() => templates.value.find(item => item.code === form.templateCode))

async function loadData() {
  const [templateResult, contractResult] = await Promise.all([listTemplates(), listContracts()])
  templates.value = templateResult.data
  contracts.value = contractResult.data
  if (!form.templateCode && templates.value.length > 0) {
    form.templateCode = templates.value[0].code
    handleTemplateChange(form.templateCode)
  }
}

function handleTemplateChange(code) {
  const template = templates.value.find(item => item.code === code)
  form.fields = {}
  template?.fields.forEach(field => {
    form.fields[field.code] = ''
  })
  if (template) {
    form.title = `${template.name}草稿`
  }
}

async function submitDraft() {
  const template = selectedTemplate.value
  if (!template) {
    ElMessage.warning('请先选择模板')
    return
  }
  await createContractDraft({
    contractType: template.name,
    templateCode: template.code,
    title: form.title,
    fields: form.fields
  })
  ElMessage.success('合同草稿已创建')
  await loadData()
}

async function handleGenerateDocx(row) {
  const result = await generateDocx(row.id)
  ElMessage.success(`Word 已生成：${result.data.fileName}`)
  window.open(contractFileUrl(row.id, 'DOCX'), '_blank')
}

async function handleExportPdf(row) {
  const result = await exportPdf(row.id)
  ElMessage.warning(result.data.nextStep || `PDF 状态：${result.data.status}`)
}

async function handleDelete(row) {
  await ElMessageBox.confirm(
    `确定删除合同 ${row.id} 吗？已生成的 Word/PDF 文件也会一起删除。`,
    '删除合同',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  )
  await deleteContract(row.id)
  ElMessage.success('合同已删除')
  await loadData()
}

onMounted(loadData)
</script>
