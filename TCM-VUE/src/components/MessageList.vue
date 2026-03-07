<template>
  <section class="chat-body">
    <div class="msg-list" ref="msgListRef">
      <!-- 空态 -->
      <div class="welcome" v-if="!visibleMessages.length">
        <div class="welcome-title">开始一次问诊</div>
        <div class="welcome-sub">你可以直接描述主诉、现病史、舌脉等信息，也可以上传病历文件。</div>
      </div>

      <!-- 消息 -->
      <template v-for="m in visibleMessages" :key="m.id">
        <!-- 知识图谱气泡：在所有AI消息前显示 -->
        <div
            v-if="m.role === 'assistant' && getKgInfo(m).text"
            class="kg-banner"
            :class="getKgInfo(m).type === 'ok' ? 'kg-ok' : 'kg-no'"
        >
          <div class="kg-banner-row">
            <span>{{ getKgInfo(m).text }}</span>

            <!-- 修改展开按钮条件：只要可展开就显示 -->
            <button
                v-if="getKgInfo(m).expandable"
                class="kg-toggle"
                type="button"
                @click="toggleKgDetail(m.id)"
                :aria-label="kgDetails[m.id] ? '收起知识图谱分析' : '展开知识图谱分析'"
                :title="kgDetails[m.id] ? '收起' : '展开'"
            >
              {{ kgDetails[m.id] ? '▴' : '▾' }}
            </button>
          </div>

          <div
              v-if="getKgInfo(m).expandable && kgDetails[m.id] && getKgInfo(m).detail"
              class="kg-detail"
          >
            <!-- 使用pre-wrap保持格式 -->
            <div style="white-space: pre-wrap;">{{ getKgInfo(m).detail }}</div>
          </div>
        </div>

        <!-- 消息行 -->
        <div
            class="msg-row"
            :class="m.role === 'user' ? 'right' : 'left'"
        >
          <div class="bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
            <!-- 思路 -->
            <div class="thought" v-if="m.role === 'assistant' && cleanedText(m.thought)">
              <div class="thought-label">思路概述：</div>
              <div v-html="format(m.thought)"></div>
            </div>

            <div class="content" v-html="format(m.content)"></div>

            <!--流式输出中指示器 -->
            <div v-if="m.isStreaming" class="streaming-indicator">
              <span class="dot"></span>
              <span class="dot"></span>
              <span class="dot"></span>
            </div>
          </div>
        </div>
      </template>

      <!-- 正在生成中 -->
      <div class="msg-row left" v-if="sending && !hasStreamingMessage">
        <div class="bubble bubble-ai bubble-thinking">正在生成中…</div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  sending: { type: Boolean, default: false }
})

const msgListRef = ref(null)

// 检查是否有正在流式输出的消息
const hasStreamingMessage = computed(() => {
  return props.messages.some(m => m.isStreaming);
})

/** 知识图谱展开状态管理 */
const kgDetails = ref({}) // 存储每个消息的知识图谱展开状态

function toggleKgDetail(messageId) {
  kgDetails.value[messageId] = !kgDetails.value[messageId]
}

/** 检查是否为知识图谱系统消息 */
function isKgSystemMessage(content) {
  const s = String(content || '');
  return s.includes('知识图谱') || s.includes('知识库') || s.includes('图分析') || s.includes('【知识图谱匹配】');
}

