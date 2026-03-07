<template>
  <div class="app-root">
    <Login v-if="!isLoggedIn" @login-success="handleLoginSuccess" />
    <MainLayout v-else :doctor="currentDoctor" @logout="handleLogout" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import Login from './components/Login.vue'
import MainLayout from './components/MainLayout.vue'


const isLoggedIn = ref(false)
const currentDoctor = ref(null)

const handleLoginSuccess = (doctor) => {
  currentDoctor.value = doctor
  isLoggedIn.value = true
}

const handleLogout = () => {
  currentDoctor.value = null
  isLoggedIn.value = false
  localStorage.removeItem('doctorInfo')
}

onMounted(() => {
  const savedDoctor = localStorage.getItem('doctorInfo')
  if (savedDoctor) {
    currentDoctor.value = JSON.parse(savedDoctor)
    isLoggedIn.value = true
  }
})
</script>

<style>
@import './styles/main.css';
</style>