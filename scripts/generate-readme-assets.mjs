/**
 * 生成 README 首页演示图（纯 SVG，GitHub 可直接渲染）。
 * 运行：node scripts/generate-readme-assets.mjs
 * 输出：assets/*.svg
 *
 * 说明：这些是依据真实页面配色（#587766 主色）绘制的界面示意图，
 * 上线前可用真实截图替换同名文件，README 无需改动。
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'assets')
mkdirSync(OUT, { recursive: true })

const C = {
  primary: '#587766',
  dark: '#1f2f3a',
  darker: '#16212b',
  text: '#2c3b34',
  muted: '#8a9891',
  line: '#e2e8e4',
  bg: '#eef2ef',
  card: '#ffffff',
  green: '#2f9b5f',
  green2: '#2f7a52',
  rust: '#b4553f',
  gold: '#8d6b27',
  teal: '#3f7f8f'
}
const FONT =
  "-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Hiragino Sans GB','Microsoft YaHei',sans-serif"

const esc = (s) =>
  String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
const T = (x, y, s, o = {}) =>
  `<text x="${x}" y="${y}" font-size="${o.size ?? 14}" fill="${o.fill ?? C.text}" font-weight="${
    o.weight ?? 400
  }" text-anchor="${o.anchor ?? 'start'}" opacity="${o.opacity ?? 1}">${esc(s)}</text>`
const R = (x, y, w, h, o = {}) =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${o.rx ?? 0}" fill="${o.fill ?? '#fff'}" stroke="${
    o.stroke ?? 'none'
  }" stroke-width="${o.sw ?? 1}" opacity="${o.opacity ?? 1}"/>`
const Ci = (cx, cy, r, fill, o = {}) =>
  `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" opacity="${o.opacity ?? 1}"${
    o.stroke ? ` stroke="${o.stroke}" stroke-width="${o.sw ?? 1}"` : ''
  }/>`
const L = (x1, y1, x2, y2, stroke, o = {}) =>
  `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${
    o.sw ?? 1
  }" opacity="${o.opacity ?? 1}"${o.dash ? ` stroke-dasharray="${o.dash}"` : ''}/>`
const tag = (x, y, s, color, o = {}) => {
  const w = o.w ?? s.length * 12 + 22
  return (
    R(x, y, w, o.h ?? 22, { rx: 11, fill: color, opacity: 0.13 }) +
    T(x + w / 2, y + 15, s, { size: o.size ?? 12, fill: color, weight: 600, anchor: 'middle' })
  )
}

const MENU = [
  '工作台',
  '智能体工会',
  '订单计划',
  '出库协同',
  '收货上架',
  '拣货任务',
  '库存控制',
  '库存盘点',
  '数据分析',
  '系统管理'
]

/* ---------------- 通用窗口框架 ---------------- */
function frame({ page, active = '工作台', content = '', W = 1200, H = 760 }) {
  const M = 40
  const winW = W - M * 2
  const winH = H - M * 2
  const top = 52
  const sbW = 210
  const cx = M + sbW + 30
  const cy = M + top + 34
  const cw = winW - sbW - 60

  const menuSvg = MENU.map((m, i) => {
    const y = M + top + 26 + i * 44
    const on = m === active
    return (
      (on ? R(M + 12, y - 16, sbW - 24, 36, { rx: 9, fill: '#ffffff', opacity: 0.1 }) : '') +
      (on ? R(M + 12, y - 12, 3, 28, { rx: 2, fill: '#8fd0a8' }) : '') +
      Ci(M + 32, y + 2, 5, on ? '#8fd0a8' : '#ffffff', { opacity: on ? 1 : 0.35 }) +
      T(M + 48, y + 6, m, { size: 14, fill: '#ffffff', opacity: on ? 1 : 0.62, weight: on ? 600 : 400 })
    )
  }).join('')

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">
  <defs>
    <linearGradient id="pagebg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#dfe8e2"/><stop offset="1" stop-color="#cdd9d1"/>
    </linearGradient>
    <linearGradient id="sb" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#26362f"/><stop offset="1" stop-color="#1b2823"/>
    </linearGradient>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="10" stdDeviation="18" flood-color="#1f2f3a" flood-opacity="0.22"/>
    </filter>
    <clipPath id="win"><rect x="${M}" y="${M}" width="${winW}" height="${winH}" rx="18"/></clipPath>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#pagebg)"/>
  <rect x="${M}" y="${M}" width="${winW}" height="${winH}" rx="18" fill="#fff" filter="url(#shadow)"/>
  <g clip-path="url(#win)">
    <rect x="${M}" y="${M}" width="${winW}" height="${top}" fill="#f6f8f7"/>
    ${Ci(M + 26, M + 26, 6, '#e07b6a')}${Ci(M + 46, M + 26, 6, '#e6c15a')}${Ci(M + 66, M + 26, 6, '#7fc08a')}
    ${T(M + winW / 2, M + 31, page, { size: 13, fill: C.muted, anchor: 'middle' })}
    ${R(M, M + top, sbW, winH - top, { fill: 'url(#sb)' })}
    ${T(M + 26, M + top + 26, 'WellLog WMS', { size: 15, fill: '#ffffff', weight: 700 })}
    ${menuSvg}
    ${content(cx, cy, cw)}
  </g>
</svg>`
}

/* ---------------- 页面内容 ---------------- */
const contentWorkbench = (cx, cy, cw) => {
  const cards = [
    { k: '待出库单', v: '18', c: C.primary },
    { k: '拣货任务', v: '7', c: C.green },
    { k: '库存异常', v: '3', c: C.rust }
  ]
  const cardW = (cw - 32) / 3
  const stats = cards
    .map((c, i) => {
      const x = cx + i * (cardW + 16)
      return (
        R(x, cy + 150, cardW, 96, { rx: 12, fill: '#fff', stroke: C.line }) +
        R(x, cy + 150, 4, 96, { rx: 2, fill: c.c }) +
        T(x + 22, cy + 182, c.k, { size: 13, fill: C.muted }) +
        T(x + 22, cy + 222, c.v, { size: 30, fill: c.c, weight: 700 })
      )
    })
    .join('')
  const todos = [
    ['SO-20260712-01', '出库单待分配', '高', C.rust],
    ['PO-20260712-07', '收货上架待质检', '中', C.gold],
    ['ST-20260711-03', '库存盘点差异复核', '中', C.gold],
    ['REQ-20260712-02', '领料单部分齐套', '低', C.green]
  ]
    .map((t, i) => {
      const y = cy + 316 + i * 44
      return (
        Ci(cx + 26, y, 7, C.line) +
        T(cx + 46, y + 5, t[0], { size: 13.5, weight: 600 }) +
        T(cx + 200, y + 5, t[1], { size: 13, fill: C.muted }) +
        tag(cx + 470, y - 9, t[2], t[3], { w: 40 })
      )
    })
    .join('')
  const donut =
    Ci(cx + cw - 120, cy + 400, 58, C.line) +
    `<path d="M ${cx + cw - 120} ${cy + 342} A 58 58 0 1 1 ${cx + cw - 176} ${cy + 434}" fill="none" stroke="${
      C.green
    }" stroke-width="14" stroke-linecap="round"/>` +
    T(cx + cw - 120, cy + 398, '92%', { size: 24, weight: 700, anchor: 'middle', fill: C.green2 }) +
    T(cx + cw - 120, cy + 422, '库存健康度', { size: 12, fill: C.muted, anchor: 'middle' })

  return (
    T(cx, cy + 10, '下午好，李工 👋', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '全角色 AI 工作台 · 今日 12 项待办', { size: 13, fill: C.muted }) +
    R(cx, cy + 56, cw, 76, { rx: 12, fill: '#f5f9f6', stroke: C.line }) +
    Ci(cx + 34, cy + 94, 18, C.primary, { opacity: 0.15 }) +
    T(cx + 34, cy + 100, 'AI', { size: 13, weight: 700, anchor: 'middle', fill: C.primary }) +
    T(cx + 64, cy + 90, '用一句话描述任务，例如：把 SO-20260712 的生产计划拆成领料单', { size: 13.5, fill: C.muted }) +
    T(cx + 64, cy + 112, 'AI 会先判断意图，再决定「问答 / 待执行 / 需补充 / 无权限」', { size: 11.5, fill: C.muted, opacity: 0.8 }) +
    R(cx + cw - 108, cy + 76, 84, 36, { rx: 9, fill: C.primary }) +
    T(cx + cw - 66, cy + 99, '发送', { size: 14, fill: '#fff', weight: 600, anchor: 'middle' }) +
    stats +
    R(cx, cy + 270, 560, 240, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 22, cy + 298, '待办任务', { size: 15, weight: 700 }) +
    T(cx + 110, cy + 298, '实时同步', { size: 11.5, fill: C.green }) +
    todos +
    donut
  )
}

