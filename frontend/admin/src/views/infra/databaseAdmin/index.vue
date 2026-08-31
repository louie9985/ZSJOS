<template>
  <ContentWrap>
    <el-form :inline="true" :model="tableQuery" class="-mb-15px">
      <el-form-item label="数据源">
        <el-select
          v-model="tableQuery.dataSourceConfigId"
          class="!w-240px"
          placeholder="请选择数据源"
          @change="handleDataSourceChange"
        >
          <el-option
            v-for="item in dataSourceList"
            :key="item.id"
            :label="item.name"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="表名">
        <el-input
          v-model="tableQuery.name"
          class="!w-220px"
          clearable
          placeholder="请输入表名"
          @keyup.enter="getTableList"
        />
      </el-form-item>
      <el-form-item label="表注释">
        <el-input
          v-model="tableQuery.comment"
          class="!w-220px"
          clearable
          placeholder="请输入表注释"
          @keyup.enter="getTableList"
        />
      </el-form-item>
      <el-form-item>
        <el-button :loading="tableLoading" @click="getTableList">
          <Icon class="mr-5px" icon="ep:search" />
          搜索
        </el-button>
        <el-button @click="resetTableQuery">
          <Icon class="mr-5px" icon="ep:refresh" />
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <el-row :gutter="16">
    <el-col :lg="6" :md="8" :sm="24" :xs="24">
      <ContentWrap>
        <div class="database-admin__panel-title">数据表</div>
        <el-alert
          v-if="tableError"
          :closable="false"
          :title="tableError"
          class="mb-12px"
          type="error"
        />
        <el-table
          v-loading="tableLoading || dataSourceLoading"
          :data="tableList"
          height="560"
          highlight-current-row
          @row-click="selectTable"
        >
          <el-table-column label="表名" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatTableLabel(row) }}
            </template>
          </el-table-column>
          <el-table-column label="写入" width="74">
            <template #default="{ row }">
              <el-tag v-if="row.writable" type="success">可写</el-tag>
              <el-tag v-else type="info">只读</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </ContentWrap>
    </el-col>

    <el-col :lg="18" :md="16" :sm="24" :xs="24">
      <ContentWrap>
        <div class="database-admin__toolbar">
          <div>
            <div class="database-admin__title">
              {{ selectedTable ? formatTableLabel(selectedTable) : '请选择数据表' }}
            </div>
            <div v-if="selectedTable" class="database-admin__meta">
              主键：{{ selectedTable.primaryKeyColumn || '无单列主键' }}
            </div>
          </div>
          <div class="database-admin__actions">
            <el-input
              v-model="dataQuery.keyword"
              class="!w-240px"
              clearable
              placeholder="搜索文本字段"
              @keyup.enter="searchData"
            />
            <el-button :disabled="!selectedTable" :loading="dataLoading" @click="searchData">
              <Icon class="mr-5px" icon="ep:search" />
              查询
            </el-button>
            <el-button
              v-hasPermi="['infra:database-admin:create']"
              :disabled="!selectedTable?.writable"
              type="primary"
              @click="openRowDialog('create')"
            >
              <Icon class="mr-5px" icon="ep:plus" />
              新增
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="selectedTable && !selectedTable.writable"
          :closable="false"
          class="mb-12px"
          title="该表没有单列主键，仅支持查看数据和字段结构。"
          type="warning"
        />
        <el-alert
          v-if="dataError"
          :closable="false"
          :title="dataError"
          class="mb-12px"
          type="error"
        />

        <el-tabs v-model="activeTab">
          <el-tab-pane label="数据" name="data">
            <el-empty v-if="!selectedTable" description="请选择左侧数据表" />
            <template v-else>
              <el-table v-loading="dataLoading" :data="rows" border height="480">
                <el-table-column
                  v-for="column in selectedTable.columns"
                  :key="column.name"
                  :label="formatColumnLabel(column)"
                  :min-width="column.primaryKey ? 120 : 160"
                  :prop="column.name"
                  show-overflow-tooltip
                >
                  <template #default="{ row }">
                    {{ formatValue(row[column.name]) }}
                  </template>
                </el-table-column>
                <el-table-column
                  v-if="selectedTable.writable"
                  align="center"
                  fixed="right"
                  label="操作"
                  width="140"
                >
                  <template #default="{ row }">
                    <el-button
                      v-hasPermi="['infra:database-admin:update']"
                      link
                      type="primary"
                      @click="openRowDialog('update', row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      v-hasPermi="['infra:database-admin:delete']"
                      link
                      type="danger"
                      @click="handleDelete(row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
              <Pagination
                v-model:limit="dataQuery.pageSize"
                v-model:page="dataQuery.pageNo"
                :total="total"
                @pagination="getTableData"
              />
            </template>
          </el-tab-pane>
          <el-tab-pane label="字段" name="columns">
            <el-table :data="selectedTable?.columns || []" border>
              <el-table-column label="字段名" min-width="150" prop="name" show-overflow-tooltip />
              <el-table-column label="类型" min-width="120" prop="typeName" show-overflow-tooltip />
              <el-table-column label="注释" min-width="160" prop="remarks" show-overflow-tooltip />
              <el-table-column align="center" label="主键" width="70">
                <template #default="{ row }">
                  <el-tag v-if="row.primaryKey" type="success">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column align="center" label="自增" width="70">
                <template #default="{ row }">
                  <el-tag v-if="row.autoIncrement" type="info">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column align="center" label="可空" width="70">
                <template #default="{ row }">
                  <span>{{ row.nullable ? '是' : '否' }}</span>
                </template>
              </el-table-column>
              <el-table-column align="center" label="敏感" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.sensitive" type="danger">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column align="center" label="可编辑" width="86">
                <template #default="{ row }">
                  <el-tag v-if="row.editable" type="success">是</el-tag>
                  <el-tag v-else type="info">否</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </ContentWrap>
    </el-col>
  </el-row>

  <Dialog v-model="rowDialogVisible" :title="rowDialogTitle" width="720px">
    <el-form label-width="150px">
      <el-form-item
        v-for="column in editableColumns"
        :key="column.name"
        :label="formatColumnLabel(column)"
      >
        <el-input
          v-model="rowForm[column.name]"
          :placeholder="column.nullable ? '留空表示空值' : '请输入字段值'"
          clearable
        />
      </el-form-item>
    </el-form>
    <el-empty v-if="editableColumns.length === 0" description="该表没有可编辑字段" />
    <template #footer>
      <el-button
        :disabled="editableColumns.length === 0"
        :loading="rowSaving"
        type="primary"
        @click="submitRow"
      >
        确定
      </el-button>
      <el-button @click="rowDialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as DatabaseAdminApi from '@/api/infra/databaseAdmin'
