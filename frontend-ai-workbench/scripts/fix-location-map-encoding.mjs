import fs from 'fs'

const path = new URL('../src/pages/LocationMapPage.vue', import.meta.url)
let s = fs.readFileSync(path, 'utf8')

// Remove replacement characters from broken UTF-8
s = s.replace(/\uFFFD/g, '')

const replacements = [
  ["'销售出库?, '报废处理'", "'销售出库', '报废处理'"],
  ["occupied: '?, available: '?, frozen: '?, reserved: '?, disabled:", "occupied: '●', available: '○', frozen: '❄', reserved: '◆', disabled:"],
  ["return '该库位已被冻", "return '该库位已被冻结' // fixed"],
  ["showToast('请填写完整信", "showToast('请填写完整信息' // fixed"],
  ['showToast(`上架成功', 'showToast(`上架成功！'],
  ['showToast(`上架失败', 'showToast(`上架失败：'],
  ['showToast(`下架成功', 'showToast(`下架成功：'],
  ['showToast(`下架失败', 'showToast(`下架失败：'],
  ["showToast('请输入下架数", "showToast('请输入下架数量' // fixed"],
  ["showToast('库存记录缺失，无法下", "showToast('库存记录缺失，无法下架' // fixed"],
  ["showToast('导出成功", "showToast('导出成功！' // fixed"],
  ["'所属库?, '状?, '物料编码', '物料名称', '批次?, '占用数量'", "'所属库区', '状态', '物料编码', '物料名称', '批次号', '占用数量'"],
  ['仓库存储布局总览 查看', '仓库存储布局总览 · 查看'],
  ['placeholder="按物/ 批次检索库位 class="search-input"', 'placeholder="按物料 / 批次检索库位" class="search-input"'],
  ['@click="clearFilter"></button>', '@click="clearFilter">×</button>'],
  ['请先维护仓库库</div>', '请先维护仓库库位</div>'],
  ['加载..</div>', '加载中...</div>'],
  ['}}  × {{ getZoneShelves', '}} 排 × {{ getZoneShelves'],
  ['.length }} = {{ (zoneGrids', '.length }} 列 = {{ (zoneGrids'],
  ['floor-row-label">{{ rack }}', 'floor-row-label">排 {{ rack }}'],
  ['anomalyLocs.length }} </span>', 'anomalyLocs.length }} 条</span>'],
  ['已删除的库位</p>', '已删除的库位。</p>'],
  ["loc.item || ' }", "loc.item || '—' }"],
  ['loc.stock }} </span>', 'loc.stock }} 件</span>'],
  ['库位明细 {{ locDialog', '库位明细 · {{ locDialog'],
  ['所属库</span>', '所属库区</span>'],
  ['库位状</span>', '库位状态</span>'],
  ['<th>批次</th>', '<th>批次号</th>'],
  ["|| ' }", "|| '—' }"],
  ['不可出入</div>', '不可出入库</div>'],
  ['暂无存放物</div>', '暂无存放物料</div>'],
  ['上架 {{ putawayLoc', '上架 · {{ putawayLoc'],
  ['下架 {{ pickLoc', '下架 · {{ pickLoc'],
  ['批次</span>', '批次号</span>'],
  ['placeholder="输入批次 />', 'placeholder="输入批次号" />'],
  ['placeholder="请输入数 min="1"', 'placeholder="请输入数量" min="1"'],
  ['<option value="">请选择原因 </option>', '<option value="">— 请选择原因 —</option>'],
  ['placeholder="搜索库位编码 / 物料名称 / 批次号 class="loc-search"', 'placeholder="搜索库位编码 / 物料名称 / 批次号" class="loc-search"'],
  ['<th class="lc-status">状</th>', '<th class="lc-status">状态</th>'],
  ['<th class="lc-batch">批次</th>', '<th class="lc-batch">批次号</th>'],
  ['暂无库位数据 请先在后台维护仓库库位信</td>', '暂无库位数据 · 请先在后台维护仓库库位信息</td>'],
  ['page-info">{{ totalLocItems }} 条数</span>', 'page-info">共 {{ totalLocItems }} 条数据</span>'],
  ['changePage(currentPage - 1)"></button>', 'changePage(currentPage - 1)">‹</button>'],
  ['changePage(currentPage + 1)"></button>', 'changePage(currentPage + 1)">›</button>'],
  ["content: '; font-weight: 400", "content: '件'; font-weight: 400"],
  [' {loc.locationCode} ${putawayForm', ' {loc.locationCode} → ${putawayForm'],
]

for (const [from, to] of replacements) {
  s = s.split(from).join(to)
}

// Fix broken script strings introduced by partial fixes
s = s.replace(/return '该库位已被冻结' \/\/ fixed\n\}/g, "return '该库位已被冻结'\n}")
s = s.replace(/showToast\('请填写完整信息' \/\/ fixed\)/g, "showToast('请填写完整信息')")
s = s.replace(/showToast\('请输入下架数量' \/\/ fixed\)/g, "showToast('请输入下架数量')")
s = s.replace(/showToast\('库存记录缺失，无法下架' \/\/ fixed\)/g, "showToast('库存记录缺失，无法下架')")
s = s.replace(/showToast\('导出成功！' \/\/ fixed\)/g, "showToast('导出成功！')")

fs.writeFileSync(path, s, 'utf8')
console.log('LocationMapPage.vue encoding fixed')
