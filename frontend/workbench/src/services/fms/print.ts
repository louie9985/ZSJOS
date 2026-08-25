/**
 * FMS 数据驱动打印工具。
 *
 * ⚠️ admin 端的 utils/print.ts 通过 DOM 克隆 Element Plus 表格（.el-table__header-wrapper 等），
 * 但 antd Table 在 scroll/fixed 模式下会把表头表体渲染成两个独立 <table>，固定列还会重复渲染 DOM，
 * 换成 .ant-table-thead 会抓到重复列。因此 workbench 侧改为**数据驱动**生成打印 HTML。
 *
 * 以下 CSS 使用了颜色字面量（#303133, #f5f7fa 等），这是正确的——它们是打印样式表，
 * 存在于 TS 模板字符串中，不在 src/styles/**\/*.css 里，不受 styles.guard.test.ts 的
 * 颜色字面量禁令约束。
 */

import dayjs from 'dayjs'
import { formatMoney, formatUppercaseMoney } from './format'

export interface FmsPrintColumn<T> {
  title: string
  align?: 'left' | 'center' | 'right'
  /** 渲染单元格内容为纯文本（已格式化） */
  render: (row: T, index: number) => string
  /** 列宽（可选，不设则 auto） */
  width?: string
  /** 行合并 */
  rowSpan?: (row: T, index: number, rows: T[]) => number
  /** 列合并 */
  colSpan?: (row: T, index: number, rows: T[]) => number
}

export interface FmsTablePrintOptions<T> {
  title: string
  companyName: string
  periodLabel: string
  centerText?: string
  footerLabels?: string[]
  columns: FmsPrintColumn<T>[]
  rows: T[]
  /** 合计行（每列一个值，空串则跳过） */
  summary?: string[]
}

const PRINT_STYLES = `
* { box-sizing: border-box; }
body { margin: 0; background: #eef0f3; color: #303133; font-family: Arial, "Microsoft YaHei", sans-serif; font-size: 14px; }
.print-page { width: calc(100% - 32px); min-height: 210mm; margin: 16px auto; padding: 12mm; background: #fff; box-shadow: 0 2px 12px rgba(0, 0, 0, .12); }
h1 { margin: 0; text-align: center; font-size: 28px; font-weight: 600; }
.print-meta, .print-footer { display: flex; justify-content: space-between; gap: 20px; padding: 12px 0; }
.print-meta span { flex: 1; }
.print-meta span:nth-child(2) { text-align: center; }
.print-meta span:last-child { text-align: right; }
table { width: 100%; border-collapse: collapse; table-layout: auto; }
th, td { min-width: 54px; padding: 7px 6px; border: 1px solid #303133; line-height: 1.5; vertical-align: middle; word-break: break-word; }
th { text-align: center; font-weight: 600; background: #f5f7fa; }
tr { page-break-inside: avoid; }
.print-footer { padding-bottom: 0; color: #606266; font-size: 12px; }
@page { size: A3 landscape; margin: 8mm; }
@media print {
  body { background: #fff; }
  .print-page { width: auto; min-height: auto; margin: 0; padding: 0; box-shadow: none; }
}
`