import * as DataSourceConfigApi from '@/api/infra/dataSourceConfig'

defineOptions({ name: 'InfraDatabaseAdmin' })

type RowData = Record<string, unknown>
type RowFormData = Record<string, string | number | null | undefined>

const message = useMessage()
const { t } = useI18n()

const dataSourceLoading = ref(false)
const tableLoading = ref(false)
const dataLoading = ref(false)
const rowSaving = ref(false)
const tableError = ref('')
const dataError = ref('')
const dataSourceList = ref<DataSourceConfigApi.DataSourceConfigVO[]>([])
const tableList = ref<DatabaseAdminApi.DatabaseAdminTableVO[]>([])
const selectedTable = ref<DatabaseAdminApi.DatabaseAdminTableDetailVO>()
const rows = ref<RowData[]>([])
const total = ref(0)
const activeTab = ref('data')

const tableQuery = reactive<DatabaseAdminApi.DatabaseAdminTableQuery>({
  dataSourceConfigId: 0,
  name: undefined,
  comment: undefined
})

const dataQuery = reactive<DatabaseAdminApi.DatabaseAdminDataPageQuery>({
  dataSourceConfigId: 0,
  tableName: '',
  keyword: undefined,
  pageNo: 1,
  pageSize: 20
})

const rowDialogVisible = ref(false)
const rowDialogMode = ref<'create' | 'update'>('create')
const rowPrimaryKeyValue = ref<unknown>()
const rowForm = reactive<RowFormData>({})

const editableColumns = computed(
  () => selectedTable.value?.columns.filter((column) => column.editable) || []
)
const rowDialogTitle = computed(() =>
  rowDialogMode.value === 'create' ? '新增数据行' : '编辑数据行'
)

const getDataSourceList = async () => {
  dataSourceLoading.value = true
  try {
    dataSourceList.value = await DataSourceConfigApi.getDataSourceConfigList()
    if (dataSourceList.value.length > 0) {
      tableQuery.dataSourceConfigId = dataSourceList.value[0].id as number
      await getTableList()
    }
  } finally {
    dataSourceLoading.value = false
  }
}

const getTableList = async () => {
  if (tableQuery.dataSourceConfigId === undefined || tableQuery.dataSourceConfigId === null) {
    return
  }
  tableLoading.value = true
  tableError.value = ''
  try {
    tableList.value = await DatabaseAdminApi.getTableList(tableQuery)
    if (tableList.value.length === 0) {
      selectedTable.value = undefined
      rows.value = []
      total.value = 0
      return
    }
    const current = selectedTable.value
      ? tableList.value.find((table) => table.name === selectedTable.value?.name)
      : tableList.value[0]
    await selectTable(current || tableList.value[0])
  } catch (error) {
    tableError.value = '数据表加载失败，请检查数据源连接和权限后重试。'
  } finally {
    tableLoading.value = false
  }
}

