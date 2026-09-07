<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">知识库 / 文档空间</p>
        <h1>{{ detail?.name || '知识库详情' }}</h1>
        <p>{{ detail?.description || '管理知识库文档并维护检索索引。' }}</p></div>
      <div >
        <el-button plain :icon="ArrowLeft" @click="back">返回列表</el-button>
        <input ref="fileInput" type="file" class="hidden-file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.rtf,.wp,.jpg,.jpeg,.png,.md,.txt"
               @change="handleFile"/>
        <el-button style="margin-left: 10px" v-if="detail?.canManage" type="primary" :icon="Upload" @click="upload">上传文档</el-button>
      </div>
    </div>
    <div class="detail-summary">
      <div><small>可见范围</small><b>{{ visibilityLabel(detail?.visibility) }}</b></div>
      <div><small>文档数量</small><b>{{ total }}</b></div>
      <div><small>索引状态</small><b :class="['index-status', indexStatusClass]">{{ indexStatus }}</b></div>
      <div><small>父块 token / 重叠 token</small><b>{{ detail?.chunkSize ?? 1200 }} / {{ detail?.chunkOverlap ?? 64 }}</b></div>
    </div>
    <el-alert v-if="processing" class="processing-status" type="info" :closable="false" show-icon>
      <template #title>
        {{ processing.message || '正在处理文档' }}
        <span v-if="processing.totalChunks">（{{ processing.completedChunks || 0 }}/{{ processing.totalChunks }}）</span>
      </template>
      <el-progress :percentage="processing.progress || 0" :stroke-width="7" :show-text="true" />
    </el-alert>
    <div class="panel">
      <div class="panel-title">
        <div><h3>文档列表</h3><small>支持 PDF、Word、Excel、PPT、Markdown、TXT</small></div>
        <el-input v-model="keyword" placeholder="搜索文档" style="width:220px"/>
        <el-button type="primary" plain @click="applyFilters">查询</el-button>
      </div>
      <el-table :data="filteredDocs" class="audit-table detail-table" v-loading="loading" empty-text="暂无文档">
        <el-table-column type="index" label="序号" width="70" align="center" :index="rowNumber" />
        <el-table-column prop="fileName" label="文件名" min-width="240"/>
        <el-table-column prop="fileType" label="类型" width="100"/>
        <el-table-column prop="version" label="版本" width="100"/>
        <el-table-column prop="chunkCount" label="分块" width="100"/>
        <el-table-column label="解析状态" width="120">
          <template #default="{row}">
            <el-tooltip v-if="row.parseStatus === 'FAILED' && row.errorMsg" :content="row.errorMsg" placement="top" :show-after="200">
              <el-tag type="danger" size="small">解析失败</el-tag>
            </el-tooltip>
            <el-tag v-else :type="documentStatus(row).type" size="small">
              {{ documentStatus(row).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="200">
          <template #default="{row}">{{ formatBeijingTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="detail?.canManage ? 280 : 150" align="center">
          <template #default="{row}">
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary"  @click="showParentChunks(row)">分块</el-button>
            <template v-if="detail?.canManage">
              <el-button link type="primary" @click="reindex(row.id)">重建索引</el-button>
              <el-button link type="danger" @click="remove(row.id)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-pagination">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
                       :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
                       @current-change="load" @size-change="handleSizeChange" />
      </div>
    </div>
    <el-dialog v-model="previewVisible" :title="previewDocument?.fileName || '文件预览'" width="min(1000px, 92vw)" top="5vh" destroy-on-close class="document-preview-dialog">
      <div v-loading="previewLoading" class="document-preview-body">
        <el-alert v-if="previewError" type="warning" :title="previewError" :closable="false" show-icon />
        <img v-else-if="previewKind === 'image' && previewUrl" :src="previewUrl" :alt="previewDocument?.fileName || '预览图片'" class="preview-image" />
        <iframe v-else-if="previewKind === 'pdf' && previewUrl" :src="previewUrl" :title="previewDocument?.fileName || 'PDF 预览'" class="preview-frame" />
        <pre v-else-if="previewKind === 'text'" class="preview-text">{{ previewText }}</pre>
        <div v-else-if="!previewLoading && !previewError" class="preview-empty">暂无可预览内容</div>
      </div>
      <template #footer>
        <span class="preview-support-tip">支持在线预览：PDF、JPG、JPEG、PNG、TXT、MD；其他格式请下载后查看。</span>
        <el-button @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="chunkDrawerVisible" :title="chunkDocument?.fileName ? `分块 · ${chunkDocument.fileName}` : '文档分块'"
               size="min(720px, 92vw)" destroy-on-close class="parent-chunk-drawer">
      <div class="chunk-drawer-content" v-loading="chunkLoading">
        <el-alert v-if="chunkError" type="error" :title="chunkError" :closable="false" show-icon>
          <template #default>
            <el-button link type="primary" @click="loadParentChunks">重试</el-button>
          </template>
        </el-alert>
        <el-empty v-else-if="!chunkLoading && !parentChunks.length" description="该文件暂无父块内容" />
        <div v-else class="parent-chunk-list">
          <article v-for="chunk in parentChunks" :key="chunk.sequence" class="parent-chunk-item">
            <div class="parent-chunk-head">
              <span>父块 {{ chunk.sequence }}</span>
              <small v-if="chunk.tokenCount != null">{{ chunk.tokenCount }} token</small>
            </div>
            <pre class="parent-chunk-content">{{ chunk.content }}</pre>
          </article>
        </div>
      </div>
    </el-drawer>
  </div>
</template>
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {ArrowLeft, Grid, Upload, View} from '@element-plus/icons-vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {ApiError, documentApi, knowledgeBaseApi} from '../../api'
import {streamDocumentProcessing, type DocumentProcessingEvent} from '../../utils/sse'
import {formatBeijingTime} from '../../utils/datetime'

const router = useRouter();
const route = useRoute();
const kbId = Number(route.params.id)
const fileInput = ref<HTMLInputElement>();
const loading = ref(false);
const docs = ref<any[]>([]);
const detail = ref<any>();
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const processing = ref<DocumentProcessingEvent>()
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewUrl = ref('')
const previewText = ref('')
const previewDocument = ref<any>()
const previewKind = ref<'pdf' | 'image' | 'text' | 'unsupported'>('unsupported')
const chunkDrawerVisible = ref(false)
const chunkLoading = ref(false)
const chunkError = ref('')
const chunkDocument = ref<any>()
const parentChunks = ref<Array<{sequence: number; content: string; tokenCount?: number | null}>>([])
let chunkRequestId = 0
let processingController: AbortController | undefined
let processingRefreshTimer: ReturnType<typeof setInterval> | undefined
const filteredDocs = computed(() => docs.value)
const indexStatus = computed(() => {
  if (!docs.value.length) return '暂无文档'
  if (docs.value.every(d => d.chunkStatus === 'INDEXED')) return '全部已完成'
  if (docs.value.some(d => ['PARSING', 'INDEXING'].includes(d.parseStatus) || ['PARSING', 'INDEXING'].includes(d.chunkStatus))) return '处理中'
  if (docs.value.some(d => d.parseStatus === 'FAILED' || d.chunkStatus === 'FAILED')) return '存在失败'
  return '待处理'
})
const indexStatusClass = computed(() => ({
  success: indexStatus.value === '全部已完成',
  processing: indexStatus.value === '处理中',
  warning: indexStatus.value === '待处理',
  danger: indexStatus.value === '存在失败',
  empty: indexStatus.value === '暂无文档',
}))
const visibilityLabel = (v?: string) => ({
  PRIVATE: '仅自己',
  DEPT: '允许部门',
  ORG: '全公司',
  PUBLIC: '公开'
} as Record<string, string>)[v || ''] || '-'
const documentStatus = (row: any) => {
  if (row.chunkStatus === 'INDEXED') return {label: '已索引', type: 'success' as const}
  if (row.parseStatus === 'FAILED' || row.chunkStatus === 'FAILED') return {label: '处理失败', type: 'danger' as const}
  if (row.chunkStatus === 'INDEXING' || row.parseStatus === 'PARSING') return {label: '处理中', type: 'warning' as const}
  if (row.chunkStatus === 'PARTIAL') return {label: '部分完成', type: 'warning' as const}
  return {label: '待处理', type: 'info' as const}
}

async function load() {
  loading.value = true
  try {
    const [base, documents] = await Promise.all([knowledgeBaseApi.get(kbId), documentApi.list(kbId, {keyword: keyword.value || undefined, page: page.value, pageSize: pageSize.value})]);
    const item: any = base.data
    detail.value = {...item, ownerId: item.ownerId ?? item.ownerid, deptId: item.deptId ?? item.deptid, chunkSize: item.chunkSize ?? item.chunksize, chunkOverlap: item.chunkOverlap ?? item.chunkoverlap, canManage: item.canManage ?? item.canmanage}
    const payload: any = Array.isArray(documents.data) ? {items: documents.data, total: documents.data.length} : documents.data
    total.value = Number(payload.total ?? 0)
    docs.value = (payload.items || []).map((doc: any) => ({...doc, kbId: doc.kbId ?? doc.kbid, fileName: doc.fileName || doc.filename, fileType: doc.fileType || doc.filetype, chunkStatus: doc.chunkStatus || doc.chunkstatus, parseStatus: doc.parseStatus || doc.parsestatus, chunkCount: doc.chunkCount ?? doc.chunkcount, errorMsg: doc.errorMsg || doc.errormsg, updatedAt: doc.updatedAt || doc.updatedat}))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库详情加载失败')
    if (error instanceof ApiError && error.status === 403) router.replace('/kb')
  } finally {
    loading.value = false
  }
}

function applyFilters() { page.value = 1; load() }
function handleSizeChange(size: number) { pageSize.value = size; page.value = 1; load() }
function rowNumber(index: number) { return (page.value - 1) * pageSize.value + index + 1 }

onMounted(load)
watch(previewVisible, visible => {
  if (!visible) clearPreviewUrl()
})
watch(chunkDrawerVisible, visible => {
  if (!visible) {
    chunkDocument.value = undefined
    parentChunks.value = []
    chunkError.value = ''
  }
})
onBeforeUnmount(() => {
  processingController?.abort()
  stopProcessingRefresh()
  clearPreviewUrl()
})

function back() {
  router.push('/kb')
}

function upload() {
  fileInput.value?.click()
}

async function handleFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const {data} = await documentApi.upload(kbId, file);
    ElMessage.success('上传成功，正在解析索引');
    await load()
    await watchProcessing(data.id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '上传失败')
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function reindex(id: number) {
  try {
    await documentApi.reindex(id);
    ElMessage.success('已提交重建索引任务');
    await watchProcessing(id)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重建索引失败')
  }
}

async function remove(id: number) {
  try {
    await documentApi.remove(id);
    ElMessage.success('文档已删除');
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  }
}

function detectPreviewKind(fileType?: string) {
  const normalized = String(fileType || '').replace(/^\./, '').toLowerCase()
  if (normalized === 'pdf') return 'pdf' as const
  if (['jpg', 'jpeg', 'png'].includes(normalized)) return 'image' as const
  if (['txt', 'md'].includes(normalized)) return 'text' as const
  return 'unsupported' as const
}

async function preview(document: any) {
  clearPreviewUrl()
  previewDocument.value = document
  previewKind.value = detectPreviewKind(document.fileType)
  previewVisible.value = true
  previewError.value = ''
  previewText.value = ''
  if (previewKind.value === 'unsupported') {
    previewError.value = '当前文件格式不支持在线预览，请下载文件后查看。'
    return
  }
  previewLoading.value = true
  try {
    const {data} = await documentApi.preview(Number(document.id))
    previewUrl.value = URL.createObjectURL(data)
    if (previewKind.value === 'text') previewText.value = await data.text()
  } catch (cause) {
    previewError.value = cause instanceof Error ? cause.message : '文件预览失败，请稍后重试或下载查看。'
  } finally {
    previewLoading.value = false
  }
}

async function showParentChunks(document: any) {
  chunkDocument.value = document
  parentChunks.value = []
  chunkError.value = ''
  chunkDrawerVisible.value = true
  await loadParentChunks()
}

async function loadParentChunks() {
  const documentId = Number(chunkDocument.value?.id)
  if (!documentId) return
  const requestId = ++chunkRequestId
  chunkLoading.value = true
  chunkError.value = ''
  try {
    const {data} = await documentApi.parentChunks(documentId)
    if (requestId !== chunkRequestId) return
    const payload: any = data
    const items = Array.isArray(payload) ? payload : payload?.items
    parentChunks.value = (items || []).map((item: any, index: number) => ({
      sequence: Number(item.sequence ?? item.seq ?? index + 1),
      content: String(item.content ?? ''),
      tokenCount: item.tokenCount ?? item.tokencount ?? null,
    }))
    if (payload?.fileName && chunkDocument.value) chunkDocument.value = {...chunkDocument.value, fileName: payload.fileName}
  } catch (cause) {
    if (requestId !== chunkRequestId) return
    chunkError.value = cause instanceof Error ? cause.message : '父块加载失败，请稍后重试'
  } finally {
    if (requestId === chunkRequestId) chunkLoading.value = false
  }
}

function clearPreviewUrl() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

async function watchProcessing(docId: number) {
  processingController?.abort()
  stopProcessingRefresh()
  processingController = new AbortController()
  processing.value = {docId, progress: 0, message: '正在连接文档处理服务'}
  processingRefreshTimer = setInterval(() => { void refreshProcessingState(docId) }, 2000)
  await streamDocumentProcessing(docId, {
    onProgress: event => {
      processing.value = {...processing.value, ...event}
    },
    onSegment: event => {
      processing.value = {...processing.value, ...event, message: `已完成第 ${(event.sequence || 0) + 1} 个分段索引`}
    },
    onDone: async event => {
      processing.value = {...processing.value, ...event, progress: 100}
      await load()
      stopProcessingRefresh()
      ElMessage.success('文档解析与索引完成')
      processing.value = undefined
    },
    onError: async error => {
      ElMessage.error(error.message || '文档处理失败')
      await load()
      stopProcessingRefresh()
      processing.value = undefined
    },
  }, processingController.signal)
}

function stopProcessingRefresh() {
  if (processingRefreshTimer) {
    clearInterval(processingRefreshTimer)
    processingRefreshTimer = undefined
  }
}

async function refreshProcessingState(docId: number) {
  await load()
  const current = docs.value.find(doc => Number(doc.id) === docId)
  if (!current) return
  if (current.chunkStatus === 'INDEXED') {
    processingController?.abort()
    stopProcessingRefresh()
    processing.value = undefined
  } else if (current.parseStatus === 'FAILED' || current.chunkStatus === 'FAILED') {
    processingController?.abort()
    stopProcessingRefresh()
    processing.value = undefined
  }
}
</script>
<style scoped>
.detail-summary {
  display: grid;
  grid-template-columns:repeat(4, 1fr);
  gap: 13px;
  margin-bottom: 18px
}

.detail-summary > div {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 16px 18px
}

.detail-summary small, .detail-summary b {
  display: block
}

.detail-summary small {
  font-size: 10px;
  color: var(--muted);
  margin-bottom: 7px
}

.detail-summary b {
  font: 600 18px Manrope;
  color: var(--ink)
}

.detail-summary b.index-status {
  font-family: 'Noto Sans SC';
  font-size: 13px;
}

.detail-summary b.index-status.success { color: var(--success) }
.detail-summary b.index-status.processing { color: var(--primary) }
.detail-summary b.index-status.warning { color: var(--warning, #b7791f) }
.detail-summary b.index-status.danger { color: var(--danger, #c53030) }
.detail-summary b.index-status.empty { color: var(--muted) }

.detail-table {
  margin-top: 18px
}

.processing-status {
  margin-bottom: 18px;
}

.processing-status :deep(.el-alert__content) {
  width: 100%;
}

.processing-status :deep(.el-progress) {
  margin-top: 8px;
}

.hidden-file {
  display: none
}

.document-preview-body {
  position: relative;
  min-height: 220px;
  max-height: min(72vh, 760px);
  overflow: auto;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  background: var(--surface-soft, #f8fafc);
}
.preview-frame { display:block; width:100%; height:min(72vh, 700px); border:0; background:#fff; }
.preview-image { display:block; max-width:100%; max-height:min(72vh, 700px); margin:0 auto; object-fit:contain; }
.preview-text { min-height:220px; margin:0; padding:18px; color:var(--ink,#1f2937); font:13px/1.7 ui-monospace, SFMono-Regular, Consolas, monospace; white-space:pre-wrap; overflow-wrap:anywhere; }
.preview-empty { display:grid; min-height:220px; place-items:center; color:var(--text-muted,#8b95a7); font-size:13px; }
.preview-support-tip { margin-right:auto; color:var(--text-muted,#8b95a7); font-size:12px; line-height:1.5; }
.chunk-drawer-content { min-height: 180px; }
.parent-chunk-list { display: grid; gap: 14px; }
.parent-chunk-item { border: 1px solid var(--line, #e5e7eb); border-radius: 8px; overflow: hidden; background: var(--surface-soft, #f8fafc); }
.parent-chunk-head { display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; border-bottom: 1px solid var(--line, #e5e7eb); color: var(--ink, #1f2937); font-weight: 600; }
.parent-chunk-head small { color: var(--text-muted, #8b95a7); font-weight: 400; }
.parent-chunk-content { margin: 0; padding: 14px; max-height: 360px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--ink, #1f2937); font: 13px/1.7 ui-monospace, SFMono-Regular, Consolas, monospace; }
:global(html.dark) .document-preview-body { border-color:var(--line,#334155); background:var(--surface-soft,#111c2d); }
:global(html.dark) .preview-text { color:var(--ink,#e5ecf8); }
:global(html.dark) .parent-chunk-item { border-color: var(--line,#334155); background: var(--surface-soft,#111c2d); }
:global(html.dark) .parent-chunk-head { border-color: var(--line,#334155); color: var(--ink,#e5ecf8); }
:global(html.dark) .parent-chunk-content { color: var(--ink,#e5ecf8); }

@media (max-width: 760px) {
  .detail-summary {
    grid-template-columns:repeat(2, 1fr)
  }
  .preview-support-tip { display:block; margin-bottom:8px; }
}
</style>
