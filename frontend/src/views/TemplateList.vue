<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>模板清单</h1>
        <p>维护合同类型、模板版本和字段变量。</p>
      </div>
      <el-button :icon="Refresh" @click="loadTemplates">刷新</el-button>
    </div>

    <section class="panel">
      <el-table :data="templates">
        <el-table-column prop="code" label="编码" width="130" />
        <el-table-column prop="name" label="合同类型" width="160" />
        <el-table-column prop="fileName" label="模板文件" />
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="字段数" width="100">
          <template #default="{ row }">{{ row.fields.length }}</template>
        </el-table-column>
      </el-table>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listTemplates } from '../api/contracts'

const templates = ref([])

async function loadTemplates() {
  const result = await listTemplates()
  templates.value = result.data
}

onMounted(loadTemplates)
</script>

