<script setup>
import { ref, computed, watch } from 'vue'
import { uploadFile } from '../utils/api.js'
import {
  FEATURE_FLAGS,
  SUPPORTED_FILE_TYPES,
  MAX_FILE_SIZE,
  TIPS,
  ERROR_MESSAGES
} from '../utils/constants.js'

const props = defineProps({
  currentId: Number,
  sending: Boolean
})
const emit = defineEmits(['send-message', 'file-uploaded'])

const input = ref('')
const fileInput = ref(null)
const uploadedFileName = ref('')
const uploadReady = ref(false)
const uploading = ref(false)

const canSend = computed(() => {
  if (!props.currentId || props.sending || uploading.value) return false
  return !!input.value.trim() || uploadReady.value
})

const triggerFileUpload = () => fileInput.value?.click()

const clearUpload = () => {
  uploadedFileName.value = ''
  uploadReady.value = false
}

const handleFileUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file || !props.currentId) return

  if (!SUPPORTED_FILE_TYPES.includes(file.type)) {
    alert(ERROR_MESSAGES.FILE_TYPE_NOT_SUPPORTED)
    event.target.value = ''
    return
  }
  if (file.size > MAX_FILE_SIZE) {
    alert(ERROR_MESSAGES.FILE_SIZE_EXCEEDED)
    event.target.value = ''
    return
  }

  uploadedFileName.value = file.name
  uploading.value = true

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('consultationId', String(props.currentId))

    const res = await uploadFile(formData)
    uploadReady.value = true
    emit('file-uploaded', res)
  } catch (e) {
    alert(ERROR_MESSAGES.FILE_UPLOAD_FAILED)
    clearUpload()
  } finally {
    uploading.value = false
    event.target.value = ''
  }
}

const send = () => {
  if (!canSend.value) return
  const typed = input.value.trim()
  const silent = uploadReady.value && !typed

  emit('send-message', { content: typed, silent })

  input.value = ''
  uploadReady.value = false
  uploadedFileName.value = ''
}

const handleEnter = (e) => {
  if (e.isComposing) return
  if (e.shiftKey) return
  e.preventDefault()
  send()
}

watch(() => props.currentId, () => {
  input.value = ''
  clearUpload()
})

const sendAria = computed(() => {
  if (props.sending) return '思考中'
  if (uploadReady.value && !input.value.trim()) return '生成建议'
  return '发送'
})
</script>

<template>
  <footer class="chat-input-area">
    <form class="composer" :class="{ 'is-disabled': !currentId }" @submit.prevent="send">
      <input
          v-if="currentId && FEATURE_FLAGS.ENABLE_FILE_UPLOAD"
          type="file"
          ref="fileInput"
          :accept="SUPPORTED_FILE_TYPES.join(',')"
          @change="handleFileUpload"
          class="file-hidden"
      />

      <div class="file-mini" v-if="uploadedFileName">
        <span class="file-mini-name" :title="uploadedFileName">{{ uploadedFileName }}</span>
        <span class="file-mini-status" v-if="uploading">上传中…</span>
        <button class="file-mini-x" type="button" @click="clearUpload" aria-label="移除文件">×</button>
      </div>

      <div class="main-row">
        <textarea
            v-model="input"
            class="chat-input"
            :placeholder="TIPS.CHAT_INPUT"
            :disabled="!currentId || sending || uploading"
            @keydown.enter="handleEnter"
        ></textarea>

        <!--按钮嵌入同一个框（absolute 放在右下角） -->
        <div class="actions">
          <button
              v-if="currentId && FEATURE_FLAGS.ENABLE_FILE_UPLOAD"
              class="file-btn"
              type="button"
              @click="triggerFileUpload"
              :disabled="sending || uploading"
              aria-label="上传病历"
              title="上传病历"
          >
            <svg viewBox="0 0 24 24" class="icon" aria-hidden="true">
              <path
                  d="M8.5 12.5l6.9-6.9a3 3 0 114.2 4.2l-8.5 8.5a5 5 0 11-7.1-7.1l8.7-8.7"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
              />
            </svg>
          </button>

          <button
              class="send-btn"
              type="submit"
              :disabled="!canSend"
              :aria-label="sendAria"
              :title="sendAria"
          >
            <svg viewBox="0 0 24 24" class="send-icon" aria-hidden="true">
              <path
                  d="M12 5l-6 6M12 5l6 6M12 5v14"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2.2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>
    </form>
  </footer>
</template>

<style scoped>
@import '../styles/input-area.css';
</style>
