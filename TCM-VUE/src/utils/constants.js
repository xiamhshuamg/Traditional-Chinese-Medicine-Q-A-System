// src/utils/constants.js

// 兼容 Vite(import.meta.env) 与 Vue CLI(process.env)
// 在浏览器环境下必须避免直接访问 process
function readEnv(key) {
    // Vite
    try {
        if (typeof import.meta !== 'undefined' && import.meta.env && key in import.meta.env) {
            return import.meta.env[key]
        }
    } catch (_) {}

    // Vue CLI / Node
    try {
        if (typeof process !== 'undefined' && process.env && key in process.env) {
            return process.env[key]
        }
    } catch (_) {}

    return undefined
}

// API 相关常量：
// - Vite：请在 .env.development / .env.production 写 VITE_API_BASE_URL=...
// - VueCLI：请在 .env 写 VUE_APP_API_BASE_URL=...
export const API_BASE_URL =
    readEnv('VITE_API_BASE_URL') ||
    readEnv('VUE_APP_API_BASE_URL') ||
    ''

// 文件上传相关常量
export const SUPPORTED_FILE_TYPES = [
    'application/pdf',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'text/plain',
    'image/jpeg',
    'image/png'
]

export const MAX_FILE_SIZE = 10 * 1024 * 1024 // 10MB

// 问诊状态常量
export const CONSULTATION_STATUS = {
    ONGOING: 'ONGOING',
    FINISHED: 'FINISHED'
}

// 消息发送者类型常量
export const SENDER_TYPES = {
    DOCTOR: 'DOCTOR',
    PATIENT: 'PATIENT',
    AI: 'AI',
    SYSTEM: 'SYSTEM'
}

// 消息角色常量
export const MESSAGE_ROLES = {
    USER: 'user',
    ASSISTANT: 'assistant',
    SYSTEM: 'system'
}

// 患者性别常量
export const GENDER_OPTIONS = [
    { value: '', label: '请选择' },
    { value: '男', label: '男' },
    { value: '女', label: '女' }
]

// 本地存储键名常量
export const STORAGE_KEYS = {
    DOCTOR_INFO: 'doctorInfo',
    USER_PREFERENCES: 'userPreferences'
}

// 错误消息常量
export const ERROR_MESSAGES = {
    NETWORK_ERROR: '网络连接失败，请检查网络设置',
    SERVER_ERROR: '服务器错误，请稍后重试',
    UNAUTHORIZED: '未授权访问，请重新登录',
    FILE_UPLOAD_FAILED: '文件上传失败',
    FILE_TYPE_NOT_SUPPORTED: '文件类型不支持',
    FILE_SIZE_EXCEEDED: '文件大小超过限制'
}

// 成功消息常量
export const SUCCESS_MESSAGES = {
    LOGIN_SUCCESS: '登录成功',
    FILE_UPLOAD_SUCCESS: '文件上传成功',
    CONSULTATION_CREATED: '问诊创建成功',
    CONSULTATION_FINISHED: '问诊结束成功',
    PATIENT_INFO_UPDATED: '患者信息更新成功'
}

// 提示消息常量
export const TIPS = {
    SYMPTOM_DESCRIPTION: '建议描述：主诉＋伴随症状＋舌脉＋体质＋既往史（自然语言即可）',
    FILE_UPLOAD: '支持上传 PDF、Word、文本文件和图片，最大 10MB',
    CHAT_INPUT: '请输入对患者病情的描述，Enter 发送，Shift+Enter 换行...'
}

// 默认值常量
export const DEFAULTS = {
    PATIENT_NAME: '未命名患者',
    DOCTOR_ID: 1,
    DOCTOR_NAME: '李中和'
}

// 路由路径常量
export const ROUTES = {
    LOGIN: '/login',
    CONSULTATION: '/consultation',
    HISTORY: '/history'
}

// 样式相关常量
export const STYLE_CONSTANTS = {
    BREAKPOINTS: {
        MOBILE: 768,
        TABLET: 1024,
        DESKTOP: 1280
    },
    COLORS: {
        PRIMARY: '#2563eb',
        SUCCESS: '#10b981',
        WARNING: '#f59e0b',
        ERROR: '#ef4444',
        INFO: '#3b82f6'
    }
}

// 功能开关常量
export const FEATURE_FLAGS = {
    ENABLE_FILE_UPLOAD: true,
    ENABLE_PATIENT_EDIT: true,
    ENABLE_CONSULTATION_HISTORY: true,
    ENABLE_STATISTICS: true
}

// 时间相关常量
export const TIME_CONSTANTS = {
    AUTO_SAVE_INTERVAL: 30000, // 30秒
    SESSION_TIMEOUT: 3600000, // 1小时
    MESSAGE_DELIVERY_TIMEOUT: 10000 // 10秒
}