const contentAgentUnion = (cx, cy, cw) => {
  const agents = [
    ['总', '总控智能体', 'L0 · 决策层', C.primary],
    ['计', '订单计划智能体', 'L1 · 策略层', C.teal],
    ['库', '库存智能体', 'L1 · 策略层', C.green2],
    ['出', '出库智能体', 'L1 · 执行层', '#6f8fb0'],
    ['收', '收货智能体', 'L2 · 执行层', C.gold],
    ['质', '质检智能体', 'L2 · 执行层', C.rust],
    ['盘', '盘点智能体', 'L2 · 执行层', '#7a6fae'],
    ['移', '移库智能体', 'L2 · 执行层', '#4f8f8f']
  ]
  const cols = 4
  const cardW = (cw - (cols - 1) * 16) / cols
  const grid = agents
    .map((a, i) => {
      const x = cx + (i % cols) * (cardW + 16)
      const y = cy + 120 + Math.floor(i / cols) * 150
      return (
        R(x, y, cardW, 134, { rx: 12, fill: '#fff', stroke: C.line }) +
        Ci(x + 34, y + 38, 20, a[3], { opacity: 0.16 }) +
        T(x + 34, y + 45, a[0], { size: 17, weight: 700, anchor: 'middle', fill: a[3] }) +
        T(x + 64, y + 36, a[1], { size: 14.5, weight: 700 }) +
        T(x + 64, y + 56, a[2], { size: 11.5, fill: C.muted }) +
        L(x + 20, y + 74, x + cardW - 20, y + 74, C.line) +
        T(x + 20, y + 96, '能力', { size: 11.5, fill: C.muted }) +
        T(x + 20, y + 116, '任务规划 · 数据核对', { size: 12.5 })
      )
    })
    .join('')
  const tabs = ['全部', '决策层', '策略层', '执行层', '辅助层']
    .map((t, i) => {
      const x = cx + 300 + i * 86
      const on = i === 0
      return (
        R(x, cy + 62, 76, 30, { rx: 15, fill: on ? C.primary : '#fff', stroke: on ? 'none' : C.line }) +
        T(x + 38, cy + 82, t, { size: 12.5, fill: on ? '#fff' : C.muted, anchor: 'middle', weight: on ? 600 : 400 })
      )
    })
    .join('')
  return (
    T(cx, cy + 10, '智能体工会', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '15 位智能体 · 决策 / 策略 / 执行 / 辅助 四层协同', { size: 13, fill: C.muted }) +
    R(cx, cy + 60, 280, 34, { rx: 10, fill: '#fff', stroke: C.line }) +
    Ci(cx + 22, cy + 77, 6, C.muted, { opacity: 0.5 }) +
    T(cx + 38, cy + 82, '搜索智能体、技能或知识标签', { size: 12.5, fill: C.muted }) +
    tabs +
    grid
  )
}

