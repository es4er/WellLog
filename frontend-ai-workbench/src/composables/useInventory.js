import { ref } from 'vue'
import { apiGet, apiPost, setAuthToken } from '../api'

const loading = ref(false)

export function useInventory() {

  async function login(userCode, password) {
    const res = await apiPost('/user/login', { userCode, password })
    setAuthToken(res.token)
    return res
  }
  const doLogin = login

  async function fetchInventoryListWithDetail() {
    loading.value = true
    try {
      return await apiGet('/inventory/list-with-detail')
    } catch (e) {
      return []
    } finally {
      loading.value = false
    }
  }

  async function getTransactions(itemId, batchId) {
    return apiGet(`/inventory/transactions?itemId=${itemId}&batchId=${batchId}`)
  }

  async function getTrace(itemId, batchId) {
    return apiGet(`/inventory/trace?itemId=${itemId}&batchId=${batchId}`)
  }

  async function freezeInventory(inventoryId, qty, reason, operatedBy) {
    return apiPost('/inventory/freeze', { inventoryId, qty, reason, operatedBy })
  }

  async function unfreezeInventory(inventoryId, qty, reason, operatedBy) {
    return apiPost('/inventory/unfreeze', { inventoryId, qty, reason, operatedBy })
  }

  async function setSafetyStock(rule) {
    return apiPost('/inventory/safety-stock', rule)
  }

  async function checkSafetyStock() {
    return apiPost('/inventory/check-safety-stock')
  }

  async function getAlerts() {
    return apiGet('/inventory/alerts')
  }
  const fetchInventoryAlertList = getAlerts

  async function closeAlert(alertId) {
    return apiPost(`/inventory/alert/close?alertId=${alertId}`)
  }

  async function createStocktake(warehouseId, stocktakeType, scope, createdBy) {
    return apiPost(`/stocktake/create?warehouseId=${warehouseId}&stocktakeType=${stocktakeType || ''}&scope=${scope || ''}&createdBy=${createdBy || 1}`)
  }

  async function getStocktakeList(status) {
    return apiGet(`/stocktake/list${status ? `?status=${status}` : ''}`)
  }

  async function getStocktakeDetail(stocktakeId) {
    return apiGet(`/stocktake/detail?stocktakeId=${stocktakeId}`)
  }

  async function submitCountResult(request) {
    return apiPost('/stocktake/submit', request)
  }

  async function generateDifference(stocktakeId) {
    return apiPost(`/stocktake/difference/generate?stocktakeId=${stocktakeId}`)
  }

  async function listDifferences(stocktakeId) {
    return apiGet(`/stocktake/difference/list?stocktakeId=${stocktakeId}`)
  }
  const fetchStocktakeDifferences = listDifferences

  async function dispatchStocktake(stocktakeId, executorId) {
    return apiPost(`/stocktake/dispatch?stocktakeId=${stocktakeId}&executorId=${executorId || ''}`)
  }

  async function confirmDifference(stocktakeId) {
    return apiPost(`/stocktake/difference/confirm?stocktakeId=${stocktakeId}`)
  }
  const confirmStocktakeDifference = confirmDifference

  async function createAdjustment(stocktakeId, reason) {
    return apiPost(`/stocktake/adjustment/create?stocktakeId=${stocktakeId}&reason=${reason || ''}`)
  }

  async function approveAdjustment(adjustmentId, approvedBy) {
    return apiPost(`/stocktake/adjustment/approve?adjustmentId=${adjustmentId}&approvedBy=${approvedBy || 1}`)
  }

  async function rejectAdjustment(adjustmentId) {
    return apiPost(`/stocktake/adjustment/reject?adjustmentId=${adjustmentId}`)
  }

  async function createManualAdjustment(reason, lines, createdBy) {
    return apiPost('/stocktake/adjustment/manual', { reason, lines, createdBy })
  }

  async function getAdjustmentList(status) {
    return apiGet(`/stocktake/adjustment/list${status ? `?status=${status}` : ''}`)
  }

  async function getAdjustmentDetail(adjustmentId) {
    return apiGet(`/stocktake/adjustment/detail?adjustmentId=${adjustmentId}`)
  }

  async function fetchFreezeRecords() {
    return apiGet('/inventory/freeze-records')
  }

  // Warehouse / Location
  async function fetchWarehouseList() {
    return apiGet('/warehouse/list')
  }

  async function fetchZoneList(warehouseId) {
    return apiGet(`/warehouse/zone/list${warehouseId ? `?warehouseId=${warehouseId}` : ''}`)
  }

  async function fetchLocationList(zoneId) {
    return apiGet(`/warehouse/location/list${zoneId ? `?zoneId=${zoneId}` : ''}`)
  }

  async function fetchInventoryByLocation(locationId) {
    return apiGet(`/inventory/by-location?locationId=${locationId}`)
  }

  async function createInventory(request) {
    return apiPost('/inventory/create', request)
  }

  async function importInventory(rows) {
    return apiPost('/inventory/import', rows)
  }

  return {
    loading,
    login,
    doLogin,
    fetchInventoryListWithDetail,
    getTransactions,
    getTrace,
    freezeInventory,
    unfreezeInventory,
    setSafetyStock,
    checkSafetyStock,
    getAlerts,
    fetchInventoryAlertList,
    closeAlert,
    createStocktake,
    getStocktakeList,
    getStocktakeDetail,
    dispatchStocktake,
    submitCountResult,
    generateDifference,
    listDifferences,
    fetchStocktakeDifferences,
    confirmDifference,
    confirmStocktakeDifference,
    createAdjustment,
    approveAdjustment,
    rejectAdjustment,
    createManualAdjustment,
    getAdjustmentList,
    getAdjustmentDetail,
    fetchFreezeRecords,
    fetchWarehouseList,
    fetchZoneList,
    fetchLocationList,
    fetchInventoryByLocation,
    createInventory,
    importInventory,
  }
}
