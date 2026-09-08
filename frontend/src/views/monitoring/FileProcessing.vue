<template>
  <div class="file-processing-page">
    <div class="page-head compact processing-head">
      <div>
        <p class="eyebrow">运行观测 / 文档处理</p>
        <h1>文档全链路耗时</h1>
        <p>按文档比较上传、解析、分块、向量化与入库耗时，快速定位流水线瓶颈。</p>
      </div>
      <div class="head-actions"><el-button type="primary" :icon="Refresh" :loading="loading" @click="load">刷新</el-button></div>
    </div>
    <section class="filter-bar processing-filter">
      <div class="processing-date-field">
        <el-date-picker v-model="range" class="processing-date-picker" type="datetimerange" value-format="YYYY-MM-DD HH:mm:ss"
                        start-placeholder="开始时间" end-placeholder="结束时间" :shortcuts="shortcuts" />
      </div>
      <el-button type="primary" @click="applyFilter">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </section>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <div class="table-wrap processing-table-wrap">
      <el-table v-loading="loading" :data="items" stripe border max-height="calc(100vh - 320px)" empty-text="暂无文件处理记录">

        <el-table-column type="index" label="序号" width="70" align="center" fixed="left" :index="rowNumber" />
        <el-table-column prop="fileName" label="文件" width="280" fixed="left"  show-overflow-tooltip>
          <template #default="{row}"><div class="file-name">{{ row.fileName }}</div></template>
        </el-table-column>
        <el-table-column prop="fileType" label="解析方式" width="100" />
        <el-table-column label="上传与存储" width="120" align="center"><template #default="{row}">{{ formatMs(row.uploadMs) }}</template></el-table-column>
        <el-table-column label="解析服务" width="120" align="center"><template #default="{row}">{{ formatMs(row.parseMs) }}</template></el-table-column>
        <el-table-column prop="parseMethod" label="解析方式" width="120" />
        <el-table-column label="基础分块" width="120" align="center"><template #default="{row}">{{ formatMs(row.layoutChunkMs) }}</template></el-table-column>
        <el-table-column label="父子分块" width="120" align="center"><template #default="{row}">{{ formatMs(row.parentChildChunkMs) }}</template></el-table-column>
        <el-table-column label="向量化" width="120" align="center"><template #default="{row}">{{ formatMs(row.vectorizationMs) }}</template></el-table-column>
        <el-table-column label="PostgreSQL" width="120" align="center"><template #default="{row}">{{ formatMs(row.postgresMs) }}</template></el-table-column>
        <el-table-column label="Milvus" width="120" align="center"><template #default="{row}">{{ formatMs(row.milvusMs) }}</template></el-table-column>
        <el-table-column label="总耗时" width="120" align="center" sortable><template #default="{row}"><strong>{{ formatMs(row.totalMs) }}</strong></template></el-table-column>
        <el-table-column label="状态" width="120" align="center"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作"  align="center" min-width="120" fixed="right">
          <template #default="{row}"><el-button link type="primary" :icon="View" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <div class="table-pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
                       @current-change="load" @size-change="handleSizeChange" />
      </div>
    </div>

    <el-drawer v-model="detailVisible" class="processing-detail-drawer" direction="rtl" size="min(680px, 100%)" destroy-on-close>
      <template #header>
        <div class="detail-drawer-head">
          <div>
            <p>处理记录详情</p>
            <h2>{{ selectedItem?.fileName || '文件处理详情' }}</h2>
          </div>
          <el-tag v-if="selectedItem" :type="statusType(selectedItem.status)">{{ statusLabel(selectedItem.status) }}</el-tag>
        </div>
      </template>
      <template v-if="selectedItem">
        <section class="detail-section">
          <h3>文件信息</h3>
          <div class="detail-grid">
            <div class="detail-field detail-file-name"><span>文件名称</span><strong>{{ selectedItem.fileName }}</strong></div>
            <div class="detail-field"><span>文件类型</span><strong>{{ (selectedItem.fileType || '-').toUpperCase() }}</strong></div>
            <div class="detail-field"><span>处理状态</span><strong>{{ statusLabel(selectedItem.status) }}</strong></div>
            <div class="detail-field"><span>解析方式</span><strong>{{ selectedItem.parseMethod || '-' }}</strong></div>
            <div class="detail-field"><span>总耗时</span><strong class="total-duration">{{ formatMs(selectedItem.totalMs) }}</strong></div>
          </div>
        </section>
        <section class="detail-section">
          <h3>处理阶段耗时</h3>
          <div class="detail-grid">
            <div v-for="stage in processingStages" :key="stage.key" class="detail-field">
              <span>{{ stage.label }}</span><strong>{{ formatMs(selectedItem[stage.key]) }}</strong>
            </div>
          </div>
        </section>
        <section class="detail-section">
          <h3>异常信息</h3>
          <el-alert v-if="selectedItem.errorMessage" type="error" title="处理过程中出现异常" :description="selectedItem.errorMessage" :closable="false" show-icon />
          <div v-else class="no-error"><el-icon><CircleCheckFilled /></el-icon><span>本次处理未记录异常信息</span></div>
        </section>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { CircleCheckFilled, Refresh, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { monitoringApi, type FileProcessingItem } from '../../api'

const loading = ref(false)
const error = ref('')
const items = ref<FileProcessingItem[]>([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const range = ref<[string, string] | null>(null)
const detailVisible = ref(false)
const selectedItem = ref<FileProcessingItem | null>(null)
const processingStages: Array<{key: 'uploadMs' | 'parseMs' | 'layoutChunkMs' | 'parentChildChunkMs' | 'vectorizationMs' | 'postgresMs' | 'milvusMs'; label: string}> = [
  {key: 'uploadMs', label: '上传与存储'},
  {key: 'parseMs', label: '解析服务调用'},
  {key: 'layoutChunkMs', label: '基础分块'},
  {key: 'parentChildChunkMs', label: '父子分块'},
  {key: 'vectorizationMs', label: '向量化'},
  {key: 'postgresMs', label: 'PostgreSQL'},
  {key: 'milvusMs', label: 'Milvus'},
]
const shortcuts = [
  { text: '近1天', value: () => dateRange(1) },
  { text: '近7天', value: () => dateRange(7) },
  { text: '近30天', value: () => dateRange(30) },
  { text: '近90天', value: () => dateRange(90) },
]
function dateRange(days: number): [string, string] {
  const end = new Date(); const start = new Date(end.getTime() - days * 86400000)
  return [formatDateTime(start), formatDateTime(end)]
}
function formatDateTime(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
}
function params() { return { ...(range.value ? {from: range.value[0], to: range.value[1]} : {}), page: page.value, pageSize: pageSize.value } }
async function load() {
  loading.value = true; error.value = ''
  try {
    const payload = (await monitoringApi.fileProcessing(params())).data
    items.value = payload.items || []
    total.value = Number(payload.total ?? items.value.length)
  }
  catch (e: any) { error.value = e?.message || '文档全链路耗时加载失败'; ElMessage.error(error.value) }
  finally { loading.value = false }
}
function reset() { range.value = null; page.value = 1; load() }
function applyFilter() { page.value = 1; load() }
function handleSizeChange(size: number) { pageSize.value = size; page.value = 1; load() }
function rowNumber(index: number) { return (page.value - 1) * pageSize.value + index + 1 }
function openDetail(item: FileProcessingItem) { selectedItem.value = item; detailVisible.value = true }
function formatMs(value: number) { const ms = Number(value || 0); return ms < 1000 ? `${ms.toFixed(0)} ms` : `${(ms / 1000).toFixed(2)} s` }
function statusLabel(status: string) { return status === 'INDEXED' ? '已完成' : status === 'FAILED' ? '失败' : '处理中' }
function statusType(status: string) { return status === 'INDEXED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning' }
onMounted(load)
</script>

<style scoped>
.file-processing-page { display:flex; flex-direction:column; gap:16px; }
.processing-head { margin-bottom:2px; }
.processing-filter { display:flex; align-items:center; justify-content:flex-start; gap:10px; flex-wrap:wrap; }
.processing-date-field { width:360px; min-width:360px; max-width:360px; flex:0 0 360px; }
.processing-date-field :deep(.el-date-editor) { width:100% !important; min-width:0 !important; max-width:none !important; flex:1 1 auto; }
.processing-filter > .el-button { min-width:72px; }
.processing-table-wrap {
  padding:0;
  overflow:hidden;
  background:var(--surface,#fff);
  border:1px solid var(--border,#e5e7eb);
  border-radius:8px;
  box-shadow:0 2px 8px rgba(30, 55, 90, .04);
}
.processing-table-wrap :deep(.el-table) { height:auto !important; max-height:calc(100vh - 320px) !important; }
.processing-table-wrap :deep(.el-table__inner-wrapper::before) { height:0; }
.processing-table-wrap :deep(.el-table__header th) { white-space:nowrap; }
.processing-table-wrap :deep(.el-table__body tr:hover > td) { background:var(--blue-50,#f2f5ff); }
.file-name { font-weight:600; color:var(--ink,#1f2937); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
small { color:var(--text-muted,#8b95a7); font-size:12px; }
.detail-drawer-head { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding-right:20px; }
.detail-drawer-head p { margin:0 0 4px; color:var(--text-muted,#8b95a7); font-size:12px; }
.detail-drawer-head h2 { max-width:500px; margin:0; color:var(--ink,#1f2937); font-size:17px; line-height:1.45; overflow-wrap:anywhere; }
.detail-section + .detail-section { margin-top:28px; }
.detail-section h3 { margin:0 0 12px; color:var(--ink,#1f2937); font-size:14px; }
.detail-grid { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:10px; }
.detail-field { min-width:0; padding:11px 12px; border:1px solid var(--border,#e5e7eb); border-radius:6px; background:var(--surface-soft,#f8fafc); }
.detail-field > span { display:block; color:var(--text-muted,#8b95a7); font-size:12px; }
.detail-field > strong { display:block; margin-top:5px; color:var(--ink,#1f2937); font-size:13px; font-weight:600; overflow-wrap:anywhere; }
.detail-file-name { grid-column:1 / -1; }
.detail-field .total-duration { color:var(--primary,#2563eb); font-variant-numeric:tabular-nums; }
.no-error { display:flex; align-items:center; gap:8px; padding:12px 14px; border:1px solid var(--success-border,#b7ebd0); border-radius:6px; color:var(--success,#15915c); background:var(--success-bg,#f0f9f4); font-size:13px; }
.no-error .el-icon { font-size:17px; }
 :global(html.dark .detail-drawer-head h2), :global(html.dark .detail-section h3), :global(html.dark .detail-field > strong) { color:var(--ink,#e5ecf8); }
 :global(html.dark .detail-field) { background:var(--surface-soft,#1a2638); border-color:var(--border,#334155); }
 :global(html.dark .no-error) { background:rgba(21,145,92,.12); border-color:rgba(21,145,92,.45); }
@media (max-width: 760px) {
  .processing-filter { align-items:stretch; }
  .processing-date-field { width:100%; min-width:0; max-width:none; flex-basis:100%; }
  .processing-filter > .el-button { flex:1; }
  .processing-table-wrap { margin:0 -4px; border-left:0; border-right:0; border-radius:0; }
  .detail-grid { grid-template-columns:1fr; }
  .detail-drawer-head { padding-right:8px; }
}
</style>