const contentOutbound = (cx, cy, cw) => {
  const cols = [
    ['待分配', 3, C.gold, ['SO-20260712-01', 'SO-20260712-04', 'SO-20260712-06']],
    ['拣货中', 4, C.teal, ['SO-20260712-02', 'SO-20260712-05', 'SO-20260712-08']],
    ['待复核', 2, C.primary, ['SO-20260711-09', 'SO-20260712-03']],
    ['已发运', 5, C.green2, ['SO-20260711-02', 'SO-20260711-05', 'SO-20260711-07']]
  ]
  const w = (cw - 3 * 14) / 4
  const body = cols
    .map((col, ci) => {
      const x = cx + ci * (w + 14)
      const cards = col[3]
        .map(
          (no, i) =>
            R(x, cy + 118 + i * 92, w, 80, { rx: 10, fill: '#fff', stroke: C.line }) +
            T(x + 14, cy + 144 + i * 92, no, { size: 13, weight: 700 }) +
            T(x + 14, cy + 164 + i * 92, '成品钻具组件 × 6', { size: 11.5, fill: C.muted }) +
            R(x + 14, cy + 174 + i * 92, w - 28, 14, { rx: 7, fill: C.bg }) +
            R(x + 14, cy + 174 + i * 92, (w - 28) * (0.35 + i * 0.2), 14, { rx: 7, fill: col[2], opacity: 0.7 }) +
            tag(x + w - 58, cy + 132 + i * 92, 'FIFO', col[2], { w: 44, h: 18, size: 10.5 })
        )
        .join('')
      return (
        R(x, cy + 60, w, 470, { rx: 12, fill: '#f7faf8', stroke: C.line }) +
        Ci(x + 20, cy + 84, 6, col[2]) +
        T(x + 34, cy + 89, col[0], { size: 14, weight: 700 }) +
        tag(x + w - 46, cy + 72, String(col[1]), col[2], { w: 34, h: 20, size: 11.5 }) +
        cards
      )
    })
    .join('')
  return (
    T(cx, cy + 10, '出库协同看板', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '按可用库存自动推荐 FIFO 批次与库位，异常实时回流', { size: 13, fill: C.muted }) +
    body
  )
}

