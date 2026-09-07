import { test } from 'node:test'
import assert from 'node:assert/strict'
import { validCollectionIdentity } from '../src/utils/studentInfoValidation.ts'
test('identity rejects malformed dates and checksum', () => {
  assert.equal(validCollectionIdentity(''), false)
  assert.equal(validCollectionIdentity('000000000000000000'), false)
  assert.equal(validCollectionIdentity('110101200013010010'), false)
  assert.equal(validCollectionIdentity('110101200001010019'), false)
})
test('identity validates a synthetic checksum and legacy date', () => {
  const prefix = '11010120000101001'
  const weights = [7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2]
  const check = '10X98765432'[weights.reduce((sum, value, i) => sum + value * Number(prefix[i]), 0) % 11]
  assert.equal(validCollectionIdentity(prefix + check), true)
  assert.equal(validCollectionIdentity('110101000101001'), true)
})
