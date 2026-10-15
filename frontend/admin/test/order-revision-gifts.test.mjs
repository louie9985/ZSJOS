import test from 'node:test'
import assert from 'node:assert/strict'
import { readRevisionGifts, revisionGiftOptions, revisionGiftPayload } from '../src/views/zsjos/mySalesOrder/orderRevisionGifts.ts'

const history = { code: 'old', name: '原礼品名称', path: ['原分类', '原礼品名称'] }
test('both historical encodings retain codes and historical labels after catalog rename or deletion', () => {
  for (const giftItems of [JSON.stringify([history]), JSON.stringify([JSON.stringify(history)])]) {
    const snapshots = readRevisionGifts({ giftItems })
    assert.deepEqual(snapshots, [history])
    assert.deepEqual(revisionGiftOptions([{ id: 1, name: '当前新名称', code: 'old', status: 0, sort: 0 }], snapshots),
      [{ value: 'old', label: '原分类 / 原礼品名称' }])
    assert.deepEqual(revisionGiftOptions([], snapshots).map(x => x.value), ['old'])
  }
})
test('corrupt history blocks submission instead of silently clearing', () => {
  for (const order of [{ giftItemsInvalid: true }, { giftItems: '[42]' }, { giftItems: '{' },
    { giftItemSnapshots: [history, history] }, { giftItemSnapshots: [history], giftItemCodes: ['different'] }]) {
    assert.throws(() => readRevisionGifts(order), /历史礼品信息无法读取/)
  }
})
test('catalog choices come only from enabled leaves, retained history is always available', () => {
  const options = revisionGiftOptions([
    { id: 1, code: 'root', name: '分类', status: 0, sort: 0 },
    { id: 2, parentId: 1, code: 'new', name: '可选礼品', status: 0, sort: 1 },
    { id: 3, code: 'disabled', name: '已停用', status: 1, sort: 0 }
  ], [history])
  assert.deepEqual(options.map(x => x.value), ['new', 'old'])
  assert.equal(options[0].label, '分类 / 可选礼品')
})
test('unchanged, edited and explicitly cleared selections use array payloads', () => {
  const options = [{ value: 'old', label: '原礼品' }, { value: 'new', label: '新礼品' }]
  assert.deepEqual(revisionGiftPayload(['old'], ' 原地址 ', options), { giftItems: ['old'], giftShippingAddress: '原地址' })
  assert.deepEqual(revisionGiftPayload(['new'], '新地址', options), { giftItems: ['new'], giftShippingAddress: '新地址' })
  assert.deepEqual(revisionGiftPayload([], '旧地址', options), { giftItems: [], giftShippingAddress: undefined })
  assert.throws(() => revisionGiftPayload(['new'], ' ', options), /邮寄地址/)
  assert.throws(() => revisionGiftPayload(['injected'], '地址', options), /重新选择/)
})