const contentPda = (cx, cy, cw) => {
  const px = cx + 120
  const pw = 300
  const rows = [
    ['库位', 'A-01-03-02'],
    ['物料', 'M-PCB-CONTROL'],
    ['批次', 'LOT-2026-0612'],
    ['数量', '6 / 6']
  ]
    .map(
      (r, i) =>
        R(px + 24, cy + 190 + i * 52, pw - 48, 42, { rx: 8, fill: '#f7faf8', stroke: C.line }) +
        T(px + 38, cy + 216 + i * 52, r[0], { size: 12, fill: C.muted }) +
        T(px + pw - 38, cy + 216 + i * 52, r[1], { size: 13, weight: 600, anchor: 'end' })
    )
    .join('')
  return (
    T(cx, cy + 10, 'PDA 扫码拣货', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '库位 → 物料 → 批次三重校验，防错拣、防漏拣', { size: 13, fill: C.muted }) +
    R(px, cy + 60, pw, 470, { rx: 26, fill: C.dark }) +
    R(px + 10, cy + 70, pw - 20, 450, { rx: 18, fill: '#fff' }) +
    T(px + pw / 2, cy + 100, '拣货任务 · SO-20260712-02', { size: 13, weight: 700, anchor: 'middle' }) +
    R(px + 24, cy + 122, pw - 48, 52, { rx: 10, fill: '#f0f5f2', stroke: C.line }) +
    T(px + pw / 2, cy + 154, '扫一扫 · 库位 / 物料条码', { size: 13, fill: C.primary, anchor: 'middle', weight: 600 }) +
    rows +
    R(px + 24, cy + 410, pw - 48, 48, { rx: 10, fill: C.primary }) +
    T(px + pw / 2, cy + 441, '确认拣货', { size: 15, fill: '#fff', weight: 700, anchor: 'middle' }) +
    T(px + pw / 2, cy + 488, '校验通过 · 已生成拣货流水', { size: 11.5, fill: C.green, anchor: 'middle' }) +
    R(cx + 470, cy + 60, cw - 470, 470, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 494, cy + 92, '任务进度', { size: 15, weight: 700 }) +
    R(cx + 494, cy + 112, cw - 518, 12, { rx: 6, fill: C.bg }) +
    R(cx + 494, cy + 112, (cw - 518) * 0.6, 12, { rx: 6, fill: C.green }) +
    T(cx + 494, cy + 150, '4 / 7 行已拣', { size: 12.5, fill: C.muted }) +
    ['A-01-03-02  M-PCB-CONTROL  ✓', 'A-01-03-04  M-SEAL-RING  ✓', 'A-02-01-01  M-HV-CONN  待拣', 'A-02-01-05  M-CABLE-HV  待拣']
      .map((s, i) => T(cx + 494, cy + 190 + i * 38, s, { size: 12.5, fill: i < 2 ? C.text : C.muted }))
      .join('')
  )
}

const contentCheck = (cx, cy, cw) => {
  const rows = [
    ['M-PCB-CONTROL', '控制板', 'A-01-03-02', '120', '118', '-2', C.rust],
    ['M-SEAL-RING', '密封圈', 'A-01-03-04', '860', '860', '0', C.green2],
    ['M-HV-CONN', '高压连接器', 'A-02-01-01', '64', '64', '0', C.green2],
    ['M-CABLE-HV', '高压线缆', 'A-02-01-05', '38', '37', '-1', C.rust],
    ['M-PROBE-SHELL', '探头外壳', 'B-01-02-03', '210', '211', '+1', C.gold]
  ]
    .map((r, i) => {
      const y = cy + 170 + i * 46
      const cx0 = cx + 20
      const cells = [
        [r[0], cx0 + 8, 150],
        [r[1], cx0 + 170, 110],
        [r[2], cx0 + 290, 120],
        [r[3], cx0 + 420, 70],
        [r[4], cx0 + 500, 70]
      ]
        .map((c) => T(c[1], y + 5, c[0], { size: 12.5, fill: C.text }))
        .join('')
      return (
        (i % 2 === 0 ? R(cx + 12, y - 20, cw - 24, 40, { rx: 6, fill: '#f7faf8' }) : '') +
        cells +
        T(cx0 + 590, y + 5, r[5], { size: 13, weight: 700, fill: r[6] })
      )
    })
    .join('')
  return (
    T(cx, cy + 10, '库存盘点', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '账面 vs 实盘自动比对，差异一键生成调整单', { size: 13, fill: C.muted }) +
    R(cx, cy + 56, cw, 74, { rx: 12, fill: '#f5f9f6', stroke: C.line }) +
    T(cx + 24, cy + 88, '盘点进度', { size: 13, fill: C.muted }) +
    R(cx + 24, cy + 100, 420, 12, { rx: 6, fill: C.bg }) +
    R(cx + 24, cy + 100, 420 * 0.68, 12, { rx: 6, fill: C.primary }) +
    T(cx + 470, cy + 108, '68%', { size: 16, weight: 700, fill: C.primary }) +
    T(cx + cw - 210, cy + 88, '差异项', { size: 13, fill: C.muted }) +
    T(cx + cw - 210, cy + 112, '3 项', { size: 18, weight: 700, fill: C.rust }) +
    T(cx + cw - 110, cy + 88, '已复盘', { size: 13, fill: C.muted }) +
    T(cx + cw - 110, cy + 112, '2 项', { size: 18, weight: 700, fill: C.green2 }) +
    R(cx, cy + 148, cw, 330, { rx: 12, fill: '#fff', stroke: C.line }) +
    ['物料编码', '名称', '库位', '账面', '实盘', '差异']
      .map((h, i) => T(cx + 28 + [0, 170, 290, 420, 500, 590][i], cy + 148 + 22, h, { size: 12, fill: C.muted, weight: 600 }))
      .join('') +
    L(cx + 12, cy + 158, cx + cw - 12, cy + 158, C.line) +
    rows
  )
}

