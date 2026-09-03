<template>
  <div class="chat-page chat-history-page">
    <div class="page-head compact">
      <div>
        <p class="eyebrow">企业知识助手</p>
        <h1>智能问答</h1>
        <p>基于权限过滤的可信知识问答，答案附带原文引用。</p>
      </div>
      <el-tag type="success" effect="plain">服务正常</el-tag>
    </div>

    <div class="chat-workspace">
      <aside class="conversation-panel" aria-label="历史会话">
        <div class="conversation-panel-head">
          <b>会话记录</b>
          <el-tooltip content="新建会话" placement="top">
            <el-button circle :icon="Plus" aria-label="新建会话" @click="startNewConversation"/>
          </el-tooltip>
        </div>
        <el-scrollbar class="conversation-list">
          <div v-if="loadingConversations" class="conversation-loading">正在加载会话…</div>
          <template v-else>
            <button v-for="conversation in conversations" :key="conversation.id" class="conversation-item"
                    :class="{active: activeConversationId === conversation.id}" type="button"
                    @click="selectConversation(conversation.id)">
              <span class="conversation-item-main">
                <b>{{ conversation.title }}</b>
                <small>{{ formatTime(conversation.lastMessageAt) }}</small>
              </span>
              <el-dropdown trigger="click" @command="handleConversationCommand($event, conversation)">
                <span class="conversation-more" role="button" tabindex="0" aria-label="会话操作" @click.stop
                      @keydown.enter.stop>
                  <el-icon><MoreFilled/></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="rename">
                      <el-icon>
                        <EditPen/>
                      </el-icon>
                      重命名
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon>
                        <Delete/>
                      </el-icon>
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </button>
            <el-button v-if="conversationPage.hasMore" text class="load-conversations"
                       @click="loadConversations(conversationPage.nextCursor || undefined, true)">加载更多
            </el-button>
            <el-empty v-if="!conversations.length" :image-size="68" description="暂无历史会话"/>
          </template>
        </el-scrollbar>
      </aside>

      <section class="chat-shell">
        <div ref="messageList" class="chat-messages" aria-live="polite" aria-relevant="additions text"
             @scroll="handleMessagesScroll">
          <div v-if="loadingMessages" class="history-load-more">正在加载完整会话记录…</div>
          <div v-if="!messages.length" class="message assistant">
            <div class="msg-avatar">
              <el-icon>
                <MagicStick/>
              </el-icon>
            </div>
            <div>
              <div class="msg-bubble">你好，我是奥能电源知识助手。你可以问我关于制度、产品、流程或客户案例的问题。</div>
              <div class="suggestions">
                <button v-for="item in suggestions" :key="item" type="button" :disabled="isAsking" @click="ask(item)">
                  {{ item }}
                </button>
              </div>
            </div>
          </div>
          <div v-for="message in messages" :key="message.id" class="message"
               :class="[message.role, {error: message.error}]">
            <div v-if="message.role === 'assistant'" class="msg-avatar">
              <el-icon>
                <MagicStick/>
              </el-icon>
            </div>
            <div>
              <div class="msg-bubble">
                <div v-if="message.role === 'assistant'" class="markdown-content"
                     v-html="renderMarkdown(messageText(message))"></div>
                <template v-else>{{ message.text }}</template>
                <span v-if="message.role === 'assistant' && message.pending" class="streaming-cursor"
                      aria-label="正在生成"></span>
                <span v-if="message.role === 'assistant' && message.citations.length"
                      class="citation-note">PDF 引用 {{ message.citations.length }} 条</span>
              </div>
              <div v-if="message.role === 'assistant' && message.citations.length" class="citations"
                   aria-label="原文引用">
                <div v-for="citation in message.citations" :key="citation.key" class="citation">
                  <b>
                    <el-icon aria-hidden="true">
                      <Document/>
                    </el-icon>
                    {{ citation.fileName }}</b>
                  <small>{{ pageLabel(citation.pageNos) }}</small>
                </div>
              </div>
            </div>
          </div>
        </div>
        <form class="chat-input" @submit.prevent="ask(question)">
          <el-input v-model="question" placeholder="输入你的问题，例如：差旅报销标准是什么？" size="large"
                    :disabled="isAsking"/>
          <el-button type="primary" :icon="Promotion" native-type="submit" :loading="isAsking"
                     :disabled="isAsking || !question.trim()">发送
          </el-button>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import DOMPurify from 'dompurify'
