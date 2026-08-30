export type Visibility = 'PRIVATE' | 'DEPT' | 'ORG' | 'PUBLIC'

export interface KnowledgeBase {
    id: number;
    name: string;
    description?: string;
    category?: string;
    visibility: Visibility;
    docCount: number;
    updatedAt: string;
    canManage?: boolean;
    canConfigureDepartments?: boolean;
    allowedDeptIds?: number[]
}

export interface Citation {
    fileName: string;
    pageNo?: number;
    pageNos: number[]
}

export interface DocumentItem { id:number; kbId:number; fileName:string; fileType:string; fileSize:number; version:number; parseStatus:'PENDING'|'PARSING'|'SUCCESS'|'FAILED'; chunkStatus:'PENDING'|'INDEXING'|'INDEXED'|'PARTIAL'|'FAILED'; chunkCount:number; errorMsg?:string }
export interface RagRequest { question:string; kbIds?:number[]; conversationId?:number; stream?:boolean; topK?:number }
export interface RagResponse { recordId:number; answer:string; citations:Citation[]; latencyMs:number; conversationId?:number; userMessageId?:number; messageId?:number }

export interface ChatConversation {
    id: number;
    title: string;
    selectedKbIds: number[];
    lastMessageAt: string;
    createdAt: string;
    updatedAt: string
}

export type ChatMessageRole = 'user' | 'assistant' | 'system'
export type ChatMessageStatus = 'PENDING' | 'STREAMING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface ChatHistoryMessage {
    id: number;
    sequenceNo: number;
    role: ChatMessageRole;
    content: string;
    status: ChatMessageStatus;
    sourceKbIds: number[];
    retrievedChunkIds: number[];
    modelName?: string;
    latencyMs?: number;
    errorMessage?: string;
    qaRecordId?: number;
    citations: Citation[];
    createdAt: string;
    completedAt?: string
}

export interface CursorPage<T> {
    items: T[];
    nextCursor?: string | null;
    hasMore: boolean
}