const contentQuality = (cx, cy, cw) => {
  const panels = [
    ['标准样图', '#e6ebe8', []],
    ['待检图', '#e6ebe8', []],
    ['差异热力图', '#20262b', [[120, 150, 26], [210, 210, 20], [90, 260, 16]]]
  ]
  const pw = (cw - 2 * 16) / 3
  const body = panels
    .map((p, i) => {
      const x = cx + i * (pw + 16)
      const blobs =
        i === 2
          ? p[2]
              .map(
                (b) =>
                  Ci(x + b[0], cy + 120 + b[1], b[2], '#ff5a3c', { opacity: 0.75 }) +
                  Ci(x + b[0], cy + 120 + b[1], b[2] * 0.5, '#ffd23c', { opacity: 0.9 })
              )
              .join('')
          : R(x + 40, cy + 130, pw - 80, 150, { rx: 10, fill: i === 0 ? '#c7d2cb' : '#c7d2cb' }) +
            R(x + 60, cy + 150, pw - 120, 26, { rx: 6, fill: '#9fb0a6' }) +
            R(x + 60, cy + 190, pw - 150, 26, { rx: 6, fill: '#9fb0a6' }) +
            R(x + 60, cy + 230, pw - 100, 26, { rx: 6, fill: '#9fb0a6' })
      return (
        R(x, cy + 56, pw, 300, { rx: 12, fill: '#fff', stroke: C.line }) +
        T(x + 18, cy + 84, p[0], { size: 14, weight: 700 }) +
        tag(x + pw - 74, cy + 68, i === 2 ? 'AI' : 'IMG', i === 2 ? C.rust : C.teal, { w: 52, h: 20, size: 10.5 }) +
        R(x + 14, cy + 96, pw - 28, 240, { rx: 10, fill: p[1] }) +
        blobs
      )
    })
    .join('')
  const stats = [
    ['SSIM 相似度', '0.87', C.primary],
    ['差异区域', '3 处', C.rust],
    ['判定结果', '需复核', C.gold]
  ]
    .map((s, i) => {
      const w = (cw - 2 * 16) / 3
      const x = cx + i * (w + 16)
      return (
        R(x, cy + 374, w, 84, { rx: 12, fill: '#f5f9f6', stroke: C.line }) +
        T(x + 20, cy + 404, s[0], { size: 12.5, fill: C.muted }) +
        T(x + 20, cy + 438, s[1], { size: 22, weight: 700, fill: s[2] })
      )
    })
    .join('')
  return (
    T(cx, cy + 10, '视觉质检对比', { size: 24, weight: 700 }) +
    T(cx, cy + 36, 'OpenCV + SSIM 双指标判定，自动圈出差异并给出热力图', { size: 13, fill: C.muted }) +
    body +
    stats
  )
}

const contentAssistant = (cx, cy, cw) => {
  const chips = ['问答', '待执行', '需补充', '无权限']
    .map((t, i) => {
      const x = cx + i * 92
      const on = i === 1
      return (
        R(x, cy + 62, 82, 30, { rx: 15, fill: on ? C.primary : '#fff', stroke: on ? 'none' : C.line }) +
        T(x + 41, cy + 82, t, { size: 12.5, anchor: 'middle', fill: on ? '#fff' : C.muted, weight: on ? 600 : 400 })
      )
    })
    .join('')
  return (
    T(cx, cy + 10, 'AI 助手 · 意图分流', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '先判意图再分流：问答直接回答，动作需人工确认后才编排 Agent', { size: 13, fill: C.muted }) +
    chips +
    R(cx + cw - 430, cy + 120, 400, 62, { rx: 14, fill: C.primary }) +
    T(cx + cw - 410, cy + 150, '把 SO-20260712 拆成领料单，', { size: 13, fill: '#fff' }) +
    T(cx + cw - 410, cy + 170, '并把缺料项标出来', { size: 13, fill: '#fff' }) +
    Ci(cx + cw - 470, cy + 151, 16, C.primary, { opacity: 0.15 }) +
    T(cx + cw - 470, cy + 157, '李', { size: 12, anchor: 'middle', weight: 700, fill: C.primary }) +
    R(cx, cy + 210, 560, 230, { rx: 14, fill: '#fff', stroke: C.line }) +
    Ci(cx + 34, cy + 246, 16, C.primary, { opacity: 0.15 }) +
    T(cx + 34, cy + 252, 'AI', { size: 11, anchor: 'middle', weight: 700, fill: C.primary }) +
    T(cx + 62, cy + 251, '已识别为「待执行」动作，任务类型：ORDER_PLAN_REQUISITION', { size: 13 }) +
    T(cx + 62, cy + 280, '预计执行链：', { size: 13, fill: C.muted }) +
    T(cx + 62, cy + 306, 'OrderPlanAgent → InventoryAgent（齐套校验）', { size: 13, weight: 600 }) +
    T(cx + 62, cy + 330, '→ OrderPlanAgent（生成领料单）→ AuditAgent', { size: 13, weight: 600 }) +
    R(cx + 24, cy + 352, 290, 68, { rx: 10, fill: '#f5f9f6', stroke: C.line }) +
    T(cx + 40, cy + 380, '确认启动 Agent 流水线？', { size: 13, weight: 600 }) +
    T(cx + 40, cy + 402, '写库由领域 Agent 基于真实数据完成', { size: 11.5, fill: C.muted }) +
    R(cx + 330, cy + 376, 96, 36, { rx: 9, fill: C.primary }) +
    T(cx + 378, cy + 399, '确认执行', { size: 13, fill: '#fff', weight: 600, anchor: 'middle' }) +
    R(cx + 436, cy + 376, 96, 36, { rx: 9, fill: '#fff', stroke: C.line }) +
    T(cx + 484, cy + 399, '取消', { size: 13, fill: C.muted, anchor: 'middle' })
  )
}

