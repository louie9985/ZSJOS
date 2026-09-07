import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// Test-only adapter: never imported by a production entry point or connected to a database.
const query = new URLSearchParams(location.search)
export const lastCommand = ref('')
const col = (name: string, kind: string, type: string, nullable = false) => ({
  name,
  valueKind: kind,
  typeName: type,
  nullable,
  jdbcType: 12,
  columnSize: 1000,
  editable: name !== 'id',
  primaryKey: name === 'id',
  autoIncrement: name === 'id',
  generated: false,
  sensitive: false
})
const table = {
  name: 'editor_fixture',
  primaryKeyColumn: 'id',
  writable: true,
  columns: [
    col('id', 'integer', 'BIGINT'),
    col('payload', 'text', 'LONGTEXT'),
    col('flag', 'boolean', 'BIT(1)'),
    col('note', 'text', 'VARCHAR(64)', true),
    col('at_time', 'datetime', 'DATETIME(6)'),
    col('amount', 'decimal', 'DECIMAL(30,6)')
  ]
}
let row = {
  id: '9007199254740993',
  payload: '{"title":"测试","url":"https://example.invalid/old"}',
  flag: false,
  note: null,
  at_time: '2026-09-06 12:34:56.123456',
  amount: '123456789012345678.123456'
}
let fail = query.has('fail')
let loadFail = query.has('loadFail')
export const readonly = query.has('readonly')
export const getDataSourceConfigList = async () => [{ id: 1, name: 'Isolated fixture' }]
export const getTableList = async () => [table]
export const getTableDataPage = async () => {
  if (loadFail) {
    loadFail = false
    throw new Error('Fixture loading error')
  }
  return { table, total: query.has('empty') ? 0 : 1, rows: query.has('empty') ? [] : [{ ...row }] }
}
export const updateRow = async (command: { values: object }) => {
  lastCommand.value = JSON.stringify(command)
  if (fail) {
    fail = false
    ElMessage.error('字段值与现有唯一记录冲突')
    throw new Error('Fixture constraint error')
  }
  row = { ...row, ...command.values }
}
export const createRow = async (command: object) => {
  lastCommand.value = JSON.stringify(command)
}
export const deleteRow = async () => {
  throw new Error('Deletion is not part of this fixture')
}
export const useMessage = () => ({
  success: ElMessage.success,
  error: ElMessage.error,
  confirm: ElMessageBox.confirm
})
export const useI18n = () => ({
  t: (key: string) =>
    ({ 'common.updateSuccess': '修改成功', 'common.createSuccess': '新增成功' })[key] || key
})
