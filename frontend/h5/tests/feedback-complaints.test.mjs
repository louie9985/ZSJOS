import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'

const readSource = path => readFile(new URL(path, import.meta.url), 'utf8')

test('H5 defines an app font baseline that overrides flexible body inheritance', async () => {
  const source = await readSource('../src/styles/base.css')
  assert.match(source, /#app\s*\{[\s\S]*?font-size:\s*14px;/)
})

test('feedback detail serializes mark-read and protects the heading layout', async () => {
  const source = await readSource('../src/pages/feedback/detail.vue')
  assert.match(source, /if \(result\.unread\) await markRead\(result\)/)
  assert.match(source, /\.detail-heading__main\{min-width:0;flex:1\}/)
  assert.match(source, /\.detail-heading__meta\{[^}]*font-size:12px;/)
  assert.match(source, /\.detail-heading__status\{flex:0 0 auto;white-space:nowrap\}/)
})

test('complaint history uses pending semantics and renders both evidence groups', async () => {
  const source = await readSource('../src/pages/lead/complaints.vue')
  assert.match(source, /item\.status === 'pending' \? '待处理'/)
  assert.match(source, /<van-tab name="pending" title="待处理"/)
  assert.doesNotMatch(source, /title="处理中"/)
  assert.match(source, /v-for="\(file, index\) in item\.evidence"/)
  assert.match(source, /v-for="\(file, index\) in item\.handlerEvidence"/)
  assert.match(source, /showImagePreview\(/)
  assert.match(source, /\.complaint-card__meta\{[^}]*font-size:12px;/)
  assert.match(source, /\.complaint-card__status\{flex:0 0 auto;white-space:nowrap\}/)
  assert.doesNotMatch(source, /handlerUserName \|\| '待处理'/)
})