const contentMonitor = (cx, cy, cw) => {
  const tasks = [
    ['TASK-20260712-01', '出库单出库', '运行中', C.primary],
    ['TASK-20260712-02', '齐套校验', '已完成', C.green2],
    ['TASK-20260712-03', '盘点差异分析', '等待人工', C.gold]
  ]
    .map((t, i) => {
      const y = cy + 128 + i * 74
      return (
        R(cx + 18, y, 320, 62, { rx: 10, fill: i === 0 ? '#f0f6f2' : '#fff', stroke: C.line }) +
        T(cx + 34, y + 26, t[0], { size: 12.5, weight: 700 }) +
        T(cx + 34, y + 46, t[1], { size: 12, fill: C.muted }) +
        tag(cx + 240, y + 20, t[2], t[3], { w: 76, h: 22, size: 11 })
      )
    })
    .join('')
  const steps = [
    ['DeepSeek 任务理解与执行链规划', '成功', C.green2],
    ['OutboundAgent 校验可用库存', '成功', C.green2],
    ['InventoryAgent FIFO 推荐批次', '成功', C.green2],
    ['OutboundAgent 生成出库单', '运行中', C.primary],
    ['AuditAgent 结论归档', '等待', C.muted]
  ]
    .map((s, i) => {
      const y = cy + 130 + i * 62
      return (
        Ci(cx + 400, y + 12, 8, s[2]) +
        (i < 4 ? L(cx + 400, y + 20, cx + 400, y + 62, C.line, { sw: 2 }) : '') +
        T(cx + 424, y + 17, s[0], { size: 13, weight: 500 }) +
        T(cx + cw - 40, y + 17, s[1], { size: 12, fill: s[2], anchor: 'end', weight: 600 })
      )
    })
    .join('')
  return (
    T(cx, cy + 10, 'Agent 任务监控', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '步骤级执行日志与 AI 分析报告，全程可回溯', { size: 13, fill: C.muted }) +
    R(cx, cy + 60, 356, 470, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 18, cy + 90, '任务列表', { size: 15, weight: 700 }) +
    tasks +
    R(cx + 372, cy + 60, cw - 372, 470, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 394, cy + 90, '执行时间线 · TASK-20260712-01', { size: 15, weight: 700 }) +
    steps
  )
}

const contentAnalytics = (cx, cy, cw) => {
  const kpis = [
    ['出库及时率', '96.4%', C.green2],
    ['库存周转天数', '12.6', C.primary],
    ['拣货准确率', '99.2%', C.teal],
    ['异常闭环率', '88.0%', C.gold]
  ]
    .map((k, i) => {
      const w = (cw - 3 * 14) / 4
      const x = cx + i * (w + 14)
      return (
        R(x, cy + 60, w, 84, { rx: 12, fill: '#fff', stroke: C.line }) +
        T(x + 16, cy + 88, k[0], { size: 12.5, fill: C.muted }) +
        T(x + 16, cy + 122, k[1], { size: 22, weight: 700, fill: k[2] })
      )
    })
    .join('')
  const bars = [0.45, 0.62, 0.5, 0.78, 0.7, 0.88, 0.82]
    .map((v, i) => {
      const x = cx + 40 + i * 62
      const h = v * 150
      return R(x, cy + 360 - h, 34, h, { rx: 6, fill: C.primary, opacity: i === 5 ? 1 : 0.45 })
    })
    .join('')
  const linePts = [0.3, 0.42, 0.36, 0.55, 0.5, 0.72, 0.68, 0.85]
    .map((v, i) => `${cx + 560 + i * 42},${cy + 360 - v * 150}`)
    .join(' ')
  return (
    T(cx, cy + 10, '数据分析', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '经营看板 · 出库 / 库存 / 质量多维度指标', { size: 13, fill: C.muted }) +
    kpis +
    R(cx, cy + 160, 500, 340, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 22, cy + 192, '近 7 日出库量', { size: 15, weight: 700 }) +
    L(cx + 22, cy + 360, cx + 478, cy + 360, C.line) +
    bars +
    R(cx + 516, cy + 160, cw - 516, 340, { rx: 12, fill: '#fff', stroke: C.line }) +
    T(cx + 538, cy + 192, '库存周转趋势', { size: 15, weight: 700 }) +
    L(cx + 538, cy + 360, cx + cw - 22, cy + 360, C.line) +
    `<polyline points="${linePts}" fill="none" stroke="${C.green2}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>` +
    linePts
      .split(' ')
      .map((p) => {
        const [x, y] = p.split(',')
        return Ci(+x, +y, 4, '#fff', { stroke: C.green2, sw: 3 })
      })
      .join('')
  )
}

