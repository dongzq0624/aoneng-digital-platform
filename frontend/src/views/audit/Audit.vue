<template>
  <div>
    <div class="page-head compact audit-head">
      <div><p class="eyebrow">安全与合规</p>
        <h1>审计日志</h1>
        <p>记录平台关键操作与问答留痕，支持追溯与审查。</p></div>
      <div class="audit-head-actions">
        <span v-if="lastUpdated" class="updated-at">更新于 {{ lastUpdated }}</span>
        <el-button plain :icon="Refresh" :loading="loading" @click="load" aria-label="刷新审计日志">刷新</el-button>
        <el-button type="primary" :icon="Download" @click="exportLog" aria-label="导出审计日志">导出日志</el-button>
      </div>
    </div>
    <div class="audit-summary" aria-label="审计日志概览">
      <div class="audit-summary-item"><span class="summary-icon total"><el-icon><Document /></el-icon></span>
        <div><small>日志总数</small><strong>{{ rows.length }}</strong></div>
      </div>
      <div class="audit-summary-item"><span class="summary-icon success"><el-icon><CircleCheck /></el-icon></span>
        <div><small>成功操作</small><strong>{{ successCount }}</strong></div>
      </div>
      <div class="audit-summary-item"><span class="summary-icon danger"><el-icon><CircleClose /></el-icon></span>
        <div><small>失败操作</small><strong>{{ failureCount }}</strong></div>
      </div>
      <div class="audit-summary-item"><span class="summary-icon today"><el-icon><Calendar /></el-icon></span>
        <div><small>今日记录</small><strong>{{ todayCount }}</strong></div>
      </div>
    </div>
    <div class="filter-bar audit-filter-bar">
      <el-input v-model="keyword" placeholder="搜索用户名、操作或详情" clearable class="audit-search"
                aria-label="搜索审计日志">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-select v-model="module" placeholder="全部模块" clearable class="audit-module" aria-label="按模块筛选">
        <el-option label="全部模块" value=""/>
        <el-option label="知识库" value="知识库"/>
        <el-option label="智能问答" value="智能问答"/>
        <el-option label="系统管理" value="系统管理"/>
      </el-select>
      <el-button v-if="keyword || module" link type="primary" @click="resetFilters">清除筛选</el-button>
      <span class="filter-count">显示 {{ filtered.length }} / {{ rows.length }} 条</span>
    </div>
    <el-table :data="filtered" class="audit-table" v-loading="loading" row-key="id" empty-text="暂无匹配的审计记录">
      <el-table-column prop="time" label="时间" width="175"/>
      <el-table-column prop="user" label="操作人" width="130"/>
      <el-table-column prop="module" label="模块" width="120"/>
      <el-table-column prop="action" label="操作"/>
      <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip/>
      <el-table-column label="结果" width="90">
        <template #default="{row}">
          <el-tag :type="row.result?'success':'danger'" effect="light" size="small">{{
              row.result ? '成功' : '失败'
            }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script setup lang="ts">import {computed, onMounted, ref} from 'vue';
import {Calendar, CircleCheck, CircleClose, Document, Download, Refresh, Search} from '@element-plus/icons-vue';
import {ElMessage} from 'element-plus';
import {auditApi} from '../../api';

const keyword = ref('');
const module = ref('');
const rows = ref<any[]>([]);
const loading = ref(false);
const lastUpdated = ref('');
const filtered = computed(() => rows.value.filter(r => (!keyword.value || `${r.user}${r.action}${r.detail}`.includes(keyword.value)) && (!module.value || r.module === module.value)));
const successCount = computed(() => rows.value.filter(r => Number(r.result) === 1).length);
const failureCount = computed(() => rows.value.filter(r => Number(r.result) !== 1).length);
const todayCount = computed(() => {
  const today = new Date().toISOString().slice(0, 10);
  return rows.value.filter(r => String(r.time || '').slice(0, 10) === today).length;
});

async function load() {
  loading.value = true;
  try {
    const {data} = await auditApi.logs();
    rows.value = (Array.isArray(data) ? data : data.items).map((item: any) => ({
      ...item,
      time: item.time || item.createdAt || item.createdat || '',
      user: item.user || item.username || '',
      module: moduleLabel(item.module),
      action: actionLabel(item.action),
      detail: detailLabel(item.detail)
    }))
    lastUpdated.value = new Intl.DateTimeFormat('zh-CN', {hour: '2-digit', minute: '2-digit'}).format(new Date())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const moduleLabel = (value?: string) => ({
  System: '系统管理',
  'Knowledge Base': '知识库',
  Assistant: '智能问答'
} as Record<string, string>)[value || ''] || value || '';
const actionLabel = (value?: string) => ({
  LOGIN: '登录',
  DOC_UPLOAD: '上传文档',
  QA_ASK: '发起问答',
  DOC_DELETE: '删除文档'
} as Record<string, string>)[value || ''] || value || '';
const detailLabel = (value: unknown) => {
  if (!value) return '';
  if (typeof value === 'string') return value;
  const data = value as Record<string, unknown>;
  if (data.message) return String(data.message);
  if (data.file) return `文件：${data.file}`;
  if (data.question) return `问题：${data.question}`;
  if (data.documentId) return `文档编号：${data.documentId}`;
  return JSON.stringify(value);
};

function exportLog() {
  if (!filtered.value.length) {
    ElMessage.warning('当前没有可导出的记录')
    return
  }
  const header = ['时间', '操作人', '模块', '操作', '详情', '结果'];
  const lines = filtered.value.map(item => [item.time, item.user, item.module, item.action, item.detail, item.result ? '成功' : '失败']
    .map(value => `"${String(value ?? '').replace(/"/g, '""')}"`).join(','));
  const blob = new Blob([`\ufeff${[header.join(','), ...lines].join('\n')}`], {type: 'text/csv;charset=utf-8'});
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `奥能电源审计日志-${new Date().toISOString().slice(0, 10)}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  ElMessage.success(`已导出 ${filtered.value.length} 条日志`)
}

function resetFilters() {
  keyword.value = '';
  module.value = '';
}</script>
