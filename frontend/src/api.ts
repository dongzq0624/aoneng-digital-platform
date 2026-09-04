import axios, {AxiosError, type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig} from 'axios'
import type {ChatConversation, ChatHistoryMessage, Citation, CursorPage, DocumentItem, KnowledgeBase, RagRequest, RagResponse} from './types'

/** API base URL can be overridden at build time; the backend version prefix is added when omitted. */
const configuredApiBase = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
export const API_BASE_URL = configuredApiBase.endsWith('/v1') ? configuredApiBase : `${configuredApiBase}/v1`

export interface ApiErrorPayload {
    code?: string;
    message?: string;
    error?: string;
    details?: unknown
}

export class ApiError extends Error {
    readonly status?: number
    readonly code?: string
    readonly details?: unknown
    readonly original?: unknown

    constructor(message: string, options: {
        status?: number;
        code?: string;
        details?: unknown;
        original?: unknown
    } = {}) {
        super(message)
        this.name = 'ApiError'
        this.status = options.status
        this.code = options.code
        this.details = options.details
        this.original = options.original
    }
}

export interface LoginRequest {
    username: string;
    password: string
}

export interface UserInfo {
    id: number;
    username: string;
    realName: string;
    deptId?: number
}

export interface LoginResponse {
    token: string;
    user: UserInfo
}

export interface PageResponse<T> {
    items: T[];
    total: number;
    page?: number;
    pageSize?: number
}

export interface KnowledgeBaseRequest {
    name: string;
    description?: string;
    category?: string;
    visibility: KnowledgeBase['visibility'];
    deptId?: number;
    chunkSize?: number;
    chunkOverlap?: number
}

export interface DocumentUploadResponse extends DocumentItem {
    docId: number
}

export interface RagRecord {
    id: number;
    conversationId?: number;
    question: string;
    answer?: string;
    citations?: number;
    modelName?: string;
    latencyMs?: number;
    createdAt?: string
}

export interface FeedbackRequest {
    qaRecordId: number;
    rating: 1 | -1;
    comment?: string
}

export interface AuditLog {
    id?: number;
    time?: string;
    createdAt?: string;
    user?: string;
    username?: string;
    module?: string;
    action: string;
    detail?: string;
    result: number
}

export interface SystemUser {
    id: number;
    employeeNo: string;
    username: string;
    realName: string;
    deptId?: number;
    status: number
}

export interface Department {
    id: number;
    parentId: number;
    name: string;
    ancestors?: string;
    sort?: number;
    status: number;
    userCount?: number;
    childCount?: number
}

export interface Role {
    id: number;
    code: string;
    name: string;
    remark?: string;
    status?: number
}

export interface MenuPermission {
    id: number;
    parentId: number;
    name: string;
    perms?: string;
    path?: string;
    sort?: number;
    status: number;
    childCount?: number;
    roleCount?: number;
}

export type ApiResponse<T> = AxiosResponse<T>

function messageFromPayload(payload: unknown, fallback: string): string {
    if (typeof payload === 'string' && payload.trim()) return payload
    if (payload && typeof payload === 'object') {
        const data = payload as ApiErrorPayload
        if (typeof data.message === 'string' && data.message.trim()) return data.message
        if (typeof data.error === 'string' && data.error.trim()) return data.error
    }
    return fallback
}

export function toApiError(error: unknown): ApiError {
    if (error instanceof ApiError) return error
    if (error && typeof error === 'object' && 'response' in error) {
        const response = (error as { response?: { status?: number; data?: unknown } }).response
        const status = response?.status
        return new ApiError(messageFromPayload(response?.data, (error as {
            message?: string
        }).message || '请求失败，请稍后重试'), {status, details: response?.data, original: error})
    }
    if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<ApiErrorPayload>
        const status = axiosError.response?.status
        return new ApiError(messageFromPayload(axiosError.response?.data, axiosError.message || '请求失败，请稍后重试'), {
            status,
            code: axiosError.code,
            details: axiosError.response?.data,
            original: error,
        })
    }
    return new ApiError(error instanceof Error ? error.message : '请求失败，请稍后重试', {original: error})
}

function setAuthHeader(config: InternalAxiosRequestConfig) {
    const token = typeof localStorage !== 'undefined' ? localStorage.getItem('rag_token') : null
    if (token) {
        config.headers = config.headers ?? {}
        config.headers.Authorization = `Bearer ${token}`
    }
    return config
}

export const request: AxiosInstance = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000,
    headers: {Accept: 'application/json'}
})

