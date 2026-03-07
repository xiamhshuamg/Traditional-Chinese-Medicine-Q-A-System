<template>
  <main class="chat-layout">
    <header class="chat-header">
      <div class="chat-title">
        <button
            class="btn-icon ghost"
            v-if="!sidebarOpen"
            @click="$emit('open-sidebar')"
            title="展开侧边栏"
            aria-label="展开侧边栏"
        >
          ☰
        </button>

        <div class="title-block">
          <div class="title-row">
            <h2 class="title">{{ currentTitle }}</h2>
            <button
                class="btn-sm outline"
                :disabled="!currentId"
                @click="$emit('update-patient', currentId)"
            >
              编辑
            </button>
          </div>

          <div class="subtitle">{{ currentSubtitle }}</div>

          <div class="patient-info" v-if="currentConsultation">
            患者：{{ currentConsultation.patientName || '未命名' }}
            <span class="patient-meta" v-if="currentConsultation.patientAge">
              年龄：{{ currentConsultation.patientAge }}
            </span>
            <span class="patient-meta" v-if="currentConsultation.patientGender">
              性别：{{ currentConsultation.patientGender }}
            </span>
            <span class="patient-meta" v-if="doctorName">
              医生：{{ doctorName }}
            </span>
          </div>

          <div class="patient-info" v-else-if="doctorName">
            医生：{{ doctorName }}
          </div>
        </div>
      </div>

      <div class="chat-actions">
        <button class="btn-sm outline" :disabled="!currentId" @click="printReport">
          打印处方/报告
        </button>
        <button
            class="btn-sm outline"
            :disabled="!currentId || finishing"
            @click="$emit('finish-consultation', currentId)"
        >
          {{ finishing ? '提交中.' : '结束本次问诊' }}
        </button>
        <button class="btn-sm" @click="handleNewConsultation">新建问诊</button>
      </div>
    </header>

    <MessageList :messages="messages" :sending="activeSending" ref="messageListRef" />
    <InputArea :currentId="currentId" :sending="globalBusy" @send-message="sendMessage" />

  </main>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount, nextTick, onActivated, reactive } from 'vue'
import MessageList from './MessageList.vue'
import InputArea from './InputArea.vue'
import { getMessages, chatStreamUrl, consultationReportUrl ,getConsultationReportHtml} from '../utils/api.js'

const props = defineProps({
  currentId: Number,
  consultations: { type: Array, default: () => [] },
  doctor: { type: Object, default: () => ({}) },
  sidebarOpen: { type: Boolean, default: true }
})

const emit = defineEmits(['finish-consultation', 'update-patient', 'new-consultation', 'open-sidebar'])

// 1. 添加 MessageList 的 ref
const messageListRef = ref(null) // 引用 MessageList 组件

const finishing = ref(false)

/** 每个 consultationId 一个会话状态（含 messages / EventSource / streaming 状态） */
const sessions = reactive({})

function ensureSession(id) {
  const key = String(id)
  if (!sessions[key]) {
    sessions[key] = {
      id,
      loaded: false,
      messages: [],
      sending: false,
      es: null,
      currentAiMsgId: null,
      streamData: {
        thought: '',
        answer: '',
        kgInfo: null,
        startTime: 0,
        consultationId: id
      }
    }
  }
  return sessions[key]
}

const activeSession = computed(() => (props.currentId ? ensureSession(props.currentId) : null))

/** 页面展示用：当前会话的消息/发送态 */
const messages = computed(() => activeSession.value?.messages ?? [])
const activeSending = computed(() => activeSession.value?.sending ?? false)

/** 全局 busy：只要任何会话在流式，就禁用输入（避免你开多个流把 es 覆盖掉） */
const globalBusy = computed(() => Object.values(sessions).some(s => s?.sending))

// 医生姓名
const doctorName = computed(() => props.doctor?.name || props.doctor?.doctorName || '')

