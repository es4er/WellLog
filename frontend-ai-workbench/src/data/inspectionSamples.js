/**
 * 外观检测 · 标准样图物料映射（前端静态模拟数据）
 *
 * 每个编号代表一种测井装备物料，对应三张预置图片：
 *  - standard：标准样图（模板）
 *  - heatmap ：OpenCV / SSIM 差异检测热力图
 *  - result  ：红框标注的缺陷定位结果图
 *
 * 图片已复制到 public/inspection-samples 下，可直接通过绝对路径访问。
 */
const SAMPLE_BASE = '/inspection-samples'

export const INSPECTION_SAMPLES = [
  { id: 1, name: '法兰连接盘', spec: '测井设备连接法兰' },
  { id: 2, name: '探头外壳', spec: '测井仪器探头壳体' },
  { id: 3, name: '压力筒', spec: '测井工具压力筒体' },
  { id: 4, name: '连接接头', spec: '测井管柱连接接头' },
  { id: 5, name: '螺纹接头', spec: '测井传感器螺纹接头' },
  { id: 6, name: '中心定位器', spec: '测井工具中心定位器' },
  { id: 7, name: '稳定器叶片', spec: '测井工具稳定器叶片' },
  { id: 8, name: '卡瓦座', spec: '测井工具卡瓦座' },
  { id: 9, name: '上接头', spec: '测井工具上接头' },
  { id: 10, name: '防喷接头', spec: '测井防喷装置接头' }
].map((item) => ({
  ...item,
  standard: `${SAMPLE_BASE}/standards/${item.id}.png`,
  heatmap: `${SAMPLE_BASE}/heatmap/${item.id}.png`,
  result: `${SAMPLE_BASE}/results/${item.id}.png`
}))

export function findInspectionSample(id) {
  return INSPECTION_SAMPLES.find((item) => item.id === Number(id)) || null
}
