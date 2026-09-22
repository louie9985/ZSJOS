// UTF-8. Browser validation against synthetic transport only.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
page.setDefaultTimeout(12000)
const errors = []
page.on('pageerror', e => errors.push(e.message))
try {
  await page.goto((process.env.LEAD_TEST_BASE || 'http://localhost:5174') + '/test/lead-refresh.html')
  await page.locator('.lead-action-toolbar > button').filter({ hasText: '跟进' }).click()
  async function submit(scope, remark) {
    for (const label of ['跟进方式', '跟进结果']) {
      await scope.locator('.ant-form-item').filter({ hasText: label }).getByRole('combobox').click()
      await scope.locator('.ant-form-item').filter({ hasText: label }).getByRole('combobox').press('ArrowDown')
      await scope.locator('.ant-form-item').filter({ hasText: label }).getByRole('combobox').press('Enter')
    }
    await scope.locator('textarea').fill(remark)
    await scope.getByRole('button', { name: '+1 天', exact: true }).click()
    await scope.getByRole('button', { name: '提交跟进', exact: true }).click()
    await page.getByRole('button', { name: '确认执行', exact: true }).click()
  }
  await submit(page.getByRole('dialog'), '弹窗新增记录')
  await page.locator('.lead-latest-followup').getByText('弹窗新增记录').waitFor()
  await page.getByRole('tab', { name: /跟进记录/ }).click()
  await submit(page.locator('.fu-panel-form-wrapper'), '页内新增记录')
  await page.getByRole('tab', { name: '概览', exact: true }).click()
  await page.locator('.lead-latest-followup').getByText('页内新增记录').waitFor()
  assert.ok(await page.locator('.lead-latest-followup-next').textContent())
  await page.getByRole('button', { name: '模拟读取失败' }).click()
  await page.getByText('最近跟进读取失败（验证）', { exact: true }).waitFor()
  await page.getByRole('button', { name: /^重\s*试$/ }).click()
  await page.locator('.lead-latest-followup').getByText('页内新增记录').waitFor()
  await page.goto((process.env.LEAD_TEST_BASE || 'http://localhost:5174') + '/test/lead-refresh.html?table')
  await page.locator('.ant-pagination-item-2').click()
  await page.locator('.ant-table-tbody').getByRole('button', { name: /详细/ }).first().click()
  await page.locator('.ant-drawer .lead-action-toolbar > button').filter({ hasText: '跟进' }).click()
  await submit(page.getByRole('dialog', { name: '新增跟进', exact: true }), '表格新增记录')
  await page.locator('.lead-latest-followup').getByText('表格新增记录').waitFor()
  assert.ok((await page.locator('.ant-pagination-item-active').getAttribute('title')) === '2')
  await page.locator('.ant-drawer-close').click()
  const date = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(Date.now()+86400000))
  assert.ok((await page.locator('.ant-table-tbody').innerText()).includes(date))
  assert.deepEqual(errors, [])
  console.log('PASS: modal + inline submit refresh latest record/next date; error and retry; page-2 table refresh')
} catch(e) { console.log(errors); console.log((await page.locator('body').innerText()).slice(0,4500)); throw e }
finally { await browser.close() }
