<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="80px"
    >
      <el-form-item label="资产名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入资产名称"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="资产编号" prop="assetCode">
        <el-input
          v-model="queryParams.assetCode"
          placeholder="请输入资产编号"
          clearable
          class="!w-200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="分类" prop="categoryId">
        <el-tree-select
          v-model="queryParams.categoryId"
          :data="categoryTree"
          :props="treeSelectProps"
          check-strictly
          node-key="id"
          clearable
          class="!w-200px"
          placeholder="请选择分类"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-200px">
          <el-option
            v-for="dict in getIntDictOptions('eam_asset_status')"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="字段筛选">
        <el-select v-model="queryParams.extFieldKey" clearable class="!w-180px" @change="queryParams.extFieldValue = undefined">
          <el-option v-for="field in filterFields" :key="field.fieldKey" :label="field.fieldName" :value="field.fieldKey" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedFilterField" :label="selectedFilterField.fieldName" prop="extFieldValue">
        <el-select v-if="selectedFilterField.fieldType === 5" v-model="queryParams.extFieldValue" clearable class="!w-180px">
          <el-option v-for="item in filterOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-else v-model="queryParams.extFieldValue" clearable class="!w-180px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button v-hasPermi="['eam:asset:create']" type="primary" @click="openForm('create')">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button v-hasPermi="['eam:asset:import']" type="warning" plain @click="openImport">
          <Icon icon="ep:upload" class="mr-5px" /> 导入
        </el-button>
        <el-button
          v-hasPermi="['eam:asset:export']"
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
        <el-button v-hasPermi="['eam:asset:public-edit-code']" plain @click="showMyCode">我的公开编辑口令</el-button>
        <el-button v-hasPermi="['eam:asset:public-edit-code']" plain @click="updateMyCode">修改口令</el-button>
        <el-button v-hasPermi="['eam:asset:public-edit-code']" plain @click="resetMyCode">重新生成口令</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="资产编号" prop="assetCode" min-width="140" fixed="left" />
      <el-table-column label="资产名称" prop="name" min-width="160" show-overflow-tooltip />
      <el-table-column label="分类" prop="categoryName" min-width="110" />
      <el-table-column label="管理" width="80" align="center">
        <template #default="{ row }">{{ row.managementMode === 2 ? '批量' : '单件' }}</template>
      </el-table-column>
      <el-table-column label="数量" min-width="90" align="center">
        <template #default="{ row }">{{ row.quantity || 1 }} {{ row.unit || '个' }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="90" align="center">
        <template #default="{ row }">
          <dict-tag :type="'eam_asset_status'" :value="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="品牌型号" prop="brand" min-width="130" show-overflow-tooltip />
      <el-table-column label="使用员工" prop="useEmployeeName" min-width="100" />
      <el-table-column label="使用部门" prop="useDeptName" min-width="120" />
      <el-table-column label="存放地点" prop="location" min-width="140" show-overflow-tooltip />
      <el-table-column
        label="购入日期"
        prop="purchaseDate"
        min-width="110"
        :formatter="dateFormatter2"
      />
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['eam:asset:query']"
            link
            type="primary"
            @click="openDetail(row.id)"
          >
            详情
          </el-button>
          <el-button
            v-hasPermi="['eam:asset:update']"
            link
            type="primary"
            @click="openForm('update', row.id)"
          >
            编辑
          </el-button>
          <el-button v-hasPermi="['eam:asset:qrcode']" link type="primary" @click="openQrCode(row)">
            二维码
          </el-button>
          <el-button
            v-hasPermi="['eam:asset:delete']"
            link
            type="danger"
            @click="handleDelete(row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <AssetForm ref="formRef" :category-tree="categoryTree" @success="getList" />
  <AssetImportForm ref="importRef" @success="handleImportSuccess" />
  <AssetDetail ref="detailRef" />
  <QrCodeDialog ref="qrCodeRef" />
</template>

