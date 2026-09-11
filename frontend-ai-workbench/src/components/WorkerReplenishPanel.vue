<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useWorker } from '../composables/useWorker'

const props = defineProps({
  open: { type: Boolean, default: false },
  task: { type: Object, default: null }
})

const emit = defineEmits(['close', 'submitted'])

const {
  REPLENISH_REASONS,
  submitReplenish,
  resolvePickingTaskId,
  resolvePickingLineId
} = useWorker()

const form = reactive({
  itemId: '',
  qty: 1,
  reason: REPLENISH_REASONS[0],
  note: ''
})
const submitting = ref(false)
const tip = ref('')
const submitted = ref(false)

const selectedItem = computed(
  () => props.task?.items?.find((i) => i.id === form.itemId) ?? props.task?.items?.[0] ?? null
)

watch(
  () => [props.open, props.task?.id],
  () => {
    if (!props.open || !props.task) return
    form.itemId = props.task.items?.[0]?.id || ''
    form.qty = 1
    form.reason = REPLENISH_REASONS[0]
    form.note = ''
    tip.value = ''
    submitted.value = false
  },
  { immediate: true }
)

function onItemChange() {
  form.qty = 1
}

function adjustQty(delta) {
  form.qty = Math.min(99, Math.max(1, form.qty + delta))
}

function closePanel() {
  emit('close')
}

async function handleSubmit() {
  if (!props.task || !selectedItem.value) {
    tip.value = '请选择申请物料'
    return
  }
  submitting.value = true
  tip.value = ''
  try {
    const entry = await submitReplenish({
      pickingTaskId: resolvePickingTaskId(props.task),
      pickingLineId: resolvePickingLineId(selectedItem.value),
      qty: form.qty,
      reason: form.reason,
      note: form.note,
      workOrder: props.task.workOrder,
      product: props.task.product,
      material: selectedItem.value.material,
      materialCode: selectedItem.value.materialCode,
      spec: selectedItem.value.spec || '—'
    })
    submitted.value = true
    tip.value = `补料申请已提交：${entry.material} × ${entry.qty}`
    emit('submitted', entry)
  } catch (error) {
    tip.value = error.message || '提交补料申请失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div v-if="open" class="worker-overlay" @click.self="closePanel">
    <section class="qa-card worker-replenish-modal" role="dialog" aria-labelledby="worker-replenish-title">
      <header class="worker-replenish-modal__head">
        <div>
          <h2 id="worker-replenish-title">补料申请</h2>
          <p v-if="task">
            {{ task.workOrder }} · {{ task.product }}
          </p>
        </div>
        <button type="button" class="worker-modal-close" aria-label="关闭" @click="closePanel">×</button>
      </header>

      <p v-if="tip" :class="['data-hint', submitted ? 'ok' : '']">{{ tip }}</p>

      <div v-if="task" class="worker-replenish-modal__body">
        <div class="worker-form-field">
          <label>申请物料</label>
          <select v-model="form.itemId" class="wb-select" @change="onItemChange">
            <option v-for="item in task.items || []" :key="item.id" :value="item.id">
              {{ item.materialCode }} {{ item.material }}
            </option>
          </select>
        </div>

        <div class="worker-form-field">
          <label>规格型号</label>
          <input class="wb-input" type="text" :value="selectedItem?.spec || '—'" readonly />
        </div>

        <div class="worker-form-field">
          <label>申请数量</label>
          <div class="worker-stepper">
            <button type="button" @click="adjustQty(-1)">−</button>
            <strong>{{ form.qty }}</strong>
            <button type="button" @click="adjustQty(1)">+</button>
          </div>
        </div>

        <div class="worker-form-field">
          <label>申请原因</label>
          <select v-model="form.reason" class="wb-select">
            <option v-for="reason in REPLENISH_REASONS" :key="reason" :value="reason">{{ reason }}</option>
          </select>
        </div>

        <div class="worker-form-field">
          <label>备注说明</label>
          <textarea
            v-model="form.note"
            class="wb-textarea"
            rows="3"
            maxlength="200"
            placeholder="选填，补充现场缺料情况"
          ></textarea>
          <small class="worker-char-count">{{ form.note.length }}/200</small>
        </div>
      </div>

      <footer class="worker-replenish-modal__foot">
        <button type="button" class="wb-btn-secondary" @click="closePanel">取消</button>
        <button
          v-if="!submitted"
          type="button"
          class="wb-btn-primary"
          :disabled="submitting || !task"
          @click="handleSubmit"
        >{{ submitting ? '提交中...' : '提交申请' }}</button>
        <button v-else type="button" class="wb-btn-primary" @click="closePanel">完成</button>
      </footer>
    </section>
  </div>
</template>
