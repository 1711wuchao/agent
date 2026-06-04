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
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Upload, UploadFilled } from '@element-plus/icons-vue'
import { uploadBatchExcel } from '../api/contracts'

const selectedFile = ref(null)
const loading = ref(false)
const result = ref(null)

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
    const response = await uploadBatchExcel(selectedFile.value)
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
</script>
