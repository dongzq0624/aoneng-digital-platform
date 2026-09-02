import {API_BASE_URL, toApiError} from '../api'

export type StreamCitation = Record<string, unknown>

export interface StreamDone {
    recordId?: number
    conversationId?: number
    userMessageId?: number
    messageId?: number
}

export interface StreamHandlers {
    onCitations?: (items: StreamCitation[]) => void
    onDelta?: (text: string) => void
    onDone?: (result: StreamDone) => void
    onError?: (error: Error) => void
}

export interface DocumentProcessingEvent {
    docId: number
    parseStatus?: string
    chunkStatus?: string
    progress?: number
    completedChunks?: number
    totalChunks?: number
    chunkCount?: number
    chunkId?: number
    sequence?: number
    pageNo?: number
    content?: string
    message?: string
}

export interface DocumentProcessingHandlers {
    onProgress?: (event: DocumentProcessingEvent) => void
    onSegment?: (event: DocumentProcessingEvent) => void
    onDone?: (event: DocumentProcessingEvent) => void
    onError?: (error: Error) => void
}

interface SseEvent {
    event: string
    data: string
}

function parseSseEvent(block: string): SseEvent | undefined {
    let event = 'message'
    const dataLines: string[] = []
    for (const line of block.split(/\r?\n/)) {
        if (!line || line.startsWith(':')) continue
        const separator = line.indexOf(':')
        const field = separator < 0 ? line : line.slice(0, separator)
        const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /, '')
        if (field === 'event') event = value
        if (field === 'data') dataLines.push(value)
    }
    return dataLines.length ? {event, data: dataLines.join('\n')} : undefined
}

function readJson(data: string): unknown {
    try {
        return JSON.parse(data)
    } catch {
        return data
    }
}

function citationItems(data: unknown): StreamCitation[] {
    const values = Array.isArray(data)
        ? data
        : data && typeof data === 'object'
            ? (data as { citations?: unknown; items?: unknown; delta?: {sources?: unknown} }).citations
              ?? (data as { items?: unknown }).items
              ?? (data as { delta?: {sources?: unknown} }).delta?.sources
            : undefined
    return Array.isArray(values)
        ? values.filter((value): value is StreamCitation => Boolean(value) && typeof value === 'object')
        : []
}

function textValue(data: unknown, keys: string[]): string {
    if (typeof data === 'string') return data
    if (!data || typeof data !== 'object') return ''
    const values = data as Record<string, unknown>
    for (const key of keys) {
        const value = values[key]
        if (typeof value === 'string') return value
    }
    return ''
}

function numberValue(data: unknown, key: string): number | undefined {
    if (!data || typeof data !== 'object') return undefined
    const value = (data as Record<string, unknown>)[key]
    const number = typeof value === 'number' ? value : Number(value)
    return Number.isFinite(number) ? number : undefined
}

type StreamTerminalEvent = 'done' | 'error'

function dispatchSseEvent(block: string, handlers: StreamHandlers, state: {standard: boolean}): StreamTerminalEvent | undefined {
    const sseEvent = parseSseEvent(block)
    if (!sseEvent) return
    const data = readJson(sseEvent.data)
    if (sseEvent.event === 'citation' || sseEvent.event === 'citations' || sseEvent.event === 'sources') {
        handlers.onCitations?.(citationItems(data))
    } else if (sseEvent.event === 'chunk') {
        if (!state.standard) handlers.onDelta?.(textValue(data, ['delta', 'text', 'content']))
    } else if (sseEvent.event === 'message') {
        if (data === '[DONE]') return 'done'
        const sources = data && typeof data === 'object'
            ? (data as {delta?: {sources?: unknown}}).delta?.sources
            : undefined
        if (Array.isArray(sources)) handlers.onCitations?.(citationItems(sources))
        const delta = data && typeof data === 'object'
            ? (data as {choices?: Array<{delta?: {content?: unknown}}>}).choices?.[0]?.delta?.content
            : undefined
        if (typeof delta === 'string' && delta) {
            state.standard = true
            handlers.onDelta?.(delta)
        }
    } else if (sseEvent.event === 'done') {
        handlers.onDone?.({
            recordId: numberValue(data, 'recordId'),
            conversationId: numberValue(data, 'conversationId'),
            userMessageId: numberValue(data, 'userMessageId'),
            messageId: numberValue(data, 'messageId'),
        })
    } else if (sseEvent.event === 'error') {
        handlers.onError?.(new Error(textValue(data, ['message', 'error']) || '问答服务返回错误'))
    }
    if (sseEvent.event === 'done' || sseEvent.event === 'error') return sseEvent.event
}