const resetTableQuery = async () => {
  tableQuery.name = undefined
  tableQuery.comment = undefined
  await getTableList()
}

const handleDataSourceChange = async () => {
  selectedTable.value = undefined
  rows.value = []
  total.value = 0
  await getTableList()
}

const selectTable = async (table: DatabaseAdminApi.DatabaseAdminTableVO) => {
  dataQuery.dataSourceConfigId = tableQuery.dataSourceConfigId
  dataQuery.tableName = table.name
  dataQuery.pageNo = 1
  dataQuery.keyword = undefined
  await getTableData()
}

const searchData = async () => {
  dataQuery.pageNo = 1
  await getTableData()
}

const getTableData = async () => {
  if (!dataQuery.tableName) {
    return
  }
  dataLoading.value = true
  dataError.value = ''
  try {
    const data = await DatabaseAdminApi.getTableDataPage(dataQuery)
    selectedTable.value = data.table
    rows.value = data.rows
    total.value = data.total
  } catch (error) {
    dataError.value = '表数据加载失败，请确认表仍存在且当前账号有查询权限。'
    rows.value = []
    total.value = 0
  } finally {
    dataLoading.value = false
  }
}

const openRowDialog = (mode: 'create' | 'update', row?: RowData) => {
  rowDialogMode.value = mode
  rowPrimaryKeyValue.value = selectedTable.value?.primaryKeyColumn
    ? row?.[selectedTable.value.primaryKeyColumn]
    : undefined
  Object.keys(rowForm).forEach((key) => {
    delete rowForm[key]
  })
  editableColumns.value.forEach((column) => {
    rowForm[column.name] =
      mode === 'update' && row ? normalizeFormValue(row[column.name]) : undefined
  })
  rowDialogVisible.value = true
}

const submitRow = async () => {
  if (!selectedTable.value) {
    return
  }
  const values: RowData = {}
  editableColumns.value.forEach((column) => {
    values[column.name] =
      rowForm[column.name] === '' && column.nullable ? null : rowForm[column.name]
  })
  rowSaving.value = true
  try {
    if (rowDialogMode.value === 'create') {
      await DatabaseAdminApi.createRow({
        dataSourceConfigId: tableQuery.dataSourceConfigId,
        tableName: selectedTable.value.name,
        values
      })
    } else {
      await DatabaseAdminApi.updateRow({
        dataSourceConfigId: tableQuery.dataSourceConfigId,
        tableName: selectedTable.value.name,
        primaryKeyValue: rowPrimaryKeyValue.value,
        values
      })
    }
    message.success(
      t(rowDialogMode.value === 'create' ? 'common.createSuccess' : 'common.updateSuccess')
    )
    rowDialogVisible.value = false
    await getTableData()
  } finally {
    rowSaving.value = false
  }
}

const handleDelete = async (row: RowData) => {
  if (!selectedTable.value?.primaryKeyColumn) {
    return
  }
  const primaryKeyValue = row[selectedTable.value.primaryKeyColumn]
  try {
    await message.confirm(
      `确认删除表 ${selectedTable.value.name} 中主键为 ${formatValue(primaryKeyValue)} 的数据行吗？`
    )
    await DatabaseAdminApi.deleteRow({
      dataSourceConfigId: tableQuery.dataSourceConfigId,
      tableName: selectedTable.value.name,
      primaryKeyValue
    })
    message.success(t('common.delSuccess'))
    await getTableData()
  } catch {}
}

const formatColumnLabel = (column: DatabaseAdminApi.DatabaseAdminColumnVO) => {
  return column.remarks ? `${column.name}（${column.remarks}）` : column.name
}

const formatTableLabel = (table: DatabaseAdminApi.DatabaseAdminTableVO) => {
  return table.remarks ? `${table.name}（${table.remarks}）` : table.name
}

const formatValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

const normalizeFormValue = (value: unknown) => {
  if (value === null || value === undefined) {
    return undefined
  }
  if (typeof value === 'number') {
    return value
  }
  return formatValue(value)
}

onMounted(() => {
  getDataSourceList()
})
</script>

<style scoped>
.database-admin__panel-title {
  margin-bottom: 12px;
  font-size: 16px;
  font-weight: 600;
}

.database-admin__toolbar {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.database-admin__title {
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
}

.database-admin__meta {
  margin-top: 4px;
  line-height: 22px;
  color: var(--el-text-color-secondary);
}

.database-admin__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
</style>