/** 提取知识图谱信息 */
function extractKgInfo(content) {
  const s = String(content || '');
  console.log('MessageList提取知识图谱:', s.substring(0, 100)); // 调试用

  if (!s.includes('知识图谱')) {
    return null;
  }

  let detail = '';

  // 与ChatArea.vue中的parseKgInfo保持一致
  if (s.includes('【知识图谱匹配】')) {
    const match = s.match(/【知识图谱匹配】([\s\S]*)/);
    if (match && match[1]) {
      detail = match[1].trim();
    }
  } else if (s.includes('知识图谱分析：')) {
    const index = s.indexOf('知识图谱分析：');
    detail = s.substring(index + '知识图谱分析：'.length).trim();

    if (detail.includes('\n\n')) {
      detail = detail.split('\n\n')[0];
    }
  }

  const noHit = /未命中|未匹配|内容不足|无相关|无匹配|无法匹配|暂无匹配/i.test(detail || s);

  console.log('MessageList提取结果:', { detail, noHit }); // 调试用

  return {
    hit: !noHit,
    detail: noHit ? '' : detail,
    text: noHit ? '知识图谱：未命中' : '知识图谱：已命中',
    type: noHit ? 'no' : 'ok',
    expandable: !noHit && detail && detail.trim().length > 0
  };
}

/** 为AI消息获取知识图谱信息 */
function getKgInfo(message) {
  // 如果消息已经有kgInfo，直接返回
  if (message.kgInfo) {
    return message.kgInfo;
  }

  // 否则，查找相关的系统消息（向后兼容）
  const list = Array.isArray(props.messages) ? props.messages : [];
  const messageIndex = list.findIndex(m => m.id === message.id);

  if (messageIndex !== -1) {
    // 向前查找最近的知识图谱系统消息
    for (let i = messageIndex - 1; i >= 0; i--) {
      const prevMsg = list[i];
      const prevRole = String(prevMsg?.role || '').toLowerCase();

      if (prevRole === 'system' && isKgSystemMessage(prevMsg.content)) {
        const kgInfo = extractKgInfo(prevMsg.content);
        if (kgInfo) {
          // 缓存到消息对象中
          message.kgInfo = kgInfo;
          return kgInfo;
        }
      }
    }
  }

  // 默认：未命中知识图谱
  return {
    hit: false,
    detail: '',
    text: '知识图谱：未命中',
    type: 'no',
    expandable: false,
    processing: false
  };
}



/** 重新计算可见消息 */
const visibleMessages = computed(() => {
  const list = Array.isArray(props.messages) ? props.messages : []
  const result = []

  // 遍历消息，过滤掉系统消息（但保留用于知识图谱判断）
  for (let i = 0; i < list.length; i++) {
    const msg = { ...list[i] }
    const role = String(msg?.role || '').toLowerCase()

    // 跳过系统消息（不显示在消息列表中）
    if (role === 'system') {
      continue
    }

    // 只有有内容的消息才显示
    if (role === 'assistant') {
      const content = String(msg?.content ?? '').trim()
      const thought = String(msg?.thought ?? '').trim()
      if (content.length === 0 && thought.length === 0) {
        continue
      }
    }

    result.push(msg)
  }

  return result
})













/** 清洗：去掉 <<<...>>> 标记、markdown 星号、行首 - 、多余括号等 */
function cleanedText(raw) {
  if (!raw) return ''
  let t = String(raw)

  // 去掉 <<<...>>> 标记
  t = t.replace(/<<<[^>]+>>>/g, '')

  // **标题**：=>【标题】：
  t = t.replace(/\*\*([^*\n]+)\*\*\s*[:：]/g, '【$1】：')
  t = t.replace(/\*\*([^*\n]+)\*\*/g, '$1')
  t = t.replace(/\*([^*\n]+)\*/g, '$1')
  t = t.replace(/[＊*]+/g, '')

  // 去掉行首项目符号 "- "
  t = t.replace(/^\s*-\s+/gm, '')

  // 修复你出现过的 "【建议】）"
  t = t.replace(/【建议】\s*）/g, '【建议】')

  // 删除空括号（常见于把 * 删掉后留下的）
  t = t.replace(/（\s*）|\(\s*\)/g, '')

  // 标题后若有两行空行，压成一行
  t = t.replace(/(【[^】]+】[:：]?)\s*\n\s*\n/g, '$1\n')

  // 折叠空行
  t = t.replace(/\n{3,}/g, '\n\n').trim()

  t = t.replace(/^\s*[-•]\s+/gm, '')

  // 删除"没有正文的标题行"（例如末尾只有【建议】）
  const lines = t.split('\n')
  const out = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trimEnd()
    const isHeading = /^【[^】]+】[:：]?$/.test(line.trim())

    if (isHeading) {
      let j = i + 1
      while (j < lines.length && lines[j].trim() === '') j++
      if (j >= lines.length) continue
      if (/^【[^】]+】[:：]?$/.test(lines[j].trim())) continue
    }

    out.push(line)
  }

  return out.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