export async function streamChat(payload: Record<string, unknown>, handlers: StreamHandlers, signal?: AbortSignal) {
    try {
        const token = typeof localStorage !== 'undefined' ? localStorage.getItem('rag_token') : null
        const res = await fetch(`${API_BASE_URL}/rag/chat/stream`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})},
            body: JSON.stringify(payload),
            signal,
        })
        if (!res.ok || !res.body) {
            let body: unknown
            try {
                body = await res.json()
            } catch {
                body = undefined
            }
            if (res.status === 401 && typeof localStorage !== 'undefined') {
                localStorage.removeItem('rag_token')
                if (typeof location !== 'undefined' && location.pathname !== '/login') location.href = '/login'
            }
            throw toApiError({response: {status: res.status, data: body}, message: `问答请求失败 (${res.status})`})
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        let terminal = false
        const state = {standard: false}
        try {
            while (true) {
                const {done, value} = await reader.read()
                if (value) {
                    buffer += decoder.decode(value, {stream: !done}).replace(/\r\n/g, '\n')
                    const blocks = buffer.split('\n\n')
                    buffer = blocks.pop() || ''
                    for (const block of blocks) {
                        const terminalEvent = dispatchSseEvent(block, handlers, state)
                        if (terminalEvent) {
                            terminal = true
                            try {
                                await reader.cancel()
                            } catch {
                                // The response may already be closed after a terminal SSE event.
                            }
                            return
                        }
                    }
                }
                if (done) break
            }
            buffer += decoder.decode().replace(/\r\n/g, '\n')
            if (buffer.trim()) dispatchSseEvent(buffer, handlers, state)
            if (!terminal) handlers.onError?.(new Error('问答连接已意外关闭'))
        } finally {
            reader.releaseLock()
        }
    } catch (error) {
        if ((error as { name?: string })?.name === 'AbortError') return
        handlers.onError?.(toApiError(error))
    }
}

function documentEvent(data: unknown): DocumentProcessingEvent {
    const value = data && typeof data === 'object' ? data as Record<string, unknown> : {}
    const numeric = (key: string) => numberValue(value, key)
    return {
        docId: numeric('docId') || 0,
        parseStatus: textValue(value, ['parseStatus']),
        chunkStatus: textValue(value, ['chunkStatus']),
        progress: numeric('progress'),
        completedChunks: numeric('completedChunks'),
        totalChunks: numeric('totalChunks'),
        chunkCount: numeric('chunkCount'),
        chunkId: numeric('chunkId'),
        sequence: numeric('sequence'),
        pageNo: numeric('pageNo'),
        content: textValue(value, ['content']),
        message: textValue(value, ['message']),
    }
}

export async function streamDocumentProcessing(docId: number, handlers: DocumentProcessingHandlers, signal?: AbortSignal) {
    try {
        const token = typeof localStorage !== 'undefined' ? localStorage.getItem('rag_token') : null
        const res = await fetch(`${API_BASE_URL}/kb/docs/${docId}/events`, {
            headers: {Accept: 'text/event-stream', ...(token ? {Authorization: `Bearer ${token}`} : {})},
            signal,
        })
        if (!res.ok || !res.body) {
            let body: unknown
            try {
                body = await res.json()
            } catch {
                body = undefined
            }
            if (res.status === 401 && typeof localStorage !== 'undefined') {
                localStorage.removeItem('rag_token')
                if (typeof location !== 'undefined' && location.pathname !== '/login') location.href = '/login'
            }
            throw toApiError({response: {status: res.status, data: body}, message: `文档处理订阅失败 (${res.status})`})
        }

        const reader = res.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''
        try {
            while (true) {
                const {done, value} = await reader.read()
                if (value) {
                    buffer += decoder.decode(value, {stream: !done}).replace(/\r\n/g, '\n')
                    const blocks = buffer.split('\n\n')
                    buffer = blocks.pop() || ''
                    for (const block of blocks) {
                        const event = parseSseEvent(block)
                        if (!event) continue
                        const data = documentEvent(readJson(event.data))
                        if (event.event === 'progress') handlers.onProgress?.(data)
                        if (event.event === 'segment') handlers.onSegment?.(data)
                        if (event.event === 'done') {
                            handlers.onDone?.(data)
                            return
                        }
                        if (event.event === 'error') {
                            handlers.onError?.(new Error(data.message || '文档处理失败'))
                            return
                        }
                    }
                }
                if (done) break
            }
        } finally {
            reader.releaseLock()
        }
    } catch (error) {
        if ((error as { name?: string })?.name !== 'AbortError') handlers.onError?.(toApiError(error))
    }
}
