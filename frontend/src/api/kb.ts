import { knowledgeBaseApi, documentApi } from '../api'

/**
 * 知识库 API 的兼容导出层。知识库质量指标沿用统一的数据契约，使用 recallRate 表示召回率。
 */
export { knowledgeBaseApi, documentApi }
export const listKnowledgeBases = knowledgeBaseApi.list
export const createKnowledgeBase = knowledgeBaseApi.create
export const uploadDocument = documentApi.upload
export const listDocuments = documentApi.list
export const reindexDocument = documentApi.reindex