request.interceptors.request.use(setAuthHeader, (error) => Promise.reject(toApiError(error)))
request.interceptors.response.use(
    (response) => {
        // 后端成功响应统一包装为 {code, message, data}；对调用方暴露实际业务载荷。
        const payload = response.data
        if (payload && typeof payload === 'object'
            && typeof payload.code === 'string'
            && Object.prototype.hasOwnProperty.call(payload, 'data')) {
            response.data = payload.data
        }
        return response
    },
    (error: unknown) => {
        const apiError = toApiError(error)
        if (apiError.status === 401) {
            if (typeof localStorage !== 'undefined') localStorage.removeItem('rag_token')
            if (typeof location !== 'undefined' && location.pathname !== '/login') location.href = '/login'
        }
        return Promise.reject(apiError)
    },
)

export const authApi = {
    login: (payload: LoginRequest) => request.post<LoginResponse>('/auth/login', payload),
    me: () => request.get<UserInfo>('/auth/me'),
}

export const knowledgeBaseApi = {
    list: (params?: {
        keyword?: string;
        visibility?: KnowledgeBase['visibility'];
        page?: number;
        pageSize?: number
    }) => request.get<KnowledgeBase[] | PageResponse<KnowledgeBase>>('/kb/bases', {params}),
    get: (id: number) => request.get<KnowledgeBase>(`/kb/bases/${id}`),
    create: (payload: KnowledgeBaseRequest) => request.post<KnowledgeBase>('/kb/bases', payload),
    update: (id: number, payload: Partial<KnowledgeBaseRequest>) => request.put<KnowledgeBase>(`/kb/bases/${id}`, payload),
    remove: (id: number) => request.delete<{ id: number; deleted: boolean }>(`/kb/bases/${id}`),
    allowedDepartments: (id: number) => request.get<{ departmentIds: number[] }>(`/kb/bases/${id}/departments`),
    updateAllowedDepartments: (id: number, departmentIds: number[]) => request.put<KnowledgeBase>(`/kb/bases/${id}/departments`, {departmentIds}),
}

export const documentApi = {
    upload: (kbId: number, file: File, onUploadProgress?: (progress: number) => void) => {
        const form = new FormData();
        form.append('file', file);
        return request.post<DocumentUploadResponse>(`/kb/bases/${kbId}/docs`, form, {
            headers: {'Content-Type': 'multipart/form-data'},
            onUploadProgress: (event) => onUploadProgress?.(event.total ? Math.round((event.loaded / event.total) * 100) : 0)
        })
    },
    list: (kbId: number) => request.get<DocumentItem[]>(`/kb/bases/${kbId}/docs`),
    get: (id: number) => request.get<DocumentItem>(`/kb/docs/${id}`),
    remove: (id: number) => request.delete<{ docId: number; deleted: boolean }>(`/kb/docs/${id}`),
    reindex: (id: number) => request.post<DocumentItem>(`/kb/docs/${id}/reindex`),
}

export const ragApi = {
    records: (params?: {
        page?: number;
        pageSize?: number
    }) => request.get<PageResponse<RagRecord> | RagRecord[]>('/rag/records', {params}),
    record: (id: number) => request.get<RagRecord>(`/rag/records/${id}`),
    feedback: (payload: FeedbackRequest) => request.post<{ success: boolean }>('/rag/feedback', payload),
    chunk: (id: number) => request.get<Citation>(`/rag/chunks/${id}`),
    chat: (payload: RagRequest) => request.post<RagResponse>('/rag/chat/stream', payload),
    conversations: (params?: {cursor?: string; pageSize?: number}) => request.get<CursorPage<ChatConversation>>('/rag/conversations', {params}),
    createConversation: (payload: {title: string; kbIds?: number[]}) => request.post<ChatConversation>('/rag/conversations', payload),
    conversation: (id: number) => request.get<ChatConversation>(`/rag/conversations/${id}`),
    updateConversation: (id: number, payload: {title: string}) => request.patch<ChatConversation>(`/rag/conversations/${id}`, payload),
    removeConversation: (id: number) => request.delete<{id: number; deleted: boolean}>(`/rag/conversations/${id}`),
    messages: (id: number, params?: {before?: string; pageSize?: number}) => request.get<CursorPage<ChatHistoryMessage>>(`/rag/conversations/${id}/messages`, {params}),
}

