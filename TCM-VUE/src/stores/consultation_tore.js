import { reactive } from 'vue'

export const consultationStore = reactive({
    consultations: [],
    currentId: null,

    setConsultations(consultations) {
        this.consultations = consultations
    },

    setCurrentId(id) {
        this.currentId = id
    },

    addConsultation(consultation) {
        this.consultations.unshift(consultation)
    },

    updateConsultation(id, updates) {
        const consultation = this.consultations.find(c => c.id === id)
        if (consultation) {
            Object.assign(consultation, updates)
        }
    }
})