<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>Excel 批量生成</h1>
        <p>按销方、购买方和同一开票日期分组，自动生成购销或技术开发合同。</p>
      </div>
    </div>

    <div class="batch-grid">
      <section class="panel">
        <div class="panel-title">上传发票 Excel</div>
        <el-upload
          drag
          :auto-upload="false"
          :show-file-list="true"
          :limit="1"
          accept=".xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖入 Excel 或点击选择文件</div>
        </el-upload>

        <div class="batch-options">
          <div class="field-label">批量生成需求</div>
          <el-input
            v-model="requirements"
            type="textarea"
            :rows="6"
            maxlength="1000"
            show-word-limit
            placeholder="可选。例如：盖章页甲乙双方并排；中城合同按固定格式；交货地点写甲方指定地点。"
          />
        </div>

        <el-button
          class="batch-submit"
          type="primary"
          :icon="Upload"
          :loading="loading"
          :disabled="!selectedFile"
          @click="submit"
        >
          开始生成
        </el-button>
      </section>

      <section class="panel">
        <div class="panel-title">生成结果</div>
        <el-empty v-if="!result" description="暂无结果" />
        <template v-else>
          <div class="batch-summary">
            <div>
              <span>读取行数</span>
              <strong>{{ result.rowCount }}</strong>
            </div>
            <div>
              <span>生成合同</span>
              <strong>{{ result.contractCount }}</strong>
            </div>
          </div>

          <div class="batch-actions">
            <el-button
              type="primary"
              :icon="Download"
              :loading="batchDownloading"
              :disabled="!contractIds.length"
              @click="downloadBatch"
            >
              批量下载 Word
            </el-button>
          </div>

          <el-table :data="result.contracts || []" height="520">
            <el-table-column prop="contractId" label="合同编号" width="140" />
            <el-table-column prop="contractType" label="类型" width="130" />
            <el-table-column prop="invoiceDate" label="开票日期" width="120" />
            <el-table-column prop="lineCount" label="明细数" width="90" />
            <el-table-column prop="amount" label="价税合计" width="130" />
            <el-table-column label="下载" width="100">
              <template #default="{ row }">
                <el-button size="small" :icon="Download" @click="download(row)">Word</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload, UploadFilled } from '@element-plus/icons-vue'
import { downloadBatchDocx, uploadBatchExcel } from '../api/contracts'

const selectedFile = ref(null)
const loading = ref(false)
const batchDownloading = ref(false)
const result = ref(null)
const requirements = ref('')

const contractIds = computed(() => (result.value?.contracts || [])
  .map((contract) => contract.contractId)
  .filter(Boolean))

function handleFileChange(file) {
  selectedFile.value = file.raw
}

function handleFileRemove() {
  selectedFile.value = null
}

async function submit() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  loading.value = true
  try {
    const response = await uploadBatchExcel(selectedFile.value, requirements.value)
    result.value = response.data
    ElMessage.success(`已生成 ${response.data.contractCount} 份合同`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message)
  } finally {
    loading.value = false
  }
}

function download(row) {
  window.open(row.downloadUrl, '_blank')
}

async function downloadBatch() {
  if (!contractIds.value.length) {
    ElMessage.warning('暂无可下载的合同')
    return
  }
  batchDownloading.value = true
  try {
    const response = await downloadBatchDocx(contractIds.value)
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `批量合同_${new Date().toISOString().slice(0, 10).replaceAll('-', '')}.zip`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message)
  } finally {
    batchDownloading.value = false
  }
}
</script>
