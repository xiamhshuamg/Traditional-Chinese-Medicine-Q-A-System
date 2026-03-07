<template>
  <div class="main-container">
    <Sidebar
        v-if="sidebarOpen"
        :doctor="doctor"
        :consultations="consultations"
        :currentId="currentId"
        @new-consultation="onNewConsultation"
        @select-consultation="selectConsultation"
        @logout="$emit('logout')"
        @collapse="sidebarOpen = false"
        @edit-patient="editPatientInfo"

    />

    <!-- 收起态：用“左侧窄栏”承载按钮，避免遮挡内容 -->
    <div v-else class="sidebar-gutter">
      <button
          class="icon-btn"
          @click="sidebarOpen = true"
          title="展开侧边栏"
          aria-label="展开侧边栏"
      >☰</button>
      <button
          class="icon-btn"
          @click="onNewConsultation"
          title="新建问诊"
          aria-label="新建问诊"
      >＋</button>
    </div>

    <ChatArea
        :currentId="currentId"
        :consultations="consultations"
        @finish-consultation="onFinishConsultation"
        @new-consultation="onNewConsultation"
        @update-patient="editPatientInfo"
    />

    <PatientEditModal
        v-if="showPatientEdit"
        :consultation="currentConsultation"
        @save="savePatientInfo"
        @cancel="showPatientEdit = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Sidebar from './Sidebar.vue'
import ChatArea from './ChatArea.vue'
import PatientEditModal from './PatientEditModal.vue'
import {
  listConsultationsByDoctor,
  startConsultation,
  finishConsultation,
  updateConsultationPatientInfo
} from '../utils/api.js'

const props = defineProps({
  doctor: { type: Object, required: true }
})
defineEmits(['logout'])

const sidebarOpen = ref(true)

const consultations = ref([])
const currentId = ref(null)
const showPatientEdit = ref(false)

const currentConsultation = computed(() => {
  if (!currentId.value) return null
  const list = Array.isArray(consultations.value) ? consultations.value : []
  return list.find(x => x.id === currentId.value) || null
})

onMounted(() => {
  window.scrollTo({ top: 0, left: 0 })
})

onMounted(async () => {
  await loadConsultations()
})

async function loadConsultations() {
  const doctorId = props?.doctor?.id
  if (!doctorId) return

  try {
    const list = await listConsultationsByDoctor(doctorId)
    const arr = Array.isArray(list) ? list : []
    consultations.value = arr

    if (arr.length && !currentId.value) {
      currentId.value = arr[0].id
    }
  } catch (error) {
    console.error('加载问诊记录失败:', error)
    consultations.value = []
  }
}

async function onNewConsultation() {
  const doctorId = props?.doctor?.id
  if (!doctorId) return

  try {
    const c = await startConsultation(doctorId, '')
    consultations.value.unshift(c)
    currentId.value = c.id
  } catch (error) {
    console.error('创建咨询失败:', error)
    alert('创建咨询失败，请重试')
  }
}

function selectConsultation(id) {
  currentId.value = id
}

async function onFinishConsultation(consultationId) {
  try {
    await finishConsultation(consultationId, { summary: '前端确认结束问诊。' })

    const consultation = consultations.value.find(x => x.id === consultationId)
    if (consultation) {
      consultation.status = 'FINISHED'
      consultation.endTime = new Date().toISOString()   //  立刻显示结束时间
      consultation.lastActiveTime = consultation.endTime
    }

    //同步后端真实 endTime（如果你后端会回填）
    await loadConsultations()
  } catch (error) {
    console.error('结束问诊失败:', error)
    alert('结束问诊失败')
  }
}


async function savePatientInfo(patientInfo) {
  if (!currentId.value) return
  try {
    await updateConsultationPatientInfo(currentId.value, patientInfo)
    const consultation = consultations.value.find(x => x.id === currentId.value)
    if (consultation) {
      consultation.patientName = patientInfo.name
      consultation.patientAge = patientInfo.age
      consultation.patientGender = patientInfo.gender
    }
    showPatientEdit.value = false
  } catch (error) {
    console.error('保存患者信息失败:', error)
    alert('保存失败，请重试')
  }
}

function editPatientInfo(id) {
  if (id) currentId.value = id
  showPatientEdit.value = true
}

</script>

<style scoped>
@import '../styles/main-layout.css';
</style>
