// // src/api.js
//
// async function request(url, options = {}) {
//   const headers = { ...(options.headers || {}) }
//
//   if (options.json) {
//     headers['Content-Type'] = 'application/json'
//   }
//
//   const resp = await fetch(url, {
//     method: options.method || 'GET',
//     headers,
//     body: options.body || null,
//   })
//
//   if (!resp.ok) {
//     const msg = await resp.text().catch(() => '')
//     throw new Error(msg || `请求失败: ${resp.status}`)
//   }
//
//   const ct = resp.headers.get('content-type') || ''
//   if (ct.includes('application/json')) {
//     return resp.json()
//   }
//   return resp.text()
// }
//
// /** 医生登录：POST /api/consultations/doctors/login */
// export async function doctorLogin(doctorId, doctorName) {
//   return request('/api/consultations/doctors/login', {
//     method: 'POST',
//     json: true,
//     body: JSON.stringify({
//       doctorId: parseInt(doctorId),
//       name: doctorName
//     })
//   })
// }
//
// /** 开始新问诊：POST /api/consultations/start?doctorId=&patientId= */
// export async function startConsultation(doctorId, patientId = '') {
//   const qs = new URLSearchParams()
//   qs.set('doctorId', doctorId)
//   if (patientId) qs.set('patientId', patientId)
//
//   return request(`/api/consultations/start?${qs.toString()}`, {
//     method: 'POST',
//   })
// }
//
// /** 按医生查询问诊列表：GET /api/consultations/byDoctor?doctorId= */
// export async function listConsultationsByDoctor(doctorId) {
//   return request(`/api/consultations/byDoctor?doctorId=${doctorId}`)
// }
//
// /** 获取某次问诊消息：GET /api/consultations/{id}/messages */
// export async function getMessages(consultationId) {
//   return request(`/api/consultations/${consultationId}/messages`)
// }
//
// /** 结束问诊：POST /api/consultations/{id}/finish */
// export async function finishConsultation(consultationId, payload = {}) {
//   return request(`/api/consultations/${consultationId}/finish`, {
//     method: 'POST',
//     json: true,
//     body: JSON.stringify(payload),
//   })
// }
//
// /** 发送消息并获取 AI 回复：GET /ai/chat/plain?consultationId=&prompt= */
// export async function sendChat(consultationId, prompt) {
//   const qs = new URLSearchParams()
//   qs.set('consultationId', consultationId)
//   qs.set('prompt', prompt)
//   return request(`/ai/chat/plain?${qs.toString()}`)
// }
//
// /** 更新问诊的患者信息：PUT /api/consultations/{id}/patient */
// export async function updateConsultationPatientInfo(consultationId, patientInfo) {
//   return request(`/api/consultations/${consultationId}/patient`, {
//     method: 'PUT',
//     json: true,
//     body: JSON.stringify(patientInfo)
//   })
// }
//
// //获得医生统计数信息
// export async function getDcotorStats(doctorId) {
//   return request(`/api/doctors/${doctorId}/stats`)
// }
//
// /** 获取医生统计信息：GET /api/doctors/{id}/stats */
// export async function getDoctorStats(doctorId) {
//   try {
//     return await request(`/api/doctors/${doctorId}/stats`)
//   } catch (error) {
//     console.warn('统计接口不存在，返回默认值')
//     return {
//       todayConsultCount: 0,
//       totalConsultCount: 0
//     }
//   }
// }