// 当前问诊信息
const currentConsultation = computed(() => {
  if (!props.currentId) return null
  return props.consultations.find(x => x.id === props.currentId) || null
})

// 标题和副标题
const currentTitle = computed(() => {
  if (!props.currentId) return '尚未开始问诊'
  const c = currentConsultation.value
  if (!c) return '问诊会话'
  const tag = String(c.status || '').toUpperCase() === 'ONGOING' ? '当前问诊' : '历史问诊'
  const name = c.patientName || '未命名'
  return `${tag} · ${name}`
})

const currentSubtitle = computed(() => {
  if (!props.currentId) return ''
  const c = currentConsultation.value
  if (!c) return ''
  const startRaw = c.startTime || c.createdAt
  const endRaw = c.endTime
  const start = formatTime(startRaw)
  const end = formatTime(endRaw)
  if (c.status === 'FINISHED' || endRaw) return `开始时间：${start} · 结束时间：${end || '—'}`
  return `开始时间：${start}`
})

// 时间格式化
function formatTime(v) {
  if (!v) return ''
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return v
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}

watch(
    () => props.currentId,
    async (newId) => {
      if (!newId) return

      const s = ensureSession(newId)

      // 第一次进入该会话：优先恢复本地流式缓存，其次拉历史
      if (!s.loaded) {
        const restored = restoreStreamingState(newId)
        if (!restored) {
          await loadMessages(newId)
        }
        s.loaded = true
      }

      setTimeout(() => {
        if (props.currentId === newId) scrollToBottom()
      }, 100)
    },
    { immediate: true }
)


async function loadMessages(id) {
  const s = ensureSession(id)
  try {
    const list = await getMessages(id)
    s.messages = Array.isArray(list) ? list : []
  } catch (e) {
    console.error('加载消息失败', e)
    s.messages = []
  }
}

/** 保存流式状态（按会话 id 保存；不再用 messages.value / props.currentId） */
function saveStreamingState(consultationId) {
  const s = ensureSession(consultationId)
  if (!consultationId || !s.currentAiMsgId) return

  const state = {
    consultationId,
    aiMsgId: s.currentAiMsgId,
    thought: s.streamData.thought,
    answer: s.streamData.answer,
    kgInfo: s.streamData.kgInfo,
    timestamp: Date.now(),
    //深拷贝一份消息快照（避免 reactive 引用导致存储异常）
    messages: JSON.parse(JSON.stringify(s.messages))
  }

  localStorage.setItem(`streaming_${consultationId}`, JSON.stringify(state))
}

function restoreStreamingState(consultationId) {
  const raw = localStorage.getItem(`streaming_${consultationId}`)
  if (!raw) return false

  try {
    const state = JSON.parse(raw)
    // 只恢复 2 分钟内的缓存
    if (Date.now() - state.timestamp > 2 * 60 * 1000) {
      clearStreamingState(consultationId)
      return false
    }

    const s = ensureSession(consultationId)
    s.messages = Array.isArray(state.messages) ? state.messages : []
    s.currentAiMsgId = state.aiMsgId || null
    s.streamData = {
      ...s.streamData,
      thought: state.thought || '',
      answer: state.answer || '',
      kgInfo: state.kgInfo || null
    }

    //关键：不要在这里把 sending=true（因为并不能保证连接仍在）
    //也不要强行把 isStreaming=false（否则你看到“卡住”）
    s.sending = false

    return true
  } catch (e) {
    console.error('恢复流式状态失败:', e)
    clearStreamingState(consultationId)
    return false
  }
}

function clearStreamingState(consultationId) {
  localStorage.removeItem(`streaming_${consultationId}`)
}

function closeStream(session) {
  if (session?.es) {
    session.es.close()
    session.es = null
  }
}

onBeforeUnmount(() => {
  // 页面离开时把所有 SSE 关掉（否则会泄漏）
  Object.values(sessions).forEach(s => closeStream(s))
})


