// UTF-8. Existing Playwright/Chromium; synthetic fixture only.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ headless: true, executablePath: process.env.LEAD_TEST_CHROME })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', error => { errors.push(error.message); console.log('PAGE ERROR', error.stack) })
const base = process.env.LEAD_TEST_BASE || 'http://localhost:5174'
const accept = () => page.locator('.assignment-accept-btn')
async function open(mode = 'normal', extra = '') {
  console.log('CASE', mode, extra)
  await page.goto(`${base}/test/lead-accept.html?mode=${mode}${extra}`)
  await accept().waitFor()
}
async function overview(id = 101) {
  await page.waitForFunction(id => document.querySelector('#route')?.textContent?.endsWith(`leadId=${id}&tab=overview`), id)
  await page.getByRole('tab', { name: '概览', exact: true }).last().waitFor()
  assert.equal(await page.getByRole('tab', { name: '概览', exact: true }).last().getAttribute('aria-selected'), 'true')
  await page.getByText(`TEST-${id}`, { exact: true }).last().waitFor()
}
try {
  await open(); await accept().click(); await overview()
  await page.screenshot({ path: process.env.TEMP + '/lead-accept-desktop.png' })
  await page.getByRole('tab', { name: /跟进记录/ }).last().click()
  await page.getByRole('button', { name: '提交跟进', exact: true }).last().waitFor()
  for (const label of ['跟进方式', '跟进结果']) {
    await page.getByLabel(label, { exact: true }).click()
    await page.locator('.ant-select-item-option-content:visible').filter({ hasText: '测试选项' }).last().click()
  }
  await page.getByLabel('跟进备注', { exact: true }).fill('接单后的首次沟通（测试）')
  await page.getByRole('button', { name: '+1 天', exact: true }).click()
  await page.getByRole('button', { name: '提交跟进', exact: true }).click()
  await page.getByRole('button', { name: '确认执行', exact: true }).click()
  await page.getByText('跟进已保存', { exact: true }).waitFor()
  for (const mode of ['specified', 'refresh-error']) { await open(mode); await accept().click(); await overview() }
  await open('normal', '&same=1'); await accept().click(); await overview()
  for (const mode of ['queue', 'race']) {
    await open(mode); await accept().click()
    await page.getByText('TEST-102', { exact: true }).waitFor()
    await accept().click(); await overview(102)
    await page.waitForTimeout(1800)
    assert.equal(await page.getByText('TEST-101', { exact: true }).count(), 0)
  }
  await open('accept-error'); await accept().click()
  await page.getByText('接单已超时（测试）', { exact: true }).waitFor()
  assert.equal(await page.locator('#route').textContent(), '/other')
  await open(); await page.getByRole('button', { name: '不接单', exact: true }).click()
  assert.equal(await page.locator('#route').textContent(), '/other')
  await open('specified'); await page.getByRole('button', { name: '稍后接单', exact: true }).click()
  assert.equal(await page.locator('#route').textContent(), '/other')
  await open('detail-error'); await accept().click()
  await page.getByText('详情加载失败（测试）', { exact: true }).last().waitFor()
  await page.getByRole('button', { name: /重试/ }).last().click(); await overview()
  await open('denied'); await accept().click(); await overview()
  assert.equal(await page.getByRole('tab', { name: /跟进记录/ }).count(), 0)
  await open('object-denied'); await accept().click()
  await page.getByText('无权查看此客资（测试）', { exact: true }).last().waitFor()
  assert.equal(await page.getByRole('tab', { name: '概览', exact: true }).count(), 0)
  await open(); await accept().click(); await overview()
  await page.getByRole('button', { name: '表格模式', exact: true }).click()
  await page.getByRole('dialog').waitFor()
  await page.waitForTimeout(500)
  await page.screenshot({ path: process.env.TEMP + '/lead-accept-table.png' })
  await open('normal', '&same=1'); await accept().click(); await overview()
  await page.getByRole('dialog').waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  await open(); await accept().click(); await overview()
  await page.getByRole('dialog').waitFor()
  await page.waitForTimeout(500)
  await page.screenshot({ path: process.env.TEMP + '/lead-accept-mobile.png' })
  await open('queue'); await accept().click()
  await page.locator('.lead-assignment-modal').getByText('TEST-102', { exact: true }).waitFor()
  await accept().click(); await overview(102)
  assert.deepEqual(errors, [])
  console.log('PASS: acceptance, same-page, filtered-empty list, queue/race, failure/retry, denied, table/mobile and follow-up entry')
} catch (error) { console.log((await page.locator('body').innerText()).slice(0, 4500)); throw error } finally { await browser.close() }