export const systemApi = {
    users: (params?: {
        keyword?: string;
        deptId?: number;
        page?: number;
        pageSize?: number
    }) => request.get<PageResponse<SystemUser>>('/system/users', {params}),
    createUser: (payload: Partial<SystemUser> & {
        password?: string
    }) => request.post<SystemUser>('/system/users', payload),
    updateUser: (id: number, payload: Partial<SystemUser>) => request.put<SystemUser>(`/system/users/${id}`, payload),
    removeUser: (id: number) => request.delete<{ id: number; deleted: boolean }>(`/system/users/${id}`),
    resetPassword: (id: number) => request.post<{ id: number; reset: boolean }>(`/system/users/${id}/reset-password`),
    departments: () => request.get<Department[]>('/system/depts'),
    createDepartment: (payload: Partial<Department>) => request.post<Department>('/system/depts', payload),
    updateDepartment: (id: number, payload: Partial<Department>) => request.put<Department>(`/system/depts/${id}`, payload),
    removeDepartment: (id: number) => request.delete<{ id: number; deleted: boolean }>(`/system/depts/${id}`),
    roles: () => request.get<Role[]>('/system/roles'),
    createRole: (payload: Partial<Role>) => request.post<Role>('/system/roles', payload),
    updateRole: (id: number, payload: Partial<Role>) => request.put<Role>(`/system/roles/${id}`, payload),
    removeRole: (id: number) => request.delete<{ id: number; deleted: boolean }>(`/system/roles/${id}`),
    roleMenus: (id: number) => request.get<{ menuIds: number[] }>(`/system/roles/${id}/menus`),
    updateRoleMenus: (id: number, menuIds: number[]) => request.put<{ id: number; updated: boolean }>(`/system/roles/${id}/menus`, {menuIds}),
    menus: () => request.get<MenuPermission[]>('/system/menus'),
    myMenus: () => request.get<{ menuIds: number[] }>('/system/my-menus'),
    createMenu: (payload: Partial<MenuPermission>) => request.post<MenuPermission>('/system/menus', payload),
    updateMenu: (id: number, payload: Partial<MenuPermission>) => request.put<MenuPermission>(`/system/menus/${id}`, payload),
    removeMenu: (id: number) => request.delete<{ id: number; deleted: boolean }>(`/system/menus/${id}`),
}

export const auditApi = {
    logs: (params?: {
        keyword?: string;
        module?: string;
        action?: string;
        page?: number;
        pageSize?: number
    }) => request.get<PageResponse<AuditLog>>('/audit/logs', {params}),
}

export interface MonitoringStage {
    operation: string;
    calls: number;
    avg_ms: number;
    p50_ms: number;
    p95_ms: number;
    success_rate: number
}

export interface MonitoringQuality {
    evaluated: number;
    failed: number;
    faithfulness: number;
    answer_correctness: number;
    context_precision: number;
    context_recall: number;
    citation_completeness: number;
    /** 离线评估召回率（0-1），兼容 Recall@5 与历史评估字段。 */
    recallAt5: number
}

export interface MonitoringRetrievalTrend {
    period: string;
    /** Recall@5 比例（0-1，超过 1 的历史百分比值也可兼容展示）。 */
    recallAt5: number
}

export interface MonitoringRetrievalQuality {
    hybrid: Array<Record<string, unknown>>;
    hotDocuments: Array<Record<string, unknown>>;
    recallTrend: MonitoringRetrievalTrend[];
    similarity: Array<Record<string, unknown>>;
    lowSimilarityQueries: string[]
}

export interface MonitoringDashboard {
    updatedAt: string;
    overview: {
        from: string;
        to: string;
        stages: MonitoringStage[];
        quality: MonitoringQuality;
        summary: {calls: number; total_ms: number; errors: number; throughput: number};
        performance?: Record<string, number>
    };
    retrievalQuality?: MonitoringRetrievalQuality;
    [key: string]: unknown
}

export interface DashboardSummary {
    knowledgeBaseCount: number;
    documentCount: number;
    todayQaCount: number;
    recallRate: number;
    pendingCount: number;
    recentKbs: Array<KnowledgeBase & { docs?: number }>;
    todos: Array<{ title: string; type?: string; time?: string; color?: string }>
}

export const monitoringApi = {
    dashboard: (params?: {from?: string; to?: string; kbId?: number; docId?: number; conversationId?: number}) =>
        request.get<MonitoringDashboard>('/monitoring/dashboard', {params}),
      overview: (params?: {from?: string; to?: string; kbId?: number; docId?: number; conversationId?: number}) =>
         request.get<{from: string; to: string; stages: MonitoringStage[]; quality: MonitoringQuality; summary: {calls: number; total_ms: number; errors: number; throughput: number}; performance?: Record<string, number>}>('/monitoring/overview', {params}),
      fileProcessing: (params?: {from?: string; to?: string}) =>
        request.get<{from: string; to: string; items: FileProcessingItem[]}>('/monitoring/file-processing', {params}),
}

export interface FileProcessingItem {
    docId: number;
    fileName: string;
    fileType?: string;
    uploadMs: number;
    parseMs: number;
    parseMethod?: string;
    layoutChunkMs: number;
    parentChildChunkMs: number;
    vectorizationMs: number;
    postgresMs: number;
    milvusMs: number;
    totalMs: number;
    status: string;
    errorMessage?: string | null;
}

export const dashboardApi = {
    summary: () => request.get<DashboardSummary>('/dashboard/summary'),
}
