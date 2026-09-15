<template>
  <ContentWrap>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="主体名称" prop="subjectName">
        <el-input
          v-model="queryParams.subjectName"
          placeholder="请输入主体名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-240px">
          <el-option
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openForm('create')"
          v-hasPermi="['zsjos:payment-subject:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-alert v-if="!canQuery" title="暂无支付主体查询权限" type="warning" :closable="false" />
    <el-alert v-else-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button @click="getList">重试</el-button>
    </el-alert>
    <el-table v-else v-loading="loading" :data="list" stripe>
      <el-table-column label="主体编号" align="center" prop="id" width="100" />
      <el-table-column label="主体名称" align="center" prop="subjectName" width="180" />
      <el-table-column label="商户号" align="center" prop="cusid" width="180" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="0"
            :inactive-value="1"
            @change="handleStatusChange(scope.row)"
            v-if="hasPermission(['zsjos:payment-subject:update'])"
          />
          <dict-tag v-else :type="DICT_TYPE.COMMON_STATUS" :value="scope.row.status" />
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['zsjos:payment-subject:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
            v-hasPermi="['zsjos:payment-subject:delete']"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单弹窗:添加/修改 -->
  <SubjectForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts" name="PaymentSubject">
import { getIntDictOptions, DICT_TYPE } from '@/utils/dict'
import { hasPermission } from '@/directives/permission/hasPermi'
import { dateFormatter } from '@/utils/formatTime'
import * as PaymentSubjectApi from '@/api/zsjos/payment/subject'
import SubjectForm from './SubjectForm.vue'

const message = useMessage()
const { t } = useI18n()

const loading = ref(false)
const loadError = ref('')
const canQuery = computed(() => hasPermission(['zsjos:payment-subject:query']))
const list = ref<PaymentSubjectApi.PaymentSubjectSummaryVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive<PaymentSubjectApi.PaymentSubjectPageParams>({
  pageNo: 1,
  pageSize: 10,
  subjectName: undefined,
  status: undefined
})

/** 查询列表 */
const getList = async () => {
  if (!canQuery.value) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await PaymentSubjectApi.getPaymentSubjectPage(queryParams)
    list.value = data.list
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
    loadError.value = '支付主体加载失败，请重试'
  } finally {
    loading.value = false
  }
}

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

/** 添加/修改操作 */
const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

/** 删除按钮操作 */
const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
    await PaymentSubjectApi.deletePaymentSubject(id)
    message.success(t('common.delSuccess'))
    await getList()
  } catch {}
}

/** 状态修改 */
const handleStatusChange = async (row: PaymentSubjectApi.PaymentSubjectSummaryVO) => {
  try {
    const text = row.status === 0 ? '启用' : '停用'
    await message.confirm(`确认要${text}"${row.subjectName}"吗?`, t('common.reminder'))
    await PaymentSubjectApi.updatePaymentSubjectStatus(row.id, row.status)
    message.success(text + '成功')
  } catch {
    row.status = row.status === 1 ? 0 : 1
  }
}

/** 初始化 **/
onMounted(() => {
  getList()
})
</script>