const contentTrace = (cx, cy, cw) => {
  const rows = [
    ['入库', 'PO-20260710-02', 'A-01-03-02', '入库上架', '2026-07-10 09:12', '王仓管'],
    ['质检', 'QC-20260710-11', 'A-01-03-02', '质量放行', '2026-07-10 10:40', '赵质检'],
    ['冻结', 'FZ-20260711-01', 'A-01-03-02', '库存冻结', '2026-07-11 14:05', '系统'],
    ['拣货', 'SO-20260712-02', 'A-02-01-01', 'PDA 拣货', '2026-07-12 08:30', '李拣货'],
    ['出库', 'SO-20260712-02', '月台-01', '发运复核', '2026-07-12 11:20', '王仓管']
  ]
    .map((r, i) => {
      const y = cy + 180 + i * 58
      const xs = [cx + 30, cx + 120, cx + 270, cx + 390, cx + 520, cx + 700]
      return (
        (i % 2 === 0 ? R(cx + 12, y - 26, cw - 24, 52, { rx: 8, fill: '#f7faf8' }) : '') +
        Ci(cx + 40, y, 7, [C.primary, C.green2, C.rust, C.teal, C.green][i]) +
        r.map((v, j) => T(xs[j], y + 5, v, { size: 12.5, fill: j === 0 ? C.text : C.text, weight: j === 0 ? 700 : 400 })).join('')
      )
    })
    .join('')
  return (
    T(cx, cy + 10, '批次追溯', { size: 24, weight: 700 }) +
    T(cx, cy + 36, '一个批次从入库到出库的全链路证据链', { size: 13, fill: C.muted }) +
    R(cx, cy + 56, cw, 74, { rx: 12, fill: '#f5f9f6', stroke: C.line }) +
    T(cx + 24, cy + 90, '批次号', { size: 12.5, fill: C.muted }) +
    T(cx + 24, cy + 114, 'LOT-2026-0612', { size: 16, weight: 700, fill: C.primary }) +
    T(cx + 300, cy + 90, '物料', { size: 12.5, fill: C.muted }) +
    T(cx + 300, cy + 114, 'M-PCB-CONTROL', { size: 16, weight: 700 }) +
    T(cx + 580, cy + 90, '当前状态', { size: 12.5, fill: C.muted }) +
    tag(cx + 580, cy + 98, '已发运', C.green2, { w: 80, h: 24 }) +
    R(cx, cy + 148, cw, 350, { rx: 12, fill: '#fff', stroke: C.line }) +
    ['环节', '单据号', '库位', '操作', '时间', '操作人']
      .map((h, i) => T(cx + 30 + [0, 90, 240, 360, 490, 670][i], cy + 180, h, { size: 12, fill: C.muted, weight: 600 }))
      .join('') +
    L(cx + 12, cy + 190, cx + cw - 12, cy + 190, C.line)
  )
}

