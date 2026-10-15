// UTF-8. Existing Playwright/Chrome; all transport is synthetic.
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
const require = createRequire(import.meta.url)
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const base = process.env.LEAD_TEST_BASE || 'http://localhost:5174'
const page = await browser.newPage()
const errors = []
page.on('pageerror', error => errors.push(error.message))
const transfer = () => page.locator('.lead-action-toolbar > button').filter({ hasText: '转派' })
async function open(scenario, mode = 'owner') {
  await page.goto(`${base}/test/lead-transfer-entry.html?case=${scenario}&mode=${mode}`)
  await page.getByText('客资待回收处理', { exact: true }).waitFor()
}
async function submit(scenario, expectedPath, mode = 'owner') {
  await open(scenario, mode)
  assert.equal(await transfer().count(), 1)
  assert.equal(await transfer().isEnabled(), true)
  await transfer().click()
  const dialog = page.getByRole('dialog', { name: '转派客资' })
  await dialog.getByRole('combobox').click()
  await page.locator('.ant-select-item-option-content:visible').getByText('验证销售', { exact: true }).click()
  await dialog.locator('textarea').fill('验证单一转派入口')
  await dialog.getByRole('button', { name: '确认处理' }).click()
  await page.getByRole('button', { name: '确认执行', exact: true }).click()
  await page.waitForFunction(() => document.querySelector('#completed')?.textContent === '1')
  const calls = await page.evaluate(() => window.transferCalls)
  const posts = calls.filter(call => call.method === 'post')
  assert.equal(posts.length, 1)
  assert.equal(posts[0].url, expectedPath)
  const supervisor = expectedPath.includes('subordinate-sales')
  assert.equal(posts[0].body[supervisor ? 'targetUserId' : 'salesUserId'], 200)
  assert.ok(posts[0].body.idempotencyKey)
  assert.equal(calls.filter(call => call.url.endsWith('/transfer-candidates')).at(-1).url,
    supervisor ? '/zsjos/subordinate-sales/transfer-candidates' : '/zsjos/lead/101/transfer-candidates')
}
try {
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    await submit('both', '/zsjos/subordinate-sales/leads/101/transfer')
    await submit('qualification', '/zsjos/lead/101/transfer')
    await submit('supervisor', '/zsjos/subordinate-sales/leads/101/transfer')
    await submit('supervisor-disabled', '/zsjos/lead/101/transfer')
    await submit('qualification-disabled', '/zsjos/subordinate-sales/leads/101/transfer')
    await submit('both', '/zsjos/subordinate-sales/leads/101/transfer', 'manager')
    await open('disabled')
    assert.equal(await transfer().count(), 1)
    assert.equal(await transfer().isDisabled(), true)
    await open('none')
    assert.equal(await transfer().count(), 0)
    await open('both', 'readonly')
    assert.equal(await transfer().count(), 0)
    await open('qualification', 'manager')
    assert.equal(await transfer().count(), 0)
    await open('both')
    await page.screenshot({ path: `${process.env.TEMP}/lead-transfer-entry-${width}.png` })
  }
  assert.deepEqual(errors, [])
  console.log('PASS: desktop/mobile single entry, enabled fallback, denied/read-only/manager cases, matching candidate and submission APIs, one POST with idempotency key')
} finally {
  await browser.close()
}