<script setup lang="ts">
import { dateFormatter2 } from '@/utils/formatTime'
import { getIntDictOptions } from '@/utils/dict'
import { handleTree } from '@/utils/tree'
import download from '@/utils/download'
import * as AssetApi from '@/api/eam/asset'
import * as CategoryApi from '@/api/eam/category'
import * as CategoryFieldApi from '@/api/eam/categoryField'
import { getStrDictOptions } from '@/utils/dict'
import AssetForm from './AssetForm.vue'
import AssetImportForm from './AssetImportForm.vue'
import AssetDetail from './AssetDetail.vue'
import QrCodeDialog from './QrCodeDialog.vue'
import { ElMessageBox } from 'element-plus'

defineOptions({ name: 'EamAsset' })

const message = useMessage()
const { t } = useI18n()
const treeSelectProps: any = { label: 'name', children: 'children', value: 'id' }

const loading = ref(false)
const exportLoading = ref(false)
const total = ref(0)
const list = ref<AssetApi.AssetVO[]>([])
const categoryTree = ref<any[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: undefined,
  assetCode: undefined,
  categoryId: undefined,
  status: undefined,
  useDeptId: undefined,
  useEmployeeId: undefined,
  extFieldKey: undefined,
  extFieldValue: undefined,
})
const filterFields = ref<CategoryFieldApi.CategoryFieldVO[]>([])
const selectedFilterField = computed(() => filterFields.value.find((field) => field.fieldKey === queryParams.extFieldKey))
const filterOptions = computed(() => {
  const field = selectedFilterField.value
  if (!field) return []
  return field.optionSource === 'SYSTEM_DICT' && field.dictType
    ? getStrDictOptions(field.dictType)
    : (field.options || []).map((value) => ({ label: value, value }))
})

const showMyCode = async () => {
  try {
    const data = await AssetApi.getMyPublicEditCode()
    await message.alert(`您的公开编辑口令：${data.code}`)
  } catch { /* request layer displays the reason */ }
}

const resetMyCode = async () => {
  try {
    await message.confirm('重新生成后，旧口令将立即失效，是否继续？')
    const data = await AssetApi.generateMyPublicEditCode()
    await message.alert(`新的公开编辑口令：${data.code}`)
  } catch { /* cancel and request errors are intentionally silent */ }
}

const updateMyCode = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入 6 位大写英数字，不支持 I、O、0、1',
      '修改公开编辑口令',
      {
        inputPattern: /^[A-HJ-NP-Z2-9]{6}$/,
        inputErrorMessage: '请输入有效的 6 位口令',
        inputPlaceholder: '例如 A2BC3D',
        confirmButtonText: '保存',
        cancelButtonText: '取消'
      }
    )
    const data = await AssetApi.updateMyPublicEditCode(value)
    await message.alert(`公开编辑口令已修改为：${data.code}`)
  } catch { /* cancel and request errors are intentionally silent */ }
}

const getList = async () => {
  loading.value = true
  try {
    const data = await AssetApi.getAssetPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const loadCategoryTree = async () => {
  const categories = await CategoryApi.getCategoryList()
  categoryTree.value = handleTree(categories as any, 'id', 'parentId')
}

watch(() => queryParams.categoryId, async (categoryId) => {
  filterFields.value = categoryId ? await CategoryFieldApi.getEffectiveFieldList(categoryId) : []
  if (!filterFields.value.some((field) => field.fieldKey === queryParams.extFieldKey)) {
    queryParams.extFieldKey = undefined
    queryParams.extFieldValue = undefined
  }
})

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const detailRef = ref()
const openDetail = (id: number) => {
  detailRef.value.open(id)
}

const importRef = ref()
const openImport = () => {
  importRef.value.open()
}

const handleImportSuccess = async () => {
  await loadCategoryTree()
  await getList()
}

const qrCodeRef = ref()
const openQrCode = (row: AssetApi.AssetVO) => {
  qrCodeRef.value.open(row)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await AssetApi.deleteAsset(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await AssetApi.exportAsset(queryParams)
    download.excel(data, '资产台账.xlsx')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  // 分类树只用于筛选，和资产列表没有依赖关系；并行加载避免首屏被分类接口串行阻塞。
  await Promise.all([loadCategoryTree(), getList()])
})
</script>