export function escapeHtml(value?: string | number): string {
  const str = String(value ?? '')
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 构建 FMS 表格打印 HTML（数据驱动） */
export function buildFmsTablePrintHtml<T>(options: FmsTablePrintOptions<T>): string {
  const { title, companyName, periodLabel, centerText, footerLabels = [], columns, rows, summary } = options

  // 表头
  const thead = `<thead><tr>${columns.map(col =>
    `<th style="text-align:${col.align || 'center'}${col.width ? `;width:${col.width}` : ''}">${escapeHtml(col.title)}</th>`
  ).join('')}</tr></thead>`

  // 表体
  const tbodyRows = rows.map((row, rowIndex) => {
    const cells = columns.map((col, colIndex) => {
      const rowSpan = col.rowSpan ? col.rowSpan(row, rowIndex, rows) : 1
      const colSpan = col.colSpan ? col.colSpan(row, rowIndex, rows) : 1
      if (rowSpan === 0 || colSpan === 0) return '' // 被合并的单元格
      const attrs: string[] = []
      if (rowSpan > 1) attrs.push(`rowspan="${rowSpan}"`)
      if (colSpan > 1) attrs.push(`colspan="${colSpan}"`)
      const align = col.align || (colIndex === 0 ? 'left' : 'center')
      attrs.push(`style="text-align:${align}"`)
      return `<td ${attrs.join(' ')}>${escapeHtml(col.render(row, rowIndex))}</td>`
    }).join('')
    return `<tr>${cells}</tr>`
  }).join('')
  const tbody = `<tbody>${tbodyRows}</tbody>`

  // 合计行
  let tfoot = ''
  if (summary && summary.length > 0) {
    const cells = summary.map((val, i) =>
      `<td style="text-align:${columns[i]?.align || 'center'};font-weight:600">${escapeHtml(val)}</td>`
    ).join('')
    tfoot = `<tfoot><tr>${cells}</tr></tfoot>`
  }

  const printDate = new Date().toISOString().slice(0, 10)
  const allFooterLabels = [...footerLabels, `打印日期：${printDate}`]
    .map(label => `<span>${escapeHtml(label)}</span>`)
    .join('')

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8"/>
  <title>${escapeHtml(title)}</title>
  <style>${PRINT_STYLES}</style>
</head>
<body>
  <main class="print-page">
    <h1>${escapeHtml(title)}</h1>
    <div class="print-meta">
      <span>编制单位：${escapeHtml(companyName)}</span>
      <span>${escapeHtml(centerText)}</span>
      <span>${escapeHtml(periodLabel)}</span>
    </div>
    <table>${thead}${tbody}${tfoot}</table>
    <div class="print-footer">${allFooterLabels}</div>
  </main>
</body>
</html>`
}

/** 使用隐藏 iframe 触发浏览器打印 */
export function printHtml(html: string): void {
  const iframe = document.createElement('iframe')
  iframe.style.cssText = 'position:fixed;left:-9999px;top:-9999px;width:0;height:0;border:none'
  document.body.appendChild(iframe)
  const doc = iframe.contentDocument || iframe.contentWindow?.document
  if (!doc) { document.body.removeChild(iframe); return }
  doc.open()
  doc.write(html)
  doc.close()
  // 等待内容加载后打印
  iframe.onload = () => {
    iframe.contentWindow?.print()
    // 延迟移除，等打印对话框关闭
    setTimeout(() => document.body.removeChild(iframe), 1000)
  }
  // fallback：有些浏览器不触发 iframe onload for srcdoc-like writes
  setTimeout(() => {
    try { iframe.contentWindow?.print() } catch { /* already printing or removed */ }
    setTimeout(() => { try { document.body.removeChild(iframe) } catch { /* already removed */ } }, 1000)
  }, 200)
}

// ========== Voucher Print Setting ==========

/** 凭证打印纸张类型 */
export type VoucherPrintPaperType = 'A4' | 'B5' | 'CUSTOM'

/** 凭证打印方向 */
export type VoucherPrintOrientation = 'portrait' | 'landscape'

/** 凭证打印设置 */
export interface VoucherPrintSetting {
  paperType: VoucherPrintPaperType
  orientation: VoucherPrintOrientation
  width: number   // 自定义纸张宽度 mm
  height: number  // 自定义纸张高度 mm
  marginLeft: number // 左侧附加边距 mm
  marginTop: number  // 顶部附加边距 mm
  fontSize: number   // 字体大小 px
}

/** 默认凭证打印设置 */
export const DEFAULT_VOUCHER_PRINT_SETTING: VoucherPrintSetting = {
  paperType: 'B5',
  orientation: 'landscape',
  width: 250,
  height: 176,
  marginLeft: 0,
  marginTop: 0,
  fontSize: 16
}

/** 标准纸张尺寸 mm */
const STANDARD_PAPER_SIZE: Record<'A4' | 'B5', { width: number; height: number }> = {
  A4: { width: 210, height: 297 },
  B5: { width: 176, height: 250 }
}

/** 凭证打印基础页边距 mm */
const VOUCHER_PRINT_BASE_MARGIN = 8
/** 每页分录条数 */
const VOUCHER_PRINT_ENTRIES_PER_PAGE = 4

/** 根据设置获得实际打印纸张尺寸 */
function getVoucherPaperSize(setting: VoucherPrintSetting) {
  const rawSize = setting.paperType === 'CUSTOM'
    ? { width: setting.width, height: setting.height }
    : STANDARD_PAPER_SIZE[setting.paperType]
  const shortSide = Math.min(rawSize.width, rawSize.height)
  const longSide = Math.max(rawSize.width, rawSize.height)
  return setting.orientation === 'landscape'
    ? { width: longSide, height: shortSide }
    : { width: shortSide, height: longSide }
}

/** 凭证打印分页信息 */
interface VoucherPrintPage {
  voucher: VoucherPrintData
  entries: Array<VoucherPrintEntry | undefined>
  currentPage: number
  totalPages: number
}

interface VoucherPrintEntry {
  digest: string
  subjectCode?: string
  subjectName?: string
  debitAmount?: number
  creditAmount?: number
  auxiliaries?: Array<{ name?: string }>
}

interface VoucherPrintData {
  voucherWordName?: string
  voucherNumber: number
  voucherTime: number
  attachmentCount: number
  status: number
  debitAmount?: number
  creditAmount?: number
  creatorUserName?: string
  reviewerUserName?: string
  entries: VoucherPrintEntry[]
}

/** 将多张凭证拆分为打印页（每页 4 条分录，补空行） */
function buildVoucherPrintPages(vouchers: VoucherPrintData[]): VoucherPrintPage[] {
  const pages: VoucherPrintPage[] = []
  for (const voucher of vouchers) {
    const totalPages = Math.max(1, Math.ceil(voucher.entries.length / VOUCHER_PRINT_ENTRIES_PER_PAGE))
    for (let pageIndex = 0; pageIndex < totalPages; pageIndex++) {
      const entries: Array<VoucherPrintEntry | undefined> = voucher.entries.slice(
        pageIndex * VOUCHER_PRINT_ENTRIES_PER_PAGE,
        (pageIndex + 1) * VOUCHER_PRINT_ENTRIES_PER_PAGE
      )
      while (entries.length < VOUCHER_PRINT_ENTRIES_PER_PAGE) entries.push(undefined)
      pages.push({ voucher, entries, currentPage: pageIndex + 1, totalPages })
    }
  }
  return pages
}

/** 构建单页凭证 HTML */
function buildVoucherPageHtml(companyName: string, page: VoucherPrintPage): string {
  const { voucher, entries } = page
  const totalDebit = voucher.entries.reduce((s, e) => s + Number(e.debitAmount || 0), 0)
  const totalCredit = voucher.entries.reduce((s, e) => s + Number(e.creditAmount || 0), 0)

  const entryRows = entries.map(entry => {
    if (!entry) return '<tr><td>&nbsp;</td><td></td><td class="money"></td><td class="money"></td></tr>'
    const auxiliary = entry.auxiliaries?.length ? ` / ${entry.auxiliaries.map(a => a.name).join('、')}` : ''
    return `<tr>
      <td>${escapeHtml(entry.digest)}</td>
      <td>${escapeHtml(`${entry.subjectCode || ''} ${entry.subjectName || ''}${auxiliary}`)}</td>
      <td class="money">${Number(entry.debitAmount) ? escapeHtml(formatMoney(entry.debitAmount)) : ''}</td>
      <td class="money">${Number(entry.creditAmount) ? escapeHtml(formatMoney(entry.creditAmount)) : ''}</td>
    </tr>`
  }).join('')

  return `
    <section class="voucher-page">
      <h1>记账凭证</h1>
      <div class="title-double-line"></div>
      <div class="attachment-count">附单据&nbsp;&nbsp;${voucher.attachmentCount || ''}&nbsp;&nbsp;张</div>
      <div class="voucher-meta">
        <span>单位：${escapeHtml(companyName)}</span>
        <span>日期：${dayjs(voucher.voucherTime).format('YYYY年MM月DD日')}</span>
        <span>凭证号：${escapeHtml(voucher.voucherWordName)}-${voucher.voucherNumber}（${page.currentPage}/${page.totalPages}）</span>
      </div>
      <table class="voucher-table">
        <thead><tr><th>摘要</th><th>会计科目</th><th>借方金额</th><th>贷方金额</th></tr></thead>
        <tbody>${entryRows}</tbody>
        <tfoot><tr><td colspan="2">合计：${escapeHtml(formatUppercaseMoney(totalDebit))}</td><td class="money">${escapeHtml(formatMoney(totalDebit))}</td><td class="money">${escapeHtml(formatMoney(totalCredit))}</td></tr></tfoot>
      </table>
      <div class="voucher-footer"><span>财务主管：</span><span>审核：${escapeHtml(voucher.reviewerUserName)}</span><span>出纳：</span><span>制单：${escapeHtml(voucher.creatorUserName)}</span></div>
    </section>
  `
}

/**
 * 凭证打印用：构建凭证打印 HTML。
 * 支持多张凭证、分页（每页 4 条分录）、纸张设置。
 */
export function buildVoucherPrintHtml({
  companyName,
  voucher,
  vouchers,
  setting
}: {
  companyName: string
  voucher?: VoucherPrintData
  vouchers?: VoucherPrintData[]
  setting?: VoucherPrintSetting
}): string {
  const effectiveSetting = setting || DEFAULT_VOUCHER_PRINT_SETTING
  const voucherList = vouchers || (voucher ? [voucher] : [])

  if (voucherList.length === 0) return ''

  const { width, height } = getVoucherPaperSize(effectiveSetting)
  const marginTop = VOUCHER_PRINT_BASE_MARGIN + effectiveSetting.marginTop
  const marginLeft = VOUCHER_PRINT_BASE_MARGIN + effectiveSetting.marginLeft
  const fontSize = effectiveSetting.fontSize

  const pages = buildVoucherPrintPages(voucherList)
  const content = pages.map(page => buildVoucherPageHtml(companyName, page)).join('')

  const pageStyle = `@page { size: ${width}mm ${height}mm; margin: ${marginTop}mm ${VOUCHER_PRINT_BASE_MARGIN}mm ${VOUCHER_PRINT_BASE_MARGIN}mm ${marginLeft}mm; }`

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"/>
<title>凭证打印</title>
<style>
* { box-sizing: border-box; }
body { margin: 0; background: #eef0f3; color: #303133; font-family: Arial, "Microsoft YaHei", sans-serif; font-size: ${fontSize}px; }
${pageStyle}
.voucher-page { box-sizing: border-box; width: ${width}mm; min-height: ${height}mm; margin: 16px auto; padding: 8mm; background: #fff; box-shadow: 0 2px 12px rgba(0, 0, 0, .12); page-break-after: always; }
h1 { margin: 0; text-align: center; font-size: 30px; font-weight: 500; }
.title-double-line { width: 200px; height: 6px; margin: 8px auto; border-top: 1px solid; border-bottom: 1px solid; }
.attachment-count { margin-bottom: 6px; text-align: right; }
.voucher-meta, .voucher-footer { display: flex; justify-content: space-between; gap: 16px; padding: 7px 0; }
table { width: 100%; border-collapse: collapse; }
th, td { border: 1px solid #303133; padding: 10px 8px; vertical-align: middle; }
.voucher-table th:nth-child(1) { width: 28%; }
.voucher-table th:nth-child(2) { width: 38%; }
.voucher-table th:nth-child(3), .voucher-table th:nth-child(4) { width: 17%; }
.voucher-table tbody tr { height: 54px; }
.money { text-align: right; }
.voucher-footer span { width: 25%; }
.voucher-footer span:nth-child(2), .voucher-footer span:nth-child(3) { text-align: center; }
.voucher-footer span:last-child { text-align: right; }
@media print {
  body { background: #fff; }
  .voucher-page { width: auto; min-height: auto; margin: 0; padding: 0; box-shadow: none; }
}
</style>
</head>
<body>
<main>${content}</main>
</body>
</html>`
}
