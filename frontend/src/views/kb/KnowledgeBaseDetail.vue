<template>
  <div>
    <div class="page-head compact">
      <div><p class="eyebrow">知识库 / 文档空间</p>
        <h1>{{ detail?.name || '知识库详情' }}</h1>
        <p>{{ detail?.description || '管理知识库文档并维护检索索引。' }}</p></div>
      <div>
        <el-button plain :icon="ArrowLeft" @click="back">返回列表</el-button>
        <input ref="fileInput" type="file" class="hidden-file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.md,.txt"
               @change="handleFile"/>
        <el-button v-if="detail?.canManage" type="primary" :icon="Upload" @click="upload">上传文档</el-button>
      </div>
    </div>
    <div class="detail-summary">
      <div><small>可见范围</small><b>{{ visibilityLabel(detail?.visibility) }}</b></div>
      <div><small>文档数量</small><b>{{ docs.length }}</b></div>
      <div><small>索引状态</small><b class="ok">{{ indexStatus }}</b></div>
      <div><small>父块 token / 重叠 token</small><b>{{ detail?.chunkSize ?? 2000 }} / {{ detail?.chunkOverlap ?? 64 }}</b></div>
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
      </div>
      <el-table :data="filteredDocs" class="audit-table detail-table" v-loading="loading">
        <el-table-column prop="fileName" label="文件名" min-width="280"/>
        <el-table-column prop="fileType" label="类型" width="90"/>
        <el-table-column prop="version" label="版本" width="90"/>
        <el-table-column prop="chunkCount" label="分块" width="90"/>
        <el-table-column label="解析状态" width="120">
          <template #default="{row}">
            <el-tooltip v-if="row.parseStatus === 'FAILED' && row.errorMsg" :content="row.errorMsg" placement="top" :show-after="200">
              <el-tag type="danger" size="small">解析失败</el-tag>
            </el-tooltip>
            <el-tag v-else :type="row.chunkStatus === 'INDEXED' ? 'success' : 'warning'" size="small">
              {{ row.chunkStatus === 'INDEXED' ? '已索引' : statusLabel(row.parseStatus || row.chunkStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180"/>
        <el-table-column v-if="detail?.canManage" label="操作" width="150">
          <template #default="{row}">
            <el-button link type="primary" @click="reindex(row.id)">重建索引</el-button>
            <el-button link type="danger" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import {ArrowLeft, Upload} from '@element-plus/icons-vue'
import {useRoute, useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {ApiError, documentApi, knowledgeBaseApi} from '../../api'
import {streamDocumentProcessing, type DocumentProcessingEvent} from '../../utils/sse'

const router = useRouter();
const route = useRoute();
const kbId = Number(route.params.id)
const fileInput = ref<HTMLInputElement>();
const loading = ref(false);
const docs = ref<any[]>([]);
const detail = ref<any>();
const keyword = ref('')
const processing = ref<DocumentProcessingEvent>()
let processingController: AbortController | undefined
const filteredDocs = computed(() => docs.value.filter(d => !keyword.value || d.fileName?.toLowerCase().includes(keyword.value.toLowerCase())))
const indexStatus = computed(() => docs.value.length && docs.value.every(d => d.chunkStatus === 'INDEXED') ? '全部已完成' : '处理中')
const visibilityLabel = (v?: string) => ({
  PRIVATE: '仅自己',
  DEPT: '本部门',
  ORG: '全公司',
  PUBLIC: '公开'
} as Record<string, string>)[v || ''] || '-'
const statusLabel = (v?: string) => ({PENDING: '待处理', PARSING: '解析中', SUCCESS: '解析成功', FAILED: '失败', INDEXING: '索引中', INDEXED: '已索引', PARTIAL: '部分完成'} as Record<string, string>)[v || ''] || v || '-'

async function load() {
  loading.value = true
  try {
    const [base, documents] = await Promise.all([knowledgeBaseApi.get(kbId), documentApi.list(kbId)]);
    const item: any = base.data
    detail.value = {...item, ownerId: item.ownerId ?? item.ownerid, deptId: item.deptId ?? item.deptid, chunkSize: item.chunkSize ?? item.chunksize, chunkOverlap: item.chunkOverlap ?? item.chunkoverlap, canManage: item.canManage ?? item.canmanage}
    docs.value = documents.data.map((doc: any) => ({...doc, kbId: doc.kbId ?? doc.kbid, fileName: doc.fileName || doc.filename, fileType: doc.fileType || doc.filetype, chunkStatus: doc.chunkStatus || doc.chunkstatus, parseStatus: doc.parseStatus || doc.parsestatus, chunkCount: doc.chunkCount ?? doc.chunkcount, errorMsg: doc.errorMsg || doc.errormsg, updatedAt: doc.updatedAt || doc.updatedat}))
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '知识库详情加载失败')
    if (error instanceof ApiError && error.status === 403) router.replace('/kb')
  } finally {
    loading.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => processingController?.abort())

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

async function watchProcessing(docId: number) {
  processingController?.abort()
  processingController = new AbortController()
  processing.value = {docId, progress: 0, message: '正在连接文档处理服务'}
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
      ElMessage.success('文档解析与索引完成')
      processing.value = undefined
    },
    onError: async error => {
      ElMessage.error(error.message || '文档处理失败')
      await load()
      processing.value = undefined
    },
  }, processingController.signal)
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

.detail-summary b.ok {
  font-family: 'Noto Sans SC';
  font-size: 13px;
  color: var(--success)
}

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

@media (max-width: 760px) {
  .detail-summary {
    grid-template-columns:repeat(2, 1fr)
  }
}
</style>
