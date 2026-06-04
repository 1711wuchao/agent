import { http } from './http'

export function listTemplates() {
  return http.get('/templates')
}

export function listContracts() {
  return http.get('/contracts')
}

export function createContractDraft(payload) {
  return http.post('/contracts', payload)
}

export function deleteContract(id) {
  return http.delete(`/contracts/${id}`)
}

export function generateDocx(id) {
  return http.post(`/contracts/${id}/generate-docx`)
}

export function exportPdf(id) {
  return http.post(`/contracts/${id}/export-pdf`)
}

export function contractFileUrl(id, fileType) {
  return `/api/contracts/${id}/files/${fileType}`
}

/**
 * @typedef {Object} ParsedContractNumber
 * @property {string} date 日期部分，格式 YYYYMMDD
 * @property {string} serial 4 位流水号
 */

/**
 * 生成合同编号。
 * 流水号存储方式：后端查询 MySQL contract 表的 contract_no 字段。
 *
 * @param {Date|string} date 签订日期，支持 Date 对象、YYYYMMDD 字符串、yyyy-MM-dd 字符串
 * @returns {Promise<import('axios').AxiosResponse<{ contractNumber: string }>>}
 */
export function generateContractNumber(date) {
  const normalizedDate = date instanceof Date
    ? `${date.getFullYear()}${String(date.getMonth() + 1).padStart(2, '0')}${String(date.getDate()).padStart(2, '0')}`
    : date
  return http.get('/contract-numbers/generate', { params: { date: normalizedDate } })
}

/**
 * 解析合同编号。
 *
 * @param {string} contractNumber 合同编号，例如 20260604-0003
 * @returns {Promise<import('axios').AxiosResponse<ParsedContractNumber>>}
 */
export function parseContractNumber(contractNumber) {
  return http.get('/contract-numbers/parse', { params: { contractNumber } })
}

/**
 * 校验合同编号是否符合 YYYYMMDD-XXXX。
 *
 * @param {string} contractNumber 合同编号
 * @returns {Promise<import('axios').AxiosResponse<{ valid: boolean }>>}
 */
export function validateContractNumber(contractNumber) {
  return http.get('/contract-numbers/validate', { params: { contractNumber } })
}

/**
 * 查询指定签订日期下的所有合同编号。
 *
 * @param {string} date 日期字符串 YYYYMMDD
 * @returns {Promise<import('axios').AxiosResponse<string[]>>}
 */
export function getContractsByDate(date) {
  return http.get(`/contract-numbers/date/${date}`)
}

export function uploadBatchExcel(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/contracts/batch/excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function callKimiTool(payload, token = '') {
  return http.post('/ai/tools/call', payload, {
    headers: token ? { 'X-Agent-Token': token } : {}
  })
}

export function callContractAgent(payload) {
  return http.post('/ai/agent/chat', payload, {
    timeout: 120000
  })
}

export function seedKnowledge() {
  return http.post('/knowledge/seed')
}

export function addKnowledgeChunk(payload) {
  return http.post('/knowledge/chunks', payload)
}

export function searchKnowledge(params) {
  return http.get('/knowledge/search', { params })
}
