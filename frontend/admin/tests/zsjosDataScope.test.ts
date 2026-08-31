import assert from 'node:assert/strict'
import test from 'node:test'
import { cashbackDataScope, withdrawalDataScope } from '../src/utils/zsjosDataScope.ts'

const permissions = (...values: string[]) => new Set(values)

test('withdrawal scope prioritizes finance, then administrator, then own access', () => {
  assert.equal(withdrawalDataScope(permissions('*:*:*')), 'all')
  assert.equal(
    withdrawalDataScope(permissions('zsjos:withdrawal:finance-query', 'zsjos:withdrawal:my-query')),
    'all'
  )
  assert.equal(withdrawalDataScope(permissions('zsjos:withdrawal:admin-query')), 'all')
  assert.equal(withdrawalDataScope(permissions('zsjos:withdrawal:my-query')), 'own')
  assert.equal(withdrawalDataScope(permissions()), 'unauthorized')
})

test('cashback scope uses finance view only when finance permission exists', () => {
  assert.equal(cashbackDataScope(permissions('*:*:*')), 'all')
  assert.equal(
    cashbackDataScope(permissions('zsjos:cashback:finance-query', 'zsjos:cashback:my-query')),
    'all'
  )
  assert.equal(cashbackDataScope(permissions('zsjos:cashback:my-query')), 'own')
  assert.equal(cashbackDataScope(permissions()), 'unauthorized')
})
