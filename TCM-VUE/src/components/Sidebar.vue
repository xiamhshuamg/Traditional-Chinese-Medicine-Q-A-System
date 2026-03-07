<script setup>
import { reactive, watch, computed } from 'vue'

const isOngoing = c => String(c?.status || '').toUpperCase() === 'ONGOING'

const props = defineProps({
  doctor: { type: Object, required: true },
  consultations: { type: Array, default: () => [] },
  currentId: { type: Number, default: null }
})

defineEmits(['new-consultation', 'select-consultation', 'edit-patient', 'logout', 'collapse'])

const stats = reactive({ today: 0, total: 0 })

watch(
    () => props.consultations,
    () => {
      const list = props.consultations || []
      stats.total = list.length
      const today = new Date().toDateString()
      stats.today = list.filter(
          c => new Date(c.startTime || c.createdAt).toDateString() === today
      ).length
    },
    { immediate: true, deep: true }
)

// 分组：今天 / 昨天 / 7天内 / 更早
const normalizeDate = v => {
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? null : d
}
const dayStart = d => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
const groupTitleOf = v => {
  const d = normalizeDate(v)
  if (!d) return '更早'
  const now = new Date()
  const diff = Math.max(0, Math.round((dayStart(now) - dayStart(d)) / 86400000))
  if (diff === 0) return '今天'
  if (diff === 1) return '昨天'
  if (diff < 7) return '7天内'
  return '更早'
}

const grouped = computed(() => {
  const map = new Map()
  for (const c of props.consultations || []) {
    const t = c.startTime || c.createdAt
    const key = groupTitleOf(t)
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(c)
  }
  const order = ['今天', '昨天', '7天内', '更早']
  return order.filter(k => map.has(k)).map(k => ({ title: k, items: map.get(k) }))
})

const titleOf = c => c.patientName || '未命名'

const doctorName = computed(() => {
  const d = props.doctor || {}
  return d.name || d.doctorName || ''
})
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-top">
      <button class="icon-btn" @click="$emit('collapse')" title="收起侧边栏" aria-label="收起侧边栏">
        «
      </button>

      <div class="brand">
        <div class="brand-title">问诊记录</div>
        <div class="brand-sub">今日 {{ stats.today }} · 总计 {{ stats.total }}</div>
      </div>
    </div>

    <button class="btn-start" @click="$emit('new-consultation')">
      <span class="plus">＋</span> 开始新问诊
    </button>

    <!-- 中间滚动区：高度会自动撑满（底部给固定栏留空间） -->
    <div class="consult-scroll">
      <div class="consult-scroll-inner">
        <template v-for="g in grouped" :key="g.title">
          <div class="group-title">{{ g.title }}</div>

          <div
              v-for="c in g.items"
              :key="c.id"
              class="nav-item"
              :class="{ active: c.id === currentId }"
              @click="$emit('select-consultation', c.id)"
              :title="titleOf(c)"
          >
            <div class="nav-text">{{ titleOf(c) }}</div>

            <div class="nav-right">
  <span
      v-if="isOngoing(c)"
      class="status-dot"
      title="未结束"
      aria-label="未结束"
  ></span>

<!--              <button class="nav-more" @click.stop title="更多">…</button>-->
              <button class="nav-more" @click.stop="$emit('edit-patient', c.id)" title="更多">…</button>
            </div>
          </div>
        </template>

        <div v-if="!consultations.length" class="empty-tip">暂无问诊记录</div>
      </div>
    </div>

    <!--底部固定栏：医生名 + 退出登录 同一行 + 向上渐隐 -->
    <div class="sidebar-bottom">
      <div class="bottom-bar">
        <div class="bottom-doctor" :title="doctorName">{{ doctorName }}</div>
        <button class="btn-logout" @click="$emit('logout')">退出登录</button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
@import '../styles/sidebar.css';
</style>
