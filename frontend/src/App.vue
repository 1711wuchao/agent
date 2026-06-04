<template>
  <el-config-provider>
    <div class="app-shell">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-mark">K</div>
          <div>
            <strong>Kimi 合同 Agent</strong>
            <span>合同生成与审查</span>
          </div>
        </div>
        <el-menu :default-active="activeView" class="nav" @select="activeView = $event">
          <el-menu-item index="agent">
            <el-icon><ChatLineRound /></el-icon>
            <span>合同 Agent</span>
          </el-menu-item>
          <el-menu-item index="workspace">
            <el-icon><DocumentAdd /></el-icon>
            <span>合同工作台</span>
          </el-menu-item>
          <el-menu-item index="templates">
            <el-icon><Files /></el-icon>
            <span>模板清单</span>
          </el-menu-item>
          <el-menu-item index="knowledge">
            <el-icon><Reading /></el-icon>
            <span>知识库</span>
          </el-menu-item>
          <el-menu-item index="batch">
            <el-icon><Upload /></el-icon>
            <span>批量生成</span>
          </el-menu-item>
          <el-menu-item index="tools">
            <el-icon><Connection /></el-icon>
            <span>Agent Tool</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <main class="main">
        <AgentChat v-if="activeView === 'agent'" />
        <ContractWorkspace v-else-if="activeView === 'workspace'" />
        <TemplateList v-else-if="activeView === 'templates'" />
        <KnowledgeBase v-else-if="activeView === 'knowledge'" />
        <BatchContracts v-else-if="activeView === 'batch'" />
        <ToolConsole v-else />
      </main>
    </div>
  </el-config-provider>
</template>

<script setup>
import { ref } from 'vue'
import { ChatLineRound, Connection, DocumentAdd, Files, Reading, Upload } from '@element-plus/icons-vue'
import AgentChat from './views/AgentChat.vue'
import BatchContracts from './views/BatchContracts.vue'
import ContractWorkspace from './views/ContractWorkspace.vue'
import KnowledgeBase from './views/KnowledgeBase.vue'
import TemplateList from './views/TemplateList.vue'
import ToolConsole from './views/ToolConsole.vue'

const activeView = ref('agent')
</script>
