import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validCollectionIdentity, deriveCollectionIdentity, findCollectionIdentityArea } from '../src/utils/studentInfoValidation.ts'
test('identity rejects malformed dates and checksum', () => {
  assert.equal(validCollectionIdentity(''), false)
  assert.equal(validCollectionIdentity('000000000000000000'), false)
  assert.equal(validCollectionIdentity('110101200013010010'), false)
  assert.equal(validCollectionIdentity('110101200001010019'), false)
})

const identity = (prefix) => prefix + '10X98765432'[[7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2].reduce((sum, weight, i) => sum + weight * Number(prefix[i]), 0) % 11]
test('identity derives completed years, sex and exact area code, clearing invalid input', () => {
  const card = identity('11010120001019001')
  assert.deepEqual(deriveCollectionIdentity(card, new Date('2026-10-18T12:00:00+08:00')), { age: '25', gender: '1', areaCode: '110101' })
  assert.equal(deriveCollectionIdentity(card, new Date('2026-10-19T12:00:00+08:00')).age, '26')
  assert.equal(deriveCollectionIdentity(card, new Date('2026-10-18T16:00:00Z')).age, '26')
  assert.equal(deriveCollectionIdentity(identity('11010120001019002')).gender, '2')
  assert.equal(deriveCollectionIdentity(''), undefined)
  assert.equal(deriveCollectionIdentity(card.slice(0,17) + (card[17] === '0' ? '1' : '0')), undefined)
  assert.deepEqual(deriveCollectionIdentity(' ' + card.toLowerCase() + ' '), deriveCollectionIdentity(card))
})
test('identity area uses authoritative codes and full parent path without guessing missing historical regions', () => {
  const county = { id: 30, selectionCode: '110101', name: '测试区' }
  const city = { id: 20, selectionCode: '110100', name: '测试市', children: [county] }
  const province = { id: 10, selectionCode: '110000', name: '测试省', children: [city] }
  assert.deepEqual(findCollectionIdentityArea([province], '110101'), [province, city, county])
  assert.deepEqual(findCollectionIdentityArea([province], '110102'), [])
  assert.deepEqual(findCollectionIdentityArea([{ id: 110101, name: '兼容地区' }], '110101').map(x => x.id), [110101])
})
test('identity validates a synthetic checksum and rejects legacy date', () => {
  const prefix = '11010120000101001'
  const weights = [7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2]
  const check = '10X98765432'[weights.reduce((sum, value, i) => sum + value * Number(prefix[i]), 0) % 11]
  assert.equal(validCollectionIdentity(prefix + check), true)
  assert.equal(validCollectionIdentity('110101000101001'), false)
})
