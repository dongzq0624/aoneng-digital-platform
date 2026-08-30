import { knowledgeBaseApi, documentApi } from '../api'
export { knowledgeBaseApi, documentApi }
export const listKnowledgeBases = knowledgeBaseApi.list
export const createKnowledgeBase = knowledgeBaseApi.create
export const uploadDocument = documentApi.upload
export const listDocuments = documentApi.list
export const reindexDocument = documentApi.reindex
