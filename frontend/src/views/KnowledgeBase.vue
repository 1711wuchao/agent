<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h1>合同知识库</h1>
        <p>维护标准条款、风险规则和法务审核知识，并验证 Chroma 检索效果。</p>
      </div>
      <el-button type="primary" :icon="Plus" :loading="seedLoading" @click="seedDefaultKnowledge">
        初始化默认知识
      </el-button>
    </div>

    <div class="knowledge-grid">
      <section class="panel">
        <div class="panel-title">新增知识片段</div>
        <el-form label-position="top">
          <el-form-item label="标题">
            <el-input v-model="form.title" placeholder="例如：销售合同标准付款条款" />
          </el-form-item>
          <el-form-item label="合同类型">
            <el-select v-model="form.contractType">
              <el-option label="通用" value="COMMON" />
              <el-option label="销售合同" value="SALES" />
              <el-option label="采购合同" value="PURCHASE" />
              <el-option label="服务合同" value="SERVICE" />
              <el-option label="保密协议" value="NDA" />
              <el-option label="补充协议" value="SUPPLEMENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识内容">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="8"
              placeholder="输入标准条款、风险规则或审核建议"
            />
          </el-form-item>
          <el-form-item label="标签 JSON">
            <el-input v-model="metadataText" type="textarea" :rows="5" />
          </el-form-item>
          <el-button type="primary" :icon="Upload" :loading="addLoading" @click="submitChunk">
            写入知识库
          </el-button>
        </el-form>
      </section>

      <section class="panel">
        <div class="panel-title">检索测试</div>
        <div class="knowledge-search">
          <el-input v-model="query" placeholder="例如：销售合同付款条款有什么风险" clearable />
          <el-select v-model="contractType">
            <el-option label="全部" value="" />
            <el-option label="通用" value="COMMON" />
            <el-option label="销售合同" value="SALES" />
            <el-option label="采购合同" value="PURCHASE" />
            <el-option label="服务合同" value="SERVICE" />
            <el-option label="保密协议" value="NDA" />
            <el-option label="补充协议" value="SUPPLEMENT" />
          </el-select>
          <el-button :icon="Search" :loading="searchLoading" @click="runSearch">检索</el-button>
        </div>

        <el-empty v-if="!matches.length" description="暂无检索结果" />
        <div v-else class="knowledge-results">
          <article v-for="item in matches" :key="item.id" class="knowledge-item">
            <div class="knowledge-item-head">
              <strong>{{ item.metadata?.title || item.id }}</strong>
              <el-tag size="small">{{ item.metadata?.contractType || 'COMMON' }}</el-tag>
            </div>
            <p>{{ item.content }}</p>
            <div class="knowledge-meta">
              <span>风险：{{ item.metadata?.riskLevel || '-' }}</span>
              <span>距离：{{ formatDistance(item.distance) }}</span>
            </div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Search, Upload } from '@element-plus/icons-vue'
import { addKnowledgeChunk, searchKnowledge, seedKnowledge } from '../api/contracts'

const form = ref({
  title: '',
  contractType: 'COMMON',
  content: ''
})
const metadataText = ref('{\n  "clauseType": "PAYMENT",\n  "riskLevel": "MEDIUM",\n  "source": "manual"\n}')
const query = ref('销售合同付款条款')
const contractType = ref('SALES')
const matches = ref([])
const seedLoading = ref(false)
const addLoading = ref(false)
const searchLoading = ref(false)

async function seedDefaultKnowledge() {
  seedLoading.value = true
  try {
    const result = await seedKnowledge()
    ElMessage.success(`已写入 ${result.data.length || result.data.Count || 0} 条默认知识`)
    await runSearch()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    seedLoading.value = false
  }
}

async function submitChunk() {
  if (!form.value.title || !form.value.content) {
    ElMessage.warning('请先填写标题和知识内容')
    return
  }
  addLoading.value = true
  try {
    const metadata = JSON.parse(metadataText.value || '{}')
    await addKnowledgeChunk({
      title: form.value.title,
      contractType: form.value.contractType,
      content: form.value.content,
      metadata
    })
    ElMessage.success('知识片段已写入 MySQL 和 Chroma')
    query.value = form.value.title
    contractType.value = form.value.contractType
    await runSearch()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    addLoading.value = false
  }
}

async function runSearch() {
  if (!query.value) {
    ElMessage.warning('请输入检索问题')
    return
  }
  searchLoading.value = true
  try {
    const result = await searchKnowledge({
      query: query.value,
      contractType: contractType.value,
      limit: 6
    })
    matches.value = result.data.matches || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    searchLoading.value = false
  }
}

function formatDistance(distance) {
  return typeof distance === 'number' ? distance.toFixed(4) : '-'
}
</script>
