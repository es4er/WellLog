import { computed, ref, unref, watch } from 'vue'

/**
 * 前端列表分页（默认每页 5 条）
 * @param {import('vue').Ref|import('vue').ComputedRef|(() => any[])} source
 * @param {number} [pageSize=5]
 */
export function useClientPager(source, pageSize = 5) {
  const currentPage = ref(1)

  const fullList = computed(() => {
    const raw = typeof source === 'function' ? source() : unref(source)
    return Array.isArray(raw) ? raw : []
  })

  const total = computed(() => fullList.value.length)
  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize) || 1))

  const pagedList = computed(() => {
    const page = Math.min(Math.max(1, currentPage.value), totalPages.value)
    const start = (page - 1) * pageSize
    return fullList.value.slice(start, start + pageSize)
  })

  watch(fullList, () => {
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
    }
  })

  function resetPage() {
    currentPage.value = 1
  }

  function goPage(n) {
    currentPage.value = Math.min(Math.max(1, Number(n) || 1), totalPages.value)
  }

  function prevPage() {
    goPage(currentPage.value - 1)
  }

  function nextPage() {
    goPage(currentPage.value + 1)
  }

  return {
    currentPage,
    pageSize,
    total,
    totalPages,
    pagedList,
    goPage,
    prevPage,
    nextPage,
    resetPage
  }
}
