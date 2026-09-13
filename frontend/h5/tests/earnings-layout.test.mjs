import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const earningsSource = readFileSync(path.join(projectRoot, 'src/pages/earnings/index.vue'), 'utf8')
const helpPopoverSource = readFileSync(path.join(projectRoot, 'src/components/HelpPopover.vue'), 'utf8')

test('收益汇总使用 2x2 等分布局且单元内容水平和垂直居中', () => {
  assert.match(earningsSource, /\.earnings-hero__grid\s*\{[^}]*grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/s)
  assert.match(earningsSource, /\.earnings-hero__grid\s*\{[^}]*grid-template-rows:\s*repeat\(2, minmax\(0, 1fr\)\)/s)
  assert.match(earningsSource, /\.earnings-hero__grid\s*\{[^}]*gap:\s*8px 16px/s)
  assert.match(earningsSource, /\.earnings-hero__item\s*\{[^}]*display:\s*flex[^}]*flex-direction:\s*column[^}]*align-items:\s*center[^}]*justify-content:\s*center[^}]*text-align:\s*center/s)
})

test('收益汇总、筛选和首张卡片间距收紧且后续卡片间距不变', () => {
  assert.match(earningsSource, /\.earnings-tabs-wrap\s*\{[^}]*padding:\s*4px 16px/s)
  assert.match(earningsSource, /\.earnings-card\s*\{[^}]*margin:\s*4px 16px 0/s)
  assert.match(earningsSource, /\.earnings-card \+ \.earnings-card\s*\{[^}]*margin-top:\s*12px/s)
})

test('收益帮助气泡展示四项金额说明并启用主题浅色底', () => {
  assert.match(earningsSource, /累计收益：累计生成的返现金额。/)
  assert.match(earningsSource, /待结算：已生成，未转为可提现的金额。/)
  assert.match(earningsSource, /提现中：处于审核中的金额。/)
  assert.match(earningsSource, /已提现：提现成功的金额。/)
  assert.match(earningsSource, /<HelpPopover[\s\S]*:text="earningsSummaryHelp"[\s\S]*theme-tint[\s\S]*aria-label="查看收益金额说明"/)

  assert.match(helpPopoverSource, /themeTint\?: boolean/)
  assert.match(helpPopoverSource, /'help-popover--theme': props\.themeTint/)
  assert.match(helpPopoverSource, /\.help-popover--theme\.van-popover\)[^{]*\{[^}]*var\(--h5-primary\) 12%[^}]*var\(--h5-glass-surface-strong\)/s)
  assert.match(helpPopoverSource, /\.help-popover--theme\.van-popover--light \.van-popover__arrow\)[^{]*\{[^}]*var\(--help-popover-theme-background\)/s)
})

test('提现悬浮按钮使用人民币钱币图标', () => {
  assert.match(earningsSource, /class="earnings-fab"[^>]*aria-label="申请提现"[^>]*>[\s\S]*?<van-icon name="gold-coin-o" size="25" color="#fff" \/>/)
  assert.doesNotMatch(earningsSource, /class="earnings-fab"[^>]*>[\s\S]*?<van-icon name="plus"/)
})
