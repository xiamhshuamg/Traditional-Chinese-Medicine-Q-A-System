// src/utils/api.js
import { API_BASE_URL } from './constants.js'

function buildUrl(url) {
    // 已经是绝对地址就不处理
    if (/^https?:\/\//i.test(url)) return url

    const base = (API_BASE_URL || '').trim()
    if (!base) return url

    const left = base.replace(/\/+$/, '')
    const right = String(url).replace(/^\/+/, '')
    return `${left}/${right}`
}

// 统一的API调用函数
async function request(url, options = {}) {
    const headers = { ...(options.headers || {}) }

    if (options.json) {
        headers['Content-Type'] = 'application/json'
    }

    const resp = await fetch(buildUrl(url), {
        method: options.method || 'GET',
        headers,
        body: options.body || null,
    })

    if (!resp.ok) {
        const msg = await resp.text().catch(() => '')
        throw new Error(msg || `请求失败: ${resp.status}`)
    }

    const ct = resp.headers.get('content-type') || ''
    if (ct.includes('application/json')) {
        return resp.json()
    }
    return resp.text()
}

/** 医生登录：POST /api/consultations/doctors/login */
export async function doctorLogin(doctorId, doctorName) {
    return request('/api/consultations/doctors/login', {
        method: 'POST',
        json: true,
        body: JSON.stringify({
            doctorId: parseInt(doctorId),
            name: doctorName
        })
    })
}

/** 开始新问诊：POST /api/consultations/start?doctorId=&patientId= */
export async function startConsultation(doctorId, patientId = '') {
    const qs = new URLSearchParams()
    qs.set('doctorId', doctorId)
    if (patientId) qs.set('patientId', patientId)

    return request(`/api/consultations/start?${qs.toString()}`, {
        method: 'POST',
    })
}

/** 按医生查询问诊列表：GET /api/consultations/byDoctor?doctorId= */
export async function listConsultationsByDoctor(doctorId) {
    return request(`/api/consultations/byDoctor?doctorId=${doctorId}`)
}

/** 获取某次问诊消息：GET /api/consultations/{id}/messages */
export async function getMessages(consultationId) {
    return request(`/api/consultations/${consultationId}/messages`)
}

/** 结束问诊：POST /api/consultations/{id}/finish */
export async function finishConsultation(consultationId, payload = {}) {
    return request(`/api/consultations/${consultationId}/finish`, {
        method: 'POST',
        json: true,
        body: JSON.stringify(payload),
    })
}

/** 发送消息并获取 AI 回复：GET /ai/chat/plain?consultationId=&prompt= */
export async function sendChat(consultationId, prompt = '', silent = false) {
    const qs = new URLSearchParams()
    qs.set('consultationId', consultationId)
    qs.set('prompt', prompt ?? '')
    if (silent) qs.set('silent', 'true')
    return request(`/ai/chat/plain?${qs.toString()}`)
}
/** 更新问诊的患者信息：PUT /api/consultations/{id}/patient */
export async function updateConsultationPatientInfo(consultationId, patientInfo) {
    return request(`/api/consultations/${consultationId}/patient`, {
        method: 'PUT',
        json: true,
        body: JSON.stringify(patientInfo)
    })
}

/** 获得医生统计数信息 */
export async function getDoctorStats(doctorId) {
    try {
        return await request(`/api/doctors/${doctorId}/stats`)
    } catch (error) {
        console.warn('统计接口不存在，返回默认值')
        return {
            todayConsultCount: 0,
            totalConsultCount: 0
        }
    }
}

/** 上传文件 */
export async function uploadFile(formData) {
    const resp = await fetch(buildUrl('/api/consultations/upload-file'), {
        method: 'POST',
        body: formData,
    })

    if (!resp.ok) {
        const msg = await resp.text().catch(() => '')
        throw new Error(msg || `文件上传失败: ${resp.status}`)
    }

    return resp.json()
}


export function chatStreamUrl(consultationId, prompt = '') {
    const qs = new URLSearchParams()
    qs.set('consultationId', String(consultationId))
    qs.set('prompt', prompt ?? '')
    return buildUrl(`/ai/chat/stream?${qs.toString()}`)
}

/** 获取可打印HTML：GET /api/consultations/{id}/report/html */
export async function getConsultationReportHtml(consultationId) {
    return request(`/api/consultations/${consultationId}/report/html`)
}

/** 获取JSON（用于预览/编辑）：GET /api/consultations/{id}/report */
export async function getConsultationReport(consultationId) {
    return request(`/api/consultations/${consultationId}/report`)
}
export function consultationReportUrl(consultationId) {
    return buildUrl(`/api/consultations/${consultationId}/report/html`)
}
// PDF 下载：需要后端提供 GET /api/consultations/{id}/report/pdf
function filenameFromDisposition(disposition, fallback = 'report.pdf') {
    try {
        const s = String(disposition || '')
        // filename*=UTF-8''xxx
        const m1 = s.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)
        if (m1?.[1]) return decodeURIComponent(m1[1]).replace(/["']/g, '')
        // filename="xxx"
        const m2 = s.match(/filename\s*=\s*("?)([^";]+)\1/i)
        if (m2?.[2]) return m2[2].trim()
    } catch (_) {}
    return fallback
}

async function requestBlob(url, options = {}) {
    const headers = { ...(options.headers || {}) }
    const resp = await fetch(buildUrl(url), {
        method: options.method || 'GET',
        headers,
        body: options.body || null
    })
    if (!resp.ok) {
        const msg = await resp.text().catch(() => '')
        throw new Error(msg || `请求失败: ${resp.status}`)
    }
    const blob = await resp.blob()
    const cd = resp.headers.get('content-disposition') || ''
    return { blob, contentDisposition: cd }
}

export function consultationReportPdfUrl(consultationId) {
    return buildUrl(`/api/consultations/${consultationId}/report/pdf`)
}

export async function downloadConsultationReportPdf(consultationId, filename) {
    const { blob, contentDisposition } = await requestBlob(
        `/api/consultations/${consultationId}/report/pdf`
    )

    const name =
        filename ||
        filenameFromDisposition(contentDisposition, `consultation_${consultationId}.pdf`)

    const url = URL.createObjectURL(blob)
    try {
        const a = document.createElement('a')
        a.href = url
        a.download = name
        a.rel = 'noopener'
        document.body.appendChild(a)
        a.click()
        a.remove()
    } finally {
        URL.revokeObjectURL(url)
    }

    return true
}


