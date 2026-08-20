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
      <el-table-column label="使用人" prop="useUserName" min-width="100" />
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
import AssetForm from './AssetForm.vue'
import AssetImportForm from './AssetImportForm.vue'
import AssetDetail from './AssetDetail.vue'
import QrCodeDialog from './QrCodeDialog.vue'

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
  useUserId: undefined,
})

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
  await loadCategoryTree()
  await getList()
})
</script>