/* ---------------- 主图 teaser ---------------- */
function teaser() {
  const W = 1200
  const H = 560
  const features = ['多智能体协同编排', 'DeepSeek 任务理解与规划', 'PDA 扫码防错拣货', 'OpenCV 视觉质检对比']
    .map((f, i) => Ci(78, 250 + i * 34, 5, '#8fd0a8') + T(94, 255 + i * 34, f, { size: 14.5, fill: '#dfe9e2' }))
    .join('')
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}" font-family="${FONT}">
  <defs>
    <linearGradient id="tbg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1b2823"/><stop offset="0.55" stop-color="#243830"/><stop offset="1" stop-color="#2f4a3d"/>
    </linearGradient>
    <linearGradient id="glow" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#8fd0a8" stop-opacity="0.35"/><stop offset="1" stop-color="#8fd0a8" stop-opacity="0"/>
    </linearGradient>
    <filter id="wsh" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="16" stdDeviation="24" flood-color="#000" flood-opacity="0.35"/>
    </filter>
    <clipPath id="mini"><rect x="560" y="120" width="580" height="380" rx="16"/></clipPath>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#tbg)"/>
  <circle cx="1050" cy="40" r="260" fill="url(#glow)"/>
  <rect x="60" y="54" width="52" height="52" rx="14" fill="#8fd0a8"/>
  <path d="M74 88 L86 68 L98 88 Z" fill="#1b2823"/>
  ${T(128, 92, 'WellLog WMS', { size: 30, weight: 800, fill: '#ffffff' })}
  ${T(128, 122, 'AI 多智能体仓储管理平台', { size: 15, fill: '#a9c4b5' })}
  ${T(60, 200, '让仓库自己会思考', { size: 40, weight: 800, fill: '#ffffff' })}
  ${T(60, 236, 'Prompt-to-Warehouse：一句话下发任务，多智能体协同完成收货、质检、出库与盘点', {
    size: 15,
    fill: '#bcd3c6'
  })}
  ${features}
  ${R(60, 400, 118, 36, { rx: 10, fill: '#8fd0a8' })}
  ${T(119, 424, '了解项目', { size: 14, weight: 700, anchor: 'middle', fill: '#16211b' })}
  ${R(190, 400, 150, 36, { rx: 10, fill: 'none', stroke: '#8fd0a8', sw: 1.5 })}
  ${T(265, 424, '查看在线演示', { size: 14, weight: 600, anchor: 'middle', fill: '#cfe6d8' })}
  <g filter="url(#wsh)">
    <rect x="560" y="120" width="580" height="380" rx="16" fill="#ffffff"/>
  </g>
  <g clip-path="url(#mini)">
    <rect x="560" y="120" width="580" height="46" fill="#f3f6f4"/>
    ${Ci(584, 143, 5, '#e07b6a')}${Ci(600, 143, 5, '#e6c15a')}${Ci(616, 143, 5, '#7fc08a')}
    <rect x="560" y="166" width="150" height="334" fill="#22322b"/>
    ${['工作台', '智能体工会', '出库协同', '库存盘点', '数据分析']
      .map((m, i) => (i === 0 ? R(572, 182 + i * 40, 126, 30, { rx: 8, fill: '#ffffff', opacity: 0.12 }) : '') +
        Ci(590, 197 + i * 40, 4, i === 0 ? '#8fd0a8' : '#ffffff', { opacity: i === 0 ? 1 : 0.4 }) +
        T(604, 202 + i * 40, m, { size: 12, fill: '#ffffff', opacity: i === 0 ? 1 : 0.6 }))
      .join('')}
    ${T(734, 208, '下午好，李工', { size: 18, weight: 700 })}
    ${T(734, 230, '今日 12 项待办 · 3 项异常', { size: 11.5, fill: C.muted })}
    ${R(730, 246, 392, 66, { rx: 10, fill: '#f5f9f6', stroke: C.line })}
    ${Ci(756, 279, 15, C.primary, { opacity: 0.15 })}
    ${T(756, 284, 'AI', { size: 11, weight: 700, anchor: 'middle', fill: C.primary })}
    ${T(780, 274, '把 SO-20260712 拆成领料单', { size: 12 })}
    ${T(780, 294, '识别意图：待执行 → 人工确认后编排', { size: 10.5, fill: C.muted })}
    ${[
      ['待出库单', '18', C.primary],
      ['拣货任务', '7', C.green],
      ['库存异常', '3', C.rust]
    ]
      .map((c, i) => R(730 + i * 134, 326, 122, 74, { rx: 10, fill: '#fff', stroke: C.line }) +
        R(730 + i * 134, 326, 4, 74, { rx: 2, fill: c[2] }) +
        T(748 + i * 134, 352, c[0], { size: 11, fill: C.muted }) +
        T(748 + i * 134, 384, c[1], { size: 22, weight: 700, fill: c[2] }))
      .join('')}
    ${R(730, 416, 392, 84, { rx: 10, fill: '#fff', stroke: C.line })}
    ${T(748, 442, '出库协同', { size: 13, weight: 700 })}
    ${[0, 1, 2, 3]
      .map((i) => R(748 + i * 92, 458, 80, 26, { rx: 6, fill: i === 0 ? C.primary : C.bg, opacity: i === 0 ? 1 : 1 }) +
        T(788 + i * 92, 475, ['待分配 3', '拣货中 4', '待复核 2', '已发运 5'][i], {
          size: 10,
          anchor: 'middle',
          fill: i === 0 ? '#fff' : C.muted
        }))
      .join('')}
  </g>
</svg>`
}

/* ---------------- 写出 ---------------- */
const files = {
  'teaser.svg': teaser(),
  'demo-workbench.svg': frame({ page: 'WellLog WMS · 工作台', active: '工作台', content: contentWorkbench }),
  'demo-agent-union.svg': frame({ page: 'WellLog WMS · 智能体工会', active: '智能体工会', content: contentAgentUnion }),
  'demo-outbound.svg': frame({ page: 'WellLog WMS · 出库协同', active: '出库协同', content: contentOutbound }),
  'demo-pda-scan.svg': frame({ page: 'WellLog WMS · PDA 扫码拣货', active: '拣货任务', content: contentPda }),
  'demo-inventory-check.svg': frame({ page: 'WellLog WMS · 库存盘点', active: '库存盘点', content: contentCheck }),
  'demo-quality-compare.svg': frame({ page: 'WellLog WMS · 视觉质检', active: '收货上架', content: contentQuality }),
  'dash-assistant.svg': frame({ page: 'WellLog WMS · AI 助手', active: '工作台', content: contentAssistant }),
  'dash-agent-monitor.svg': frame({ page: 'WellLog WMS · Agent 监控', active: '系统管理', content: contentMonitor }),
  'dash-analytics.svg': frame({ page: 'WellLog WMS · 数据分析', active: '数据分析', content: contentAnalytics }),
  'dash-trace.svg': frame({ page: 'WellLog WMS · 批次追溯', active: '库存盘点', content: contentTrace })
}

for (const [name, svg] of Object.entries(files)) {
  writeFileSync(resolve(OUT, name), svg, 'utf8')
  console.log('wrote', `assets/${name}`, `${(svg.length / 1024).toFixed(1)}KB`)
}
console.log(`\n共 ${Object.keys(files).length} 个文件 -> ${OUT}`)
