// UTF-8. Uses an existing Playwright installation; only synthetic fixture requests.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ headless: true, executablePath: process.env.OPERATOR_TEST_CHROME })
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } })
const errors = []
page.on('pageerror', error => errors.push(error.message))
const base = process.env.OPERATOR_TEST_BASE || 'http://localhost:5174'
const confirm = () => page.getByRole('button', { name: '确认指派' })
const result = () => page.locator('#result').textContent()
async function open(mode) {
  await page.goto(`${base}/test/operator-assignment.html?mode=${mode}`)
  await page.getByRole('dialog').waitFor()
  if (!['loading', 'empty', 'denied', 'load-error'].includes(mode)) await confirm().waitFor({ state: 'visible' })
}
async function select(name = '新运营') {
  await page.getByRole('combobox').click()
  await page.locator('.ant-select-item-option-content').filter({ hasText: name }).click()
}
async function submitSuccess() {
  await confirm().click()
  await page.getByRole('dialog').waitFor({ state: 'hidden' })
}
try {
  await open('change'); await select()
  const student = page.locator('.ant-form-item').filter({ hasText: '当前学员' })
  const operator = page.locator('.ant-form-item').filter({ hasText: '当前运营' })
  assert.ok((await student.textContent()).includes('测试学员'))
  assert.ok((await operator.textContent()).includes('原运营'))
  assert.equal(await student.locator('input,select,textarea').count(), 0)
  assert.equal(await operator.locator('input,select,textarea').count(), 0)
  await confirm().click()
  await page.locator('.ant-form-item-explain-error').filter({ hasText: '变更已有运营时请填写原因' }).waitFor()
  assert.equal(await result(), '尚未提交')
  await page.getByRole('textbox').fill('   '); await confirm().click()
  assert.equal(await result(), '尚未提交')
  assert.equal(await page.getByRole('textbox').getAttribute('maxlength'), '500')
  await page.getByRole('textbox').fill('  工作交接  ')
  await page.locator('.ant-form-item-explain-error').waitFor({ state: 'hidden' })
  await page.screenshot({ path: process.env.TEMP + '/operator-assignment-desktop.png' })
  await submitSuccess()
  assert.equal(JSON.parse(await result()).correctionReason, '工作交接')
  assert.equal(JSON.parse(await result()).attempts, 1)

  for (const mode of ['first', 'same']) {
    await open(mode); await select(mode === 'same' ? '原运营' : '新运营')
    assert.ok((await operator.textContent()).includes(mode === 'first' ? '未指派' : '原运营'))
    await submitSuccess()
    assert.equal(JSON.parse(await result()).correctionReason, undefined)
  }
  await open('conflict'); await select('原运营'); await confirm().click()
  await page.locator('.ant-form-item-explain-error').waitFor()
  assert.equal(await result(), '尚未提交')
  await page.getByRole('textbox').fill('统一归属'); await submitSuccess()

  await open('server-required'); await select(); await confirm().click()
  await page.locator('.ant-form-item-explain-error').waitFor()
  assert.equal(JSON.parse(await result()).attempts, 1)
  assert.ok((await page.locator('.ant-select').textContent()).includes('新运营'))
  await page.getByRole('textbox').fill('其他课程交接'); await submitSuccess()
  assert.equal(JSON.parse(await result()).attempts, 2)

  await open('version'); await select(); await page.getByRole('textbox').fill('交接原因'); await confirm().click()
  await page.getByRole('button', { name: '刷新运营信息' }).waitFor()
  assert.equal(await confirm().isDisabled(), true)
  await page.getByRole('button', { name: '刷新运营信息' }).click()
  await page.waitForFunction(() => !document.querySelector('.ant-modal-footer .ant-btn-primary').disabled)
  assert.equal(await page.getByRole('textbox').inputValue(), '交接原因')
  await submitSuccess(); assert.equal(JSON.parse(await result()).version, 2)

  await open('network'); await select(); await page.getByRole('textbox').fill('网络重试'); await confirm().click()
  await page.getByText('网络异常（测试）', { exact: true }).waitFor()
  const key = JSON.parse(await result()).idempotencyKey
  await submitSuccess(); assert.equal(JSON.parse(await result()).idempotencyKey, key)

  for (const mode of ['empty', 'denied', 'load-error', 'loading']) {
    await open(mode)
    if (mode === 'empty') await page.getByText('暂无可用运营，请联系管理员配置人员关系').waitFor()
    if (mode === 'denied') await page.getByText('当前无权为该学员指派运营').waitFor()
    if (mode === 'load-error') await page.getByRole('button', { name: '重试加载' }).waitFor()
    assert.equal(await confirm().isDisabled(), true)
    if (mode === 'load-error') {
      await page.getByRole('button', { name: '重试加载' }).click(); await select(); await page.getByRole('textbox').fill('重试后交接'); await submitSuccess()
    }
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await open('change'); await select(); await page.getByRole('textbox').fill('移动端交接')
  await page.getByRole('textbox').click()
  await page.locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden)').waitFor({ state: 'hidden' })
  const bounds = await page.getByRole('dialog').boundingBox()
  assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390)
  await page.screenshot({ path: process.env.TEMP + '/operator-assignment-mobile.png' })
  await submitSuccess()
  assert.deepEqual(errors, [])
  console.log('PASS: 12 browser scenarios, desktop/mobile, no uncaught errors')
} finally { await browser.close() }
