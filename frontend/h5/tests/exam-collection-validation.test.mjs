import { test } from 'node:test'
import assert from 'node:assert/strict'
import { effectiveExamFields, validateExamCollection } from '../src/utils/examCollectionValidation.ts'

const field = (key, type = 'text', required = true) => ({ key, label: key, type, enabled: true, required, group: 'basic', sensitive: false, maxImages: 9 })
const basic = [field('name'), field('mobile'), field('id_card')]
const input = { name: '合成学员', mobile: '13800000000', wechat: 'synthetic', id_card: '11010519491231002x' }
test('exact frozen fields are submitted with trimmed uppercase identity', () => {
  const result = validateExamCollection(basic, { ...input, name: ' 合成学员 ', gift_shipping_address: 'unselected' }, [{ id: 9, fieldKey: 'portrait', side: '' }])
  assert.deepEqual(result.errors, {})
  assert.deepEqual(Object.keys(result.values), ['name', 'mobile', 'id_card'])
  assert.equal(result.values.id_card, '11010519491231002X')
  assert.equal(result.values.name, '合成学员')
})
test('missing fixed fields, invalid mobile and checksum are actionable', () => {
  assert.equal(Object.keys(validateExamCollection(basic, {}, []).errors).length, 3)
  const result = validateExamCollection(basic, { ...input, mobile: '123', id_card: '110105194912310021' }, [])
  assert.deepEqual(Object.keys(result.errors), ['mobile', 'id_card'])
})
test('selected materials require valid count and identity sides', () => {
  const fields = [...basic, field('identity_images', 'images'), field('portrait', 'images')]
  assert.equal(Object.keys(validateExamCollection(fields, input, []).errors).length, 2)
  const files = [{ id: 1, fieldKey: 'identity_images', side: 'front' }, { id: 2, fieldKey: 'identity_images', side: 'front' }, { id: 3, fieldKey: 'portrait', side: '' }]
  assert.ok(validateExamCollection(fields, input, files).errors.identity_images)
  files[1].side = 'back'
  const result = validateExamCollection(fields, input, files)
  assert.deepEqual(result.errors, {})
  assert.deepEqual(result.values.identity_images, [1, 2])
  assert.deepEqual(result.values.portrait, [3])
})
test('disabled fields do not participate and selected address remains required', () => {
  const hidden = { ...field('portrait', 'images'), enabled: false }
  const result = validateExamCollection([...basic, hidden, field('certificate_shipping_address', 'textarea')], input, [])
  assert.deepEqual(Object.keys(result.errors), ['certificate_shipping_address'])
  assert.equal(Object.hasOwn(result.values, 'portrait'), false)
})

test('legacy required WeChat is absent from rendered fields, errors and submitted values', () => {
  const legacy = [...basic, field('wechat')]
  assert.deepEqual(effectiveExamFields(legacy).map(field => field.key), ['name', 'mobile', 'id_card'])
  const result = validateExamCollection(legacy, input, [])
  assert.deepEqual(result.errors, {})
  assert.equal(Object.hasOwn(result.values, 'wechat'), false)
  assert.equal(Object.hasOwn(validateExamCollection(legacy, {}, []).errors, 'wechat'), false)
})
