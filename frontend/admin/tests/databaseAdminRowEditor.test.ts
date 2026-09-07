import assert from 'node:assert/strict'
import test from 'node:test'
import {
  changedValues,
  editorValue,
  fieldError
} from '../src/views/infra/databaseAdmin/rowEditor.ts'
import type { DatabaseAdminColumnVO } from '../src/api/infra/databaseAdmin/index'

const column = (
  name: string,
  valueKind: DatabaseAdminColumnVO['valueKind']
): DatabaseAdminColumnVO => ({
  name,
  valueKind,
  jdbcType: 12,
  typeName: 'VARCHAR',
  nullable: true,
  primaryKey: false,
  autoIncrement: false,
  sensitive: false,
  editable: true,
  generated: false
})

test('editing only attachment text sends only that column and preserves boolean/null/time', () => {
  const columns = [
    column('payload', 'text'),
    column('flag', 'boolean'),
    column('at', 'datetime'),
    column('note', 'text')
  ]
  const before = {
    payload: '{"url":"https://example.invalid/old"}',
    flag: false,
    at: '2026-09-06 12:13:14.123456',
    note: null
  }
  const after = { ...before, payload: '{"url":"https://example.invalid/new"}' }
  assert.deepEqual(
    changedValues(
      columns,
      before,
      after,
      { payload: 'value', flag: 'value', at: 'value', note: 'null' },
      false
    ),
    { payload: after.payload }
  )
  assert.deepEqual(
    changedValues(
      columns,
      before,
      before,
      { payload: 'value', flag: 'value', at: 'value', note: 'null' },
      false
    ),
    {}
  )
})

test('null, empty text and database defaults remain distinct', () => {
  const columns = [column('note', 'text')]
  assert.equal(editorValue(columns[0], undefined), null)
  assert.deepEqual(changedValues(columns, { note: null }, { note: '' }, { note: 'value' }, false), {
    note: ''
  })
  assert.deepEqual(changedValues(columns, { note: '' }, { note: '' }, { note: 'null' }, false), {
    note: null
  })
  assert.deepEqual(changedValues(columns, {}, { note: '' }, { note: 'default' }, true), {})
  assert.deepEqual(changedValues(columns, {}, { note: '' }, { note: 'value' }, true), { note: '' })
})

test('exact numeric comparisons do not round and unchanged temporal fractions are omitted', () => {
  const columns = [column('id', 'integer'), column('price', 'decimal'), column('at', 'datetime')]
  const original = {
    id: '9007199254740993',
    price: '123456789012345678.123400',
    at: '2026-09-06 12:00:00.100000'
  }
  assert.deepEqual(
    changedValues(
      columns,
      original,
      { ...original, price: '123456789012345678.1234', at: '2026-09-06T12:00:00.1' },
      { id: 'value', price: 'value', at: 'value' },
      false
    ),
    {}
  )
  assert.equal(editorValue(columns[0], original.id), original.id)
  assert.throws(() => editorValue(columns[0], 9007199254740992))
})

test('boolean compatibility is strict and text is not coerced', () => {
  const flag = column('flag', 'boolean')
  assert.equal(editorValue(flag, false), false)
  assert.equal(editorValue(flag, 'false'), false)
  assert.equal(editorValue(flag, '1'), true)
  assert.throws(() => editorValue(flag, 'yes'))
  assert.equal(editorValue(column('text', 'text'), '  false\n'), '  false\n')
  assert.ok(fieldError(flag, 'false'))
})

test('validates native JSON without rewriting it and does not infer JSON from column names', () => {
  const json = column('payload', 'json')
  const text = '{"id":9007199254740993,"url":"https://example.invalid/a?x=1&y=2"}'
  assert.equal(fieldError(json, text), undefined)
  assert.equal(editorValue(json, text), text)
  assert.ok(fieldError(json, '{'))
  assert.equal(fieldError(column('value_snapshot_json', 'text'), '{'), undefined)
  assert.ok(fieldError(column('n', 'integer'), '1.2'))
  assert.ok(fieldError(column('d', 'decimal'), '1e4'))
})