function escapeHtml(s = '') {
  return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;')
      .replace(/\n/g, '<br>')
}

function pickChiefComplaintFromMessages() {
  // 取“第一条用户消息”作为主诉/症状（你也可以改成取最后一条）
  const m = (messages.value || []).find(x => x?.role === 'user' && String(x?.content || '').trim())
  return m ? String(m.content).trim() : ''
}

// 打印报告（自动灌入【症状/主诉】）
async function printReport() {
  if (!props.currentId) return

  let html = ''
  try {
    html = await getConsultationReportHtml(props.currentId)
  } catch (e) {
    // 拉不到HTML就退回原逻辑
    window.open(consultationReportUrl(props.currentId), '_blank')
    return
  }

  const chief = pickChiefComplaintFromMessages()
  const chiefHtml = chief ? escapeHtml(chief) : '（未提供）'

  // 把模板占位符替换掉：支持多次出现
  html = html.replace(/【症状\/主诉】/g, chiefHtml)

  const win = window.open('', '_blank')
  if (!win) {
    alert('浏览器拦截了弹窗，请允许本站点弹窗后再打印')
    return
  }

  win.document.open()
  win.document.write(html)
  win.document.close()

  // 自动弹出打印框
  win.focus()
  setTimeout(() => win.print(), 200)
}

function format(text) {
  const cleaned = cleanedText(text)
  if (!cleaned) return ''
  return escapeHtml(cleaned).replace(/\n/g, '<br/>')
}

async function scrollToBottom() {
  await nextTick()
  const el = msgListRef.value
  if (!el) return
  el.scrollTop = el.scrollHeight + 999
}

let _scrollRaf = 0

function isNearBottom(el, threshold = 160) {
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

async function smartScrollToBottom(force = false) {
  await nextTick()
  const el = msgListRef.value
  if (!el) return

  if (!force && !isNearBottom(el)) return

  if (_scrollRaf) cancelAnimationFrame(_scrollRaf)
  _scrollRaf = requestAnimationFrame(() => {
    _scrollRaf = 0
    el.scrollTop = el.scrollHeight + 999
  })
}

const lastVisibleMsg = computed(() => {
  const list = visibleMessages.value
  return list[list.length - 1]
})

watch(
    () => ({
      sending: props.sending,
      count: visibleMessages.value.length,
      lastId: lastVisibleMsg.value?.id ?? null,
      // content/thought 长度变化即可捕捉流式更新（但不需要 deep）
      lastContentLen: String(lastVisibleMsg.value?.content ?? '').length,
      lastThoughtLen: String(lastVisibleMsg.value?.thought ?? '').length
    }),
    (cur, prev) => {
      const force =
          cur.count !== (prev?.count ?? -1) ||
          (cur.sending && !prev?.sending)

      smartScrollToBottom(force)
    },
    { flush: 'post' }
)

// 移除打字机效果的相关代码，因为它会干扰流式输出
// const typingText = ref('');
//
// // 监听消息变化，添加打字机效果
// watch(() => props.messages, (newMessages) => {
//   const lastAiMsg = newMessages.findLast(m => m.role === 'assistant');
//   if (lastAiMsg && lastAiMsg.content) {
//     // 实现打字机效果
//     typeWriter(lastAiMsg.content);
//   }
// }, { deep: true });
</script>

<style scoped>
@import '../styles/message-list.css';
.streaming-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  vertical-align: middle;
}

.dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: #666;
  animation: blink 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}

.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes blink {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>