//核心：流式发送信息 + 实时更新
async function sendMessage(payload) {
  if (!props.currentId) return

  const consultationId = props.currentId
  const session = ensureSession(consultationId)

  if (globalBusy.value || session.sending) return

  const text = typeof payload === 'string' ? payload : (payload?.content ?? '')
  const silent = typeof payload === 'object' ? !!payload.silent : false

  session.sending = true
  session.currentAiMsgId = `${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
  const aiMsgId = session.currentAiMsgId

  session.streamData = {
    thought: '',
    answer: '',
    kgInfo: null,
    startTime: Date.now(),
    consultationId
  }

  // 1) 用户消息
  if (!silent && text.trim()) {
    session.messages.push({
      id: `${Date.now()}_user_${Math.random().toString(36).slice(2, 9)}`,
      role: 'user',
      content: text,
      consultationId,
      timestamp: Date.now()
    })
  }

  // 2) AI 占位消息（必须先插入，否则 idxOfAi 永远 -1）
  session.messages.push({
    id: aiMsgId,
    role: 'assistant',
    content: '',
    thought: '',
    kgInfo: {
      hit: false,
      detail: '',
      text: '知识图谱：分析中…',
      type: 'no',
      expandable: false,
      processing: true
    },
    isStreaming: true,
    consultationId,
    timestamp: Date.now()
  })

  saveStreamingState(consultationId)
  if (props.currentId === consultationId) await scrollToBottom()

  // 关旧流
  closeStream(session)

  // ====== chunk 合并刷新 + 存盘防抖 ======
  const idxOfAi = () => session.messages.findIndex(m => m.id === aiMsgId)

  let pendingThought = ''
  let pendingAnswer = ''
  let flushRaf = 0
  let saveTimer = 0

  const scheduleSave = () => {
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => saveStreamingState(consultationId), 250)
  }

  const flushChunks = () => {
    flushRaf = 0
    const idx = idxOfAi()
    if (idx === -1) return

    if (pendingThought) {
      session.messages[idx].thought = (session.messages[idx].thought || '') + pendingThought
      session.streamData.thought = (session.streamData.thought || '') + pendingThought
      pendingThought = ''
    }
    if (pendingAnswer) {
      session.messages[idx].content = (session.messages[idx].content || '') + pendingAnswer
      session.streamData.answer = (session.streamData.answer || '') + pendingAnswer
      pendingAnswer = ''
    }
    scheduleSave()
  }

  const scheduleFlush = () => {
    if (flushRaf) return
    flushRaf = requestAnimationFrame(flushChunks)
  }

  const forceFlush = () => {
    if (flushRaf) cancelAnimationFrame(flushRaf)
    flushChunks()
    clearTimeout(saveTimer)
    saveStreamingState(consultationId)
  }

  // ======建立 SSE（你现在缺的就是这个 es） ======
  try {
    const es = new EventSource(chatStreamUrl(consultationId, text)) // chatStreamUrl 你 utils 里已有:contentReference[oaicite:1]{index=1}
    session.es = es

    es.addEventListener('system', (e) => {
      const systemData = e.data
      if (!systemData?.includes('知识图谱')) return

      const aiMsgIndex = idxOfAi()
      if (aiMsgIndex === -1) return

      const kgInfo = parseKgInfo(systemData)
      session.streamData.kgInfo = kgInfo
      session.messages[aiMsgIndex].kgInfo = kgInfo

      // 插入系统消息（如果你不想展示系统消息，就别插入）
      session.messages.splice(aiMsgIndex, 0, {
        id: Date.now() + Math.random(),
        role: 'system',
        content: systemData,
        relatedAiMsgId: aiMsgId,
        timestamp: Date.now()
      })

      scheduleSave()
    })

    es.addEventListener('think', (e) => {
      pendingThought += (e.data ?? '')
      scheduleFlush()
    })

    es.addEventListener('answer', (e) => {
      pendingAnswer += (e.data ?? '')
      scheduleFlush()
    })

    es.addEventListener('done', async () => {
      forceFlush()

      closeStream(session)
      session.sending = false

      const idx = idxOfAi()
      if (idx !== -1) {
        session.messages[idx].isStreaming = false
        if (session.messages[idx].kgInfo?.processing) {
          session.messages[idx].kgInfo = {
            hit: false, detail: '',
            text: '知识图谱：未命中',
            type: 'no', expandable: false, processing: false
          }
        }
      }

      clearStreamingState(consultationId)
      session.currentAiMsgId = null
      session.streamData = { thought: '', answer: '', kgInfo: null, startTime: 0, consultationId }

      if (props.currentId === consultationId) {
        setTimeout(async () => {
          await loadMessages(consultationId)
          await scrollToBottom()
        }, 300)
      }
    })

    es.onerror = (error) => {
      console.error('EventSource 错误:', error)

      forceFlush()
      closeStream(session)
      session.sending = false

      const idx = idxOfAi()
      if (idx !== -1) {
        session.messages[idx].content = (session.messages[idx].content || '') + '\n\n（连接中断或服务端异常，请重试）'
        session.messages[idx].isStreaming = false
        if (session.messages[idx].kgInfo?.processing) {
          session.messages[idx].kgInfo = {
            hit: false, detail: '',
            text: '知识图谱：未命中',
            type: 'no', expandable: false, processing: false
          }
        }
      }

      clearStreamingState(consultationId)
      session.currentAiMsgId = null
      session.streamData = { thought: '', answer: '', kgInfo: null, startTime: 0, consultationId }
    }
  } catch (err) {
    console.error('建立流式连接失败:', err)
    closeStream(session)
    session.sending = false
  }
}




//修改 clearAllState 函数
function clearAllState() {
  // 1) 停掉所有会话的 SSE（否则后台还在跑，会导致 globalBusy 一直为 true）
  Object.values(sessions).forEach((s) => {
    closeStream(s)
    s.sending = false
    s.currentAiMsgId = null
    s.streamData = {
      thought: '',
      answer: '',
      kgInfo: null,
      startTime: 0,
      consultationId: s.id
    }
    s.messages = []
    s.loaded = false
    if (s.id) clearStreamingState(s.id)
  })

  // 2) 彻底清空 sessions（释放内存 + 保证 globalBusy 立刻变 false）
  Object.keys(sessions).forEach(k => delete sessions[k])

  // 3) 重置 MessageList 组件的知识图谱展开状态
  if (messageListRef.value && typeof messageListRef.value.resetKgDetails === 'function') {
    messageListRef.value.resetKgDetails()
  }
}


// 修改新建问诊处理函数
async function handleNewConsultation() {
  // 1. 先清空所有本地状态
  clearAllState()

  // 2. 触发父组件的新建问诊事件
  emit('new-consultation')
}

//  解析知识图谱信息
function parseKgInfo(systemData) {
  const s = String(systemData || '');
  console.log('解析知识图谱消息:', s);

  // 检查是否是知识图谱消息
  if (!s.includes('知识图谱')) {
    return {
      hit: false,
      detail: '',
      text: '知识图谱：未命中',
      type: 'no',
      expandable: false,
      processing: false
    };
  }

  // 提取详情部分
  let detail = '';

  // 尝试不同的匹配模式
  if (s.includes('【知识图谱匹配】')) {
    // 模式1: 【知识图谱匹配】开头的内容
    const match = s.match(/【知识图谱匹配】([\s\S]*)/);
    if (match && match[1]) {
      detail = match[1].trim();
    }
  } else if (s.includes('知识图谱分析：')) {
    // 模式2: "知识图谱分析："开头的内容
    const index = s.indexOf('知识图谱分析：');
    detail = s.substring(index + '知识图谱分析：'.length).trim();

    // 如果还有后续内容，只取到下一个段落前
    if (detail.includes('\n\n')) {
      detail = detail.split('\n\n')[0];
    }
  }

  // 判断是否命中
  const noHit = /未命中|未匹配|内容不足|无相关|无匹配|无法匹配|暂无匹配/i.test(detail || s);

  return {
    hit: !noHit,
    detail: noHit ? '' : detail,
    text: noHit ? '知识图谱：未命中' : '知识图谱：已命中',
    type: noHit ? 'no' : 'ok',
    expandable: !noHit && detail && detail.trim().length > 0,
    processing: false
  };
}

//添加组件激活时的状态检查
onActivated(() => {
  if (props.currentId) {
    setTimeout(() => scrollToBottom(), 100)
  }
})


// 滚动到最底部
async function scrollToBottom() {
  await nextTick()
  const el = document.querySelector('.msg-list')
  if (el) el.scrollTop = el.scrollHeight
}

function extractSymptomsFromKgDetail(detail = '') {
  const s = String(detail || '').trim()
  if (!s) return []

  // 常见：命中症状：xxx、yyy、zzz
  const m =
      s.match(/(?:命中)?症状(?:列表)?\s*[:：]\s*([^\n\r]+)/) ||
      s.match(/symptoms?\s*[:：]\s*([^\n\r]+)/i)

  if (!m?.[1]) return []

  return Array.from(
      new Set(
          m[1]
              .split(/[，,、;；\s]+/)
              .map(x => x.trim())
              .filter(Boolean)
      )
  )
}

function isKgSystemMessage(content) {
  const s = String(content || '')
  return (
      s.includes('知识图谱') ||
      s.includes('知识库') ||
      s.includes('图分析') ||
      s.includes('【知识图谱匹配】') ||
      s.includes('知识图谱分析：')
  )
}

// 从知识图谱文本里“尽量稳”提取症状列表（匹配不到就返回 []）
function extractSymptomsFromKgText(text = '') {
  const s = String(text || '').trim()
  if (!s) return []

  // 1) 常见：匹配症状/命中症状/症状节点：xxx、yyy
  const lineMatchers = [
    /(?:命中|匹配|抽取|识别)?(?:到|出)?(?:的)?(?:症状|主诉)(?:节点|实体|列表)?\s*[:：]\s*([^\n\r]+)/i,
    /症状(?:节点|实体|列表)?\s*[:：]\s*([^\n\r]+)/i,
    /symptoms?\s*[:：]\s*([^\n\r]+)/i
  ]

  for (const re of lineMatchers) {
    const m = s.match(re)
    if (m?.[1]) {
      const arr = m[1]
          .split(/[，,、;；\s]+/)
          .map(x => x.trim())
          .filter(Boolean)
      return Array.from(new Set(arr))
    }
  }

  // 2) 兜底：如果它是“【症状】... - xxx - yyy ...”这种段落
  const sec = s.match(/【(?:症状|主诉)[^】]*】([\s\S]*?)(?=【|$)/)
  if (sec?.[1]) {
    const lines = sec[1].split(/\r?\n/).map(x => x.trim()).filter(Boolean)
    const items = []
    for (const ln of lines) {
      const t = ln.replace(/^[\-•\d.、]+/, '').trim()
      if (t) items.push(t)
    }
    const arr = items.join('、').split(/[，,、;；\s]+/).map(x => x.trim()).filter(Boolean)
    return Array.from(new Set(arr))
  }

  return []
}

//主诉=知识图谱命中症状；未命中=>空
function pickChiefComplaintFromKg() {
  const list = Array.isArray(messages.value) ? messages.value : []

  // 找最近一条 system 知识图谱消息
  for (let i = list.length - 1; i >= 0; i--) {
    const m = list[i]
    if (String(m?.role || '').toLowerCase() !== 'system') continue
    if (!isKgSystemMessage(m?.content)) continue

    const kg = parseKgInfo(m.content) // 你 ChatArea.vue 原本就有这个函数 :contentReference[oaicite:1]{index=1}
    if (!kg?.hit) return '' // 未命中 => 空

    // 用 detail 优先（通常是【知识图谱匹配】后面的正文），不行再用 raw
    const symptoms = extractSymptomsFromKgText(kg.detail || m.content)
    if (symptoms.length) return symptoms.join('、')

    // 命中了但解析不出，打日志方便你对正则
    console.warn('KG命中但未解析到症状，内容片段：', String(kg.detail || m.content).slice(0, 200))
    return ''
  }

  return ''
}


function fillChiefComplaintInDoc(doc, chiefText) {
  const KEYWORDS = [
    '四诊信息', '为进一步完善辨证', '建议明确以下问题',
    '这些症状持续多久', '舌象', '脉象', '建议写：'
  ]

  const candidates = [
    ...Array.from(doc.querySelectorAll('textarea')),
    ...Array.from(doc.querySelectorAll('[contenteditable="true"]'))
  ]

  // 1) 优先找“当前内容就是那段固定四诊文案”的输入框
  for (const el of candidates) {
    const cur = String(el.value ?? el.textContent ?? '')
    if (KEYWORDS.some(k => cur.includes(k))) {
      if (el.tagName === 'TEXTAREA') {
        el.value = chiefText || ''
        el.textContent = chiefText || ''
      } else {
        el.textContent = chiefText || ''
      }
      return true
    }
  }

  // 2) 再按 id/name/placeholder 含 “主诉/症状” 来找
  for (const el of candidates) {
    const key = `${el.id || ''} ${el.name || ''} ${el.className || ''} ${el.placeholder || ''}`
    if (/主诉|症状/i.test(key)) {
      if (el.tagName === 'TEXTAREA') {
        el.value = chiefText || ''
        el.textContent = chiefText || ''
      } else {
        el.textContent = chiefText || ''
      }
      return true
    }
  }

  return false
}

// 把 report.html 里的 /xxx 资源、/api/... 链接都改成后端 origin 的绝对地址，避免 about:blank 下资源丢失
function absolutizeRootRelativeUrls(doc, origin) {
  const attrs = ['href', 'src', 'action']
  const nodes = doc.querySelectorAll('[href], [src], form[action]')
  nodes.forEach((node) => {
    attrs.forEach((a) => {
      const v = node.getAttribute?.(a)
      if (!v) return
      if (v.startsWith('//') || v.startsWith('http') || v.startsWith('data:') || v.startsWith('mailto:') || v.startsWith('#')) return
      if (v.startsWith('/')) node.setAttribute(a, origin + v)
    })
  })
}

// 预览页（可编辑）打开：主诉自动=知识图谱命中症状；未命中则清空
async function printReport() {
  if (!props.currentId) return

  // 先同步开窗，避免被弹窗拦截
  const win = window.open('', '_blank')
  if (!win) return

  const reportUrl = consultationReportUrl(props.currentId)
  const apiOrigin = new URL(reportUrl, window.location.href).origin
  const chiefText = pickChiefComplaintFromKg() //命中=症状串；未命中=''

  try {
    const html = await getConsultationReportHtml(props.currentId)

    const doc = new DOMParser().parseFromString(html, 'text/html')

    // 1) 灌入主诉（找那块默认四诊文案的 textarea 并覆盖）
    fillChiefComplaintInDoc(doc, chiefText)

    // 2) 修正资源/下载链接为后端绝对地址（避免 about:blank 下按钮失效）
    absolutizeRootRelativeUrls(doc, apiOrigin)

    win.document.open()
    win.document.write('<!doctype html>\n' + doc.documentElement.outerHTML)
    win.document.close()
  } catch (e) {
    // 拉不到就退回原始页面
    win.location.href = reportUrl
  }
}

</script>

<style scoped>
@import '../styles/chat-area.css';
</style>