import {marked} from 'marked'
import {nextTick, onMounted, onUnmounted, reactive, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {Delete, Document, EditPen, MagicStick, MoreFilled, Plus, Promotion} from '@element-plus/icons-vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import type {ChatConversation, ChatHistoryMessage, Citation, CursorPage} from '../../types'
import {ragApi, toApiError} from '../../api'
import {streamChat, type StreamCitation} from '../../utils/sse'

type ChatRole = 'user' | 'assistant'
type ChatCitation = Citation & { key: string }

interface ChatMessage {
  id: number
  conversationId: number
  role: ChatRole
  text: string
  citations: ChatCitation[]
  recordId?: number
  pending?: boolean
  error?: boolean
}

const route = useRoute()
const router = useRouter()
const question = ref('')
const isAsking = ref(false)
const messageList = ref<HTMLElement>()
const conversations = ref<ChatConversation[]>([])
const activeConversationId = ref<number | null>(null)
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const messages = ref<ChatMessage[]>([])
const conversationPage = ref<CursorPage<ChatConversation>>({items: [], hasMore: false})
const suggestions = ['今年的年假规则是什么？', '如何申请差旅报销？', '产品发布流程有哪些步骤？']
let localMessageId = -1
let shouldFollowOutput = true
let conversationLoadVersion = 0
let componentDisposed = false
let activeAbortController: AbortController | undefined
let cancelActiveOutput: (() => void) | undefined

marked.setOptions({breaks: true, gfm: true})

function messageText(message: ChatMessage): string {
  return message.text || (message.pending ? '正在生成回答…' : '未生成有效回答。')
}

function renderMarkdown(content: string): string {
  const visibleContent = content.replace(/(?:【来源[:：]\s*\d+\s*-\s*\d+】|\[来源[:：]?\s*\d+\s*-\s*\d+\])/g, '')
  return DOMPurify.sanitize(marked.parse(visibleContent, {async: false}) as string, {USE_PROFILES: {html: true}})
}

function handleMessagesScroll() {
  const element = messageList.value
  if (!element) return
  shouldFollowOutput = element.scrollHeight - element.scrollTop - element.clientHeight < 40
}

function scrollToLatest(force = false) {
  if (!force && !shouldFollowOutput) return
  nextTick(() => {
    const element = messageList.value
    if (element) element.scrollTop = element.scrollHeight
  })
}

function textFrom(source: StreamCitation, keys: string[], fallback = ''): string {
  for (const key of keys) {
    const value = source[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number') return String(value)
  }
  return fallback
}

function numberFrom(source: StreamCitation, keys: string[]): number | undefined {
  for (const key of keys) {
    const value = Number(source[key])
    if (Number.isInteger(value) && value > 0) return value
  }
  return undefined
}

function pageNumbers(source: StreamCitation): number[] {
  const raw = source.pageNos ?? source.page_nos
  const values = Array.isArray(raw) ? raw : [source.pageNo ?? source.page_no ?? source.page]
  return [...new Set(values.map(value => Number(value)).filter(value => Number.isInteger(value) && value > 0 && value <= 100_000))]
      .sort((left, right) => left - right)
}

function pageLabel(pages: number[]): string {
  return `第 ${pages.join('、')} 页`
}

function normalizeCitations(items: StreamCitation[]): ChatCitation[] {
  const citations = new Map<string, ChatCitation>()
  for (const item of items) {
    const pages = pageNumbers(item)
    const fileName = textFrom(item, ['fileName', 'file_name', 'filename'])
    if (!pages.length || !/\.pdf(?:$|[?#])/i.test(fileName)) continue
    const documentKey = fileName.toLowerCase()
    const existing = citations.get(documentKey)
    if (existing) {
      existing.pageNos = [...new Set([...existing.pageNos, ...pages])].sort((left, right) => left - right)
      continue
    }
    citations.set(documentKey, {
      key: `${documentKey}-${citations.size}`,
      pageNos: pages,
      fileName,
    })
  }
  return [...citations.values()]
}

function mergeCitations(current: ChatCitation[], incoming: StreamCitation[]): ChatCitation[] {
  const existing = current.map(citation => ({fileName: citation.fileName, pageNos: citation.pageNos}))
  return normalizeCitations([...existing, ...incoming])
}

function toChatMessage(message: ChatHistoryMessage, conversationId: number): ChatMessage {
  return {
    id: message.id,
    conversationId,
    role: message.role === 'assistant' ? 'assistant' : 'user',
    text: message.content,
    citations: normalizeCitations((message.citations || []).map(citation => ({...citation}))),
    recordId: message.qaRecordId,
    pending: message.status === 'PENDING' || message.status === 'STREAMING',
    error: message.status === 'FAILED',
  }
}

function conversationFromRoute(): number | null {
  const raw = route.query.conversation
  const value = Array.isArray(raw) ? raw[0] : raw
  const id = Number(value)
  return Number.isInteger(id) && id > 0 ? id : null
}

async function loadConversations(cursor?: string, append = false) {
  if (!append) loadingConversations.value = true
  try {
    const {data} = await ragApi.conversations({cursor, pageSize: 30})
    conversationPage.value = data
    conversations.value = append ? [...conversations.value, ...data.items] : data.items
  } catch (error) {
    ElMessage.error(toApiError(error).message)
  } finally {
    loadingConversations.value = false
  }
}

async function loadAllMessages(conversationId: number) {
  const version = ++conversationLoadVersion
  loadingMessages.value = true
  try {
    let cursor: string | undefined
    const allMessages: ChatHistoryMessage[] = []
    do {
      const {data} = await ragApi.messages(conversationId, {before: cursor, pageSize: 50})
      if (version !== conversationLoadVersion || activeConversationId.value !== conversationId) return
      allMessages.unshift(...data.items)
      cursor = data.hasMore && data.nextCursor ? data.nextCursor : undefined
    } while (cursor)
    if (version !== conversationLoadVersion || activeConversationId.value !== conversationId) return
    const previousCitations = new Map(messages.value.map(message => [message.id, message.citations]))
    const loadedMessages = allMessages.map(message => toChatMessage(message, conversationId))
    loadedMessages.forEach(message => {
      if (message.role !== 'assistant' || message.citations.length) return
      const citations = previousCitations.get(message.id)
      if (citations?.length) message.citations = citations
    })
    messages.value = loadedMessages
    scrollToLatest(true)
  } catch (error) {
    if (activeConversationId.value === conversationId) ElMessage.error(toApiError(error).message)
  } finally {
    if (version === conversationLoadVersion) loadingMessages.value = false
  }
}

async function selectConversation(conversationId: number) {
  if (isAsking.value || activeConversationId.value === conversationId) return
  activeConversationId.value = conversationId
  messages.value = []
  shouldFollowOutput = true
  await router.replace({path: '/chat', query: {conversation: String(conversationId)}})
  await loadAllMessages(conversationId)
}

async function startNewConversation() {
  if (isAsking.value) return
  activeConversationId.value = null
  messages.value = []
  shouldFollowOutput = true
  await router.replace({path: '/chat'})
  nextTick(() => messageList.value?.scrollTo({top: 0}))
}

async function ensureConversation(text: string): Promise<number> {
  if (activeConversationId.value) return activeConversationId.value
  const title = text.replace(/\s+/g, ' ').slice(0, 40)
  const {data} = await ragApi.createConversation({title})
  activeConversationId.value = data.id
  conversations.value.unshift(data)
  await router.replace({path: '/chat', query: {conversation: String(data.id)}})
  return data.id
}

async function ask(rawQuestion: string) {
  const text = rawQuestion.trim()
  if (!text || isAsking.value || componentDisposed) return

  let conversationId: number
  try {
    conversationId = await ensureConversation(text)
  } catch (error) {
    ElMessage.error(toApiError(error).message)
    return
  }
  if (componentDisposed) return

  // Keep local references reactive. Vue wraps objects inserted into a ref array,
  // but mutating the original raw object reference would otherwise skip updates.
  const user = reactive<ChatMessage>({id: localMessageId--, conversationId, role: 'user', text, citations: []})
  const assistant = reactive<ChatMessage>({
    id: localMessageId--,
    conversationId,
    role: 'assistant',
    text: '',
    citations: [],
    pending: true
  })
  messages.value.push(user, assistant)
  question.value = ''
  isAsking.value = true
  shouldFollowOutput = true
  scrollToLatest(true)

  const abortController = new AbortController()
  activeAbortController = abortController
  // Store Unicode code points so surrogate pairs/emoji are never split.
  const pendingOutput: string[] = []
  let renderedOutput = ''
  let outputTimer: ReturnType<typeof setTimeout> | undefined
  const typeInterval = 24
  let outputFinished = false
  let outputSettled = false
  let settleOutput: (() => void) | undefined
  const outputDrained = new Promise<void>(resolve => {
    settleOutput = resolve
  })
  const settleWhenDrained = () => {
    if (outputSettled || !outputFinished || pendingOutput.length > 0) return
    outputSettled = true
    settleOutput?.()
  }
  const pumpOutput = () => {
    if (componentDisposed || abortController.signal.aborted) {
      outputTimer = undefined
      pendingOutput.length = 0
      settleWhenDrained()
      return
    }
    if (!pendingOutput.length) {
      outputTimer = undefined
      settleWhenDrained()
      return
    }
    // Emit a small, bounded batch per tick. A larger backlog catches up slowly
    // without reverting to a single full-answer DOM update.
    const backlog = pendingOutput.length
    const batchSize = backlog > 480 ? 6 : backlog > 160 ? 4 : backlog > 40 ? 3 : 2
    const nextText = pendingOutput.splice(0, batchSize).join('')
    renderedOutput += nextText
    assistant.text = renderedOutput
    scrollToLatest()
    outputTimer = setTimeout(pumpOutput, typeInterval)
  }
  const cancelOutput = () => {
    outputFinished = true
    pendingOutput.length = 0
    if (outputTimer) clearTimeout(outputTimer)
    outputTimer = undefined
    settleWhenDrained()
  }
  const flushOutput = () => {
    if (!pendingOutput.length) return
    renderedOutput += pendingOutput.splice(0, pendingOutput.length).join('')
    assistant.text = renderedOutput
  }
  cancelActiveOutput = cancelOutput

  try {
    await streamChat({question: text, conversationId, stream: true, streamProtocol: 'openai'}, {
      onCitations: items => {
        if (componentDisposed || abortController.signal.aborted) return
        assistant.citations = mergeCitations(assistant.citations, items)
        scrollToLatest()
      },
      onDelta: delta => {
        if (!delta || componentDisposed || abortController.signal.aborted || assistant.error) return
        assistant.pending = true
        pendingOutput.push(...Array.from(delta))
        if (!outputTimer) pumpOutput()
      },
      onDone: result => {
        if (componentDisposed || abortController.signal.aborted) return
        if (result.conversationId !== undefined && result.conversationId !== conversationId) {
          cancelOutput()
          assistant.pending = false
          assistant.error = true
          assistant.text = '问答响应与当前会话不一致，请重新打开该会话。'
          return
        }
        assistant.recordId = result.recordId
        if (result.citations?.length) assistant.citations = mergeCitations(assistant.citations, result.citations)
        if (result.userMessageId) user.id = result.userMessageId
        if (result.messageId) assistant.id = result.messageId
        scrollToLatest()
      },
      onError: error => {
        if (componentDisposed || abortController.signal.aborted) return
        cancelOutput()
        assistant.pending = false
        assistant.error = true
        assistant.text = error.message || '暂时无法连接问答服务，请检查后端或稍后重试。'
        scrollToLatest()
      },
    }, abortController.signal)
    outputFinished = true
    settleWhenDrained()
    await outputDrained
    if (componentDisposed || abortController.signal.aborted) return
    assistant.pending = false
    if (!assistant.error) {
      flushOutput()
      assistant.text = renderedOutput
    }
    if (!assistant.error && !assistant.text.trim()) assistant.text = '未生成有效回答，请尝试调整问题后重试。'
    scrollToLatest()
  } finally {
    cancelOutput()
    if (cancelActiveOutput === cancelOutput) cancelActiveOutput = undefined
    if (activeAbortController === abortController) activeAbortController = undefined
    assistant.pending = false
    isAsking.value = false
    if (!componentDisposed) {
      await Promise.all([
        loadConversations(),
        activeConversationId.value === conversationId ? loadAllMessages(conversationId) : Promise.resolve(),
      ])
    }
  }
}

async function handleConversationCommand(command: 'rename' | 'delete', conversation: ChatConversation) {
  if (command === 'rename') {
    try {
      const {value} = await ElMessageBox.prompt('请输入新的会话名称', '重命名会话', {
        inputValue: conversation.title,
        inputPattern: /\S+/,
        inputErrorMessage: '会话名称不能为空',
        confirmButtonText: '保存',
        cancelButtonText: '取消',
      })
      const {data} = await ragApi.updateConversation(conversation.id, {title: value})
      const index = conversations.value.findIndex(item => item.id === data.id)
      if (index >= 0) conversations.value[index] = data
      ElMessage.success('会话名称已更新')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') ElMessage.error(toApiError(error).message)
    }
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除“${conversation.title}”吗？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await ragApi.removeConversation(conversation.id)
    conversations.value = conversations.value.filter(item => item.id !== conversation.id)
    if (activeConversationId.value === conversation.id) await startNewConversation()
    ElMessage.success('会话已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(toApiError(error).message)
  }
}

function formatTime(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
  return date.toLocaleDateString('zh-CN', {month: 'numeric', day: 'numeric'})
}

watch(() => route.query.conversation, async () => {
  const id = conversationFromRoute()
  if (!id) {
    if (activeConversationId.value && !isAsking.value) await startNewConversation()
    return
  }
  if (id !== activeConversationId.value && !isAsking.value) {
    activeConversationId.value = id
    messages.value = []
    await loadAllMessages(id)
  }
})

onMounted(async () => {
  componentDisposed = false
  await loadConversations()
  if (componentDisposed) return
  const id = conversationFromRoute()
  if (id) {
    activeConversationId.value = id
    await loadAllMessages(id)
  }
})

onUnmounted(() => {
  componentDisposed = true
  conversationLoadVersion++
  activeAbortController?.abort()
  activeAbortController = undefined
  cancelActiveOutput?.()
  cancelActiveOutput = undefined
})
</script>

<style scoped>
.chat-history-page {
  min-width: 0;
}

.chat-workspace {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  min-height: 0;
  height: 100%;
  gap: 14px;
}

.conversation-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--card-border, var(--line));
  border-radius: var(--radius-lg, 8px);
  background: var(--card-bg, #fff);
  display: flex;
  flex-direction: column;
}

.conversation-panel-head {
  min-height: 56px;
  padding: 10px 12px 10px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
}

.conversation-panel-head b {
  color: var(--ink);
  font-size: 14px;
}

.conversation-list {
  min-height: 0;
  flex: 1;
  padding: 8px;
}

.conversation-item {
  width: 100%;
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 7px 8px 10px;
  border: 0;
  border-radius: var(--radius-md, 6px);
  color: var(--ink);
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.conversation-item:hover {
  background: var(--blue-50, #eef4ff);
}

.conversation-item.active {
  color: var(--primary);
  background: var(--primary-soft, #e8efff);
}

.conversation-item-main {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 4px;
}

.conversation-item-main b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
}

.conversation-item-main small {
  color: var(--muted);
  font-size: 11px;
}

.conversation-more {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: var(--muted);
  opacity: 0;
}

.conversation-item:hover .conversation-more, .conversation-item.active .conversation-more, .conversation-more:focus {
  opacity: 1;
}

.conversation-more:hover, .conversation-more:focus {
  color: var(--primary);
  background: rgba(49, 93, 214, .1);
  outline: none;
}

.conversation-loading {
  padding: 22px 12px;
  color: var(--muted);
  font-size: 13px;
  text-align: center;
}

.load-conversations {
  width: 100%;
  margin-top: 2px;
}

.chat-workspace .chat-shell {
  min-width: 0;
}

.history-load-more {
  display: flex;
  justify-content: center;
  padding-bottom: 12px;
}

@media (max-width: 900px) {
  .chat-workspace {
    grid-template-columns: 208px minmax(0, 1fr);
    gap: 10px;
  }
}

@media (max-width: 760px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    max-height: 154px;
  }

  .conversation-panel-head {
    min-height: 44px;
  }

  .conversation-list {
    padding: 5px 8px;
  }

  .conversation-item {
    min-height: 44px;
  }

  .conversation-item-main {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .conversation-item-main small {
    flex: 0 0 auto;
  }
}
</style>
