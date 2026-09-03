<template>
  <div class="file-processing-page">
    <div class="page-head compact">
      <div>
        <p class="eyebrow">运行观测 / 文档处理</p>
        <h1>文件处理耗时</h1>
        <p>按文件比较上传、解析、分块、向量化与入库耗时，快速定位流水线瓶颈。</p>
      </div>
      <div class="head-actions"><el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    </div>
    <section class="filter-bar">
      <el-date-picker v-model="range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss[Z]"
                      start-placeholder="开始时间" end-placeholder="结束时间" :shortcuts="shortcuts" />
      <el-button @click="reset">重置</el-button>
      <el-button type="primary" @click="load">应用筛选</el-button>
    </section>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <div class="table-wrap">
      <el-table v-loading="loading" :data="items" stripe border height="calc(100vh - 260px)" empty-text="暂无文件处理记录">
        <el-table-column prop="fileName" label="文件" min-width="220" fixed="left" show-overflow-tooltip>
          <template #default="{row}"><div class="file-name">{{ row.fileName }}</div><small>#{{ row.docId }} · {{ (row.fileType || '').toUpperCase() }}</small></template>
        </el-table-column>
        <el-table-column label="上传与存储" width="110" align="right"><template #default="{row}">{{ formatMs(row.uploadMs) }}</template></el-table-column>
        <el-table-column label="文档解析" width="110" align="right"><template #default="{row}">{{ formatMs(row.parseMs) }}</template></el-table-column>
        <el-table-column prop="parseMethod" label="解析方式" width="110" />
        <el-table-column label="版面感知分块" width="125" align="right"><template #default="{row}">{{ formatMs(row.layoutChunkMs) }}</template></el-table-column>
        <el-table-column label="父子分块" width="110" align="right"><template #default="{row}">{{ formatMs(row.parentChildChunkMs) }}</template></el-table-column>
        <el-table-column label="向量化" width="110" align="right"><template #default="{row}">{{ formatMs(row.vectorizationMs) }}</template></el-table-column>
        <el-table-column label="PostgreSQL" width="115" align="right"><template #default="{row}">{{ formatMs(row.postgresMs) }}</template></el-table-column>
        <el-table-column label="Milvus" width="100" align="right"><template #default="{row}">{{ formatMs(row.milvusMs) }}</template></el-table-column>
        <el-table-column label="总耗时" width="115" align="right" sortable><template #default="{row}"><strong>{{ formatMs(row.totalMs) }}</strong></template></el-table-column>
        <el-table-column label="状态" width="100" align="center"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="异常信息" min-width="180" show-overflow-tooltip><template #default="{row}"><span :class="{danger: row.errorMessage}">{{ row.errorMessage || '-' }}</span></template></el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { monitoringApi, type FileProcessingItem } from '../../api'

const loading = ref(false)
const error = ref('')
const items = ref<FileProcessingItem[]>([])
const range = ref<[string, string] | null>(null)
const shortcuts = [
  { text: '近1天', value: () => dateRange(1) },
  { text: '近7天', value: () => dateRange(7) },
  { text: '近30天', value: () => dateRange(30) },
  { text: '近90天', value: () => dateRange(90) },
]
function dateRange(days: number): [string, string] {
  const end = new Date(); const start = new Date(end.getTime() - days * 86400000)
  return [start.toISOString().replace(/\.\d{3}Z$/, 'Z'), end.toISOString().replace(/\.\d{3}Z$/, 'Z')]
}
function params() { return range.value ? { from: range.value[0], to: range.value[1] } : undefined }
async function load() {
  loading.value = true; error.value = ''
  try { items.value = (await monitoringApi.fileProcessing(params())).data.items || [] }
  catch (e: any) { error.value = e?.message || '文件处理耗时加载失败'; ElMessage.error(error.value) }
  finally { loading.value = false }
}
function reset() { range.value = null; load() }
function formatMs(value: number) { const ms = Number(value || 0); return ms < 1000 ? `${ms.toFixed(0)} ms` : `${(ms / 1000).toFixed(2)} s` }
function statusLabel(status: string) { return status === 'INDEXED' ? '已完成' : status === 'FAILED' ? '失败' : '处理中' }
function statusType(status: string) { return status === 'INDEXED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning' }
onMounted(load)
</script>

<style scoped>
.file-processing-page { display:flex; flex-direction:column; gap:18px; }
.filter-bar { display:flex; gap:10px; align-items:center; flex-wrap:wrap; }
.table-wrap { background:var(--surface,#fff); border:1px solid var(--border,#e5e7eb); border-radius:8px; padding:12px; }
.file-name { font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
small { color:var(--text-muted,#8b95a7); }
.danger { color:var(--danger,#d93025); }
@media (max-width: 760px) { .table-wrap { padding:4px; } }
</style>
