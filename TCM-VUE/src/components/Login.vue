<script setup>
import { reactive } from 'vue'
import { doctorLogin } from '../utils/api.js'
import { DEFAULTS, STORAGE_KEYS } from '../utils/constants.js'

const emit = defineEmits(['login-success'])

const loginForm = reactive({
  doctorName: ''
})

const handleLogin = async () => {
  if (!loginForm.doctorName.trim()) {
    alert('请输入医生姓名')
    return
  }

  try {
    //UI 不需要输入ID：内部用默认ID（保证后续按doctorId拉取数据不崩）
    const raw = await doctorLogin(DEFAULTS.DOCTOR_ID, loginForm.doctorName.trim())

    const doctorInfo = {
      ...raw,
      id: raw?.id ?? raw?.doctorId ?? DEFAULTS.DOCTOR_ID,
      name: raw?.name ?? raw?.doctorName ?? loginForm.doctorName.trim()
    }

    localStorage.setItem(STORAGE_KEYS.DOCTOR_INFO, JSON.stringify(doctorInfo))
    emit('login-success', doctorInfo)
  } catch (error) {
    console.error('登录失败:', error)
    alert('登录失败，请检查医生姓名')
  }
}
</script>

<template>
  <div class="login-container">
    <div class="login-card">
      <h2>医生登录</h2>

      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>医生姓名:</label>
          <input v-model="loginForm.doctorName" type="text" placeholder="输入医生姓名" required>
        </div>

        <button type="submit" class="login-btn">登录系统</button>
      </form>

      <div class="demo-tips">
        <p>演示账号：</p>
        <p>医生姓名: {{ DEFAULTS.DOCTOR_NAME }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import '../styles/login.css';
</style>
