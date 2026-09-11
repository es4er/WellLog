<script setup>
defineProps({
  modelValue: { type: [Number, String], default: '' },
  workers: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div :class="['wh-prep-notify', { compact }]">
    <label class="wh-prep-notify__label">备料区通知</label>
    <div class="wh-prep-notify__control">
      <select
        :value="modelValue"
        class="wh-worker-select"
        :disabled="disabled || loading || !workers.length"
        @change="$emit('update:modelValue', Number($event.target.value) || '')"
      >
        <option value="" disabled>
          {{ loading ? '正在加载工人...' : workers.length ? '选择生产工人' : '暂无可用工人' }}
        </option>
        <option v-for="w in workers" :key="w.id" :value="w.id">
          {{ w.name }}{{ w.deptName ? ` · ${w.deptName}` : '' }}
        </option>
      </select>
      <p class="wh-prep-notify__hint">完成拣货后将通知所选工人到备料区领取</p>
    </div>
  </div>
</template>
