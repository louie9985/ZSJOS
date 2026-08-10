<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
      <el-form-item label="地区名称" prop="name">
        <el-input
          v-model="queryParams.name"
          clearable
          placeholder="请输入地区名称"
          class="!w-240px"
          @keyup.enter="getList"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="请选择状态" class="!w-200px">
          <el-option
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="getList"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['system:area:create']"
          @click="openForm('create')"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增地区
        </el-button>
        <el-button plain @click="ipQueryRef.open()"
          ><Icon icon="ep:location" class="mr-5px" />IP 查询</el-button
        >
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-alert
      v-if="loadError"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
      :title="loadError"
    >
      <template #default><el-button link type="primary" @click="getList">重试</el-button></template>
    </el-alert>
    <el-empty v-if="!loading && !loadError && list.length === 0" description="暂无地区数据" />
    <el-table v-else v-loading="loading" :data="list" row-key="id" :default-expand-all="false">
      <el-table-column prop="name" label="地区名称" min-width="240" />
      <el-table-column prop="id" label="行政区编码" width="130" />
      <el-table-column prop="selectionCode" label="提交编码" width="120" />
      <el-table-column prop="type" label="层级" width="90">
        <template #default="scope">{{ typeLabels[scope.row.type] || scope.row.type }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column prop="leafSelectable" label="省级直选" width="100">
        <template #default="scope">{{ scope.row.leafSelectable ? '是' : '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="scope"
          ><dict-tag :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status"
        /></template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['system:area:update']"
            @click="openForm('update', scope.row.id)"
            >修改</el-button
          >
          <el-switch
            v-model="scope.row.status"
            :active-value="CommonStatusEnum.ENABLE"
            :inactive-value="CommonStatusEnum.DISABLE"
            v-hasPermi="['system:area:update']"
            @change="(status) => changeStatus(scope.row, Number(status))"
          />
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <AreaForm ref="formRef" @success="getList" />
  <AreaIpQueryDialog ref="ipQueryRef" />
</template>

<script lang="ts" setup>
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import { listToTree } from '@/utils/tree'
import * as AreaApi from '@/api/system/area'
import AreaForm from './AreaForm.vue'
import AreaIpQueryDialog from './AreaIpQueryDialog.vue'

defineOptions({ name: 'SystemArea' })

const message = useMessage()
const loading = ref(false)
const loadError = ref('')
const list = ref<AreaApi.AreaVO[]>([])
const queryFormRef = ref()
const queryParams = reactive<{ name?: string; status?: number }>({
  name: undefined,
  status: undefined
})
const typeLabels: Record<number, string> = { 1: '国家', 2: '省份', 3: '城市', 4: '区县' }
const formRef = ref()
const ipQueryRef = ref()

const getList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const data = (await AreaApi.getAreaList(queryParams)) as AreaApi.AreaVO[]
    const tree = listToTree<AreaApi.AreaVO>(data, {
      id: 'id',
      pid: 'parentId',
      children: 'children'
    })
    const china = data.find((item) => item.id === 1)
    list.value = queryParams.name || queryParams.status !== undefined ? tree : china?.children || []
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '地区数据加载失败'
    list.value = []
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  getList()
}

const openForm = (type: 'create' | 'update', id?: number) => formRef.value.open(type, id)

const changeStatus = async (row: AreaApi.AreaVO, status: number) => {
  const previous =
    status === CommonStatusEnum.ENABLE ? CommonStatusEnum.DISABLE : CommonStatusEnum.ENABLE
  try {
    await AreaApi.updateAreaStatus(row.id, status)
    message.success('状态已更新')
    await getList()
  } catch {
    row.status = previous
  }
}

onMounted(getList)
</script>
