<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWarehouseRoleAccess } from '../composables/useRoleAccess'
import { useWarehouse } from '../composables/useWarehouse'
import { useClientPager } from '../composables/useClientPager'

const router = useRouter()
const { allowed } = useWarehouseRoleAccess()

const {
  warehouseExceptions,
  statusClass,
  warehouseDataLoading,
  warehouseDataError,
  loadWarehouseData
} = useWarehouse()

const { currentPage, total, totalPages, pagedList, goPage, prevPage, nextPage } =
  useClientPager(warehouseExceptions, 5)

const pageNumbers = computed(() => {
  const pages = totalPages.value
  const cur = currentPage.value
  if (pages <= 5) return Array.from({ length: pages }, (_, i) => i + 1)
  if (cur <= 3) return [1, 2, 3, 4, 5]
  if (cur >= pages - 2) return [pages - 4, pages - 3, pages - 2, pages - 1, pages]
  return [cur - 2, cur - 1, cur, cur + 1, cur + 2]
})

onMounted(() => {
  if (allowed.value) loadWarehouseData()
})

function viewException(id) {
  router.push(`/warehouse-exceptions/${id}`)
}
</script>

<template>
  <section v-if="allowed" class="wh-board">
    <header class="plan-header">
      <div>
        <h1>异常记录</h1>
      </div>
    </header>

    <p v-if="warehouseDataLoading" class="data-hint">正在加载异常记录...</p>
    <p v-if="warehouseDataError" class="data-hint danger">{{ warehouseDataError }}</p>

    <div class="plan-table wh-table">
      <div class="plan-row wh-row-exception plan-row-head">
        <span>异常编号</span>
        <span>关联单据</span>
        <span>异常环节</span>
        <span>异常类型</span>
        <span>说明</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="row in pagedList" :key="row.id" class="plan-row wh-row-exception">
        <span class="plan-id"><strong>{{ row.id }}</strong></span>
        <span>{{ row.relatedDoc }}</span>
        <span>{{ row.stage }}</span>
        <span>{{ row.type }}</span>
        <span>{{ row.description }}</span>
        <span><i :class="['plan-status', statusClass(row.status)]">{{ row.status }}</i></span>
        <span class="plan-ops">
          <button type="button" @click="viewException(row.id)">查看</button>
        </span>
      </div>
      <div v-if="!total" class="wh-empty">暂无异常记录</div>
    </div>

    <div v-if="total" class="pc-pagination">
      <span class="pc-total">共 {{ total }} 条 · 每页 5 条</span>
      <div class="pc-pages">
        <button type="button" :disabled="currentPage <= 1" @click="prevPage">‹</button>
        <button
          v-for="n in pageNumbers"
          :key="n"
          type="button"
          :class="{ active: n === currentPage }"
          @click="goPage(n)"
        >{{ n }}</button>
        <button type="button" :disabled="currentPage >= totalPages" @click="nextPage">›</button>
      </div>
    </div>
  </section>
  <section v-else class="wh-board">
    <p class="data-hint">当前角色无权访问异常记录模块</p>
  </section>
</template>
