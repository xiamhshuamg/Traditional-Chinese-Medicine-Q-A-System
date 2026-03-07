<!-- src/components/PatientEditModal.vue -->
<script setup>
import { reactive, watch } from 'vue'
import { GENDER_OPTIONS } from '../utils/constants.js'

const props = defineProps({
  consultation: Object
})

const emit = defineEmits(['save', 'cancel'])

const patientForm = reactive({
  name: '',
  age: '',
  gender: ''
})

watch(
    () => props.consultation,
    (newConsultation) => {
      if (newConsultation) {
        patientForm.name = newConsultation.patientName || ''
        patientForm.age = newConsultation.patientAge || ''
        patientForm.gender = newConsultation.patientGender || ''
      }
    },
    { immediate: true }
)

const save = () => {
  emit('save', {
    name: patientForm.name,
    age: patientForm.age,
    gender: patientForm.gender
  })
}
</script>

<template>
  <div class="modal-overlay">
    <div class="modal-content">
      <h3>编辑患者信息</h3>

      <div class="form-group">
        <label>患者姓名:</label>
        <input v-model="patientForm.name" placeholder="输入患者姓名">
      </div>

      <div class="form-group">
        <label>年龄:</label>
        <input v-model="patientForm.age" type="number" placeholder="输入年龄">
      </div>

      <div class="form-group">
        <label>性别:</label>
        <select v-model="patientForm.gender">
          <option
              v-for="option in GENDER_OPTIONS"
              :key="option.value"
              :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </div>

      <div class="form-actions">
        <button class="btn-primary" @click="save">保存</button>
        <button class="btn-cancel" @click="$emit('cancel')">取消</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 你文件名叫 model.css（不是 modal.css），这里按现有文件引入 */
@import '../styles/model.css';
</style>
