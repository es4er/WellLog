<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [Number, String], default: '' },
  recommend: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])

const options = computed(() => props.recommend?.options ?? [])

function formatOption(opt) {
  const label = opt.locationName ? `${opt.locationCode} · ${opt.locationName}` : opt.locationCode
  const status = opt.empty ? '空闲' : `占用 ${opt.onhandQty ?? 0}`
  return `${label}（${status}）`
}
</script>

<template>
  <div :class="['wh-prep-notify wh-prep-location', { compact }]">
    <label class="wh-prep-notify__label">备料库位</label>
    <div class="wh-prep-notify__control">
      <select
        :value="modelValue"
        class="wh-worker-select"
        :disabled="disabled || loading || !options.length"
        @change="$emit('update:modelValue', Number($event.target.value) || '')"
      >
        <option value="" disabled>
          {{ loading ? '正在推荐库位...' : options.length ? '选择备料库位' : '暂无备料库位（请先开始拣货）' }}
        </option>
        <option v-for="opt in options" :key="opt.locationId" :value="opt.locationId">
          {{ formatOption(opt) }}
        </option>
      </select>
      <p v-if="recommend?.reason" class="wh-prep-notify__hint">{{ recommend.reason }}</p>
      <p v-else class="wh-prep-notify__hint">整单物料将移入所选备料格，工人领取后扣账</p>
    </div>
  </div>
</template>
