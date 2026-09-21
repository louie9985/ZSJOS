<template>
  <ContentWrap>
    <el-alert
      title="未配置按学校；多产品主体一致按配置；主体不一致统一按公司。仅对新生成的支付链接生效。"
      type="info"
      show-icon
      :closable="false"
      class="mb-15px"
    />
    <el-alert v-if="subjectError" :title="subjectError" type="error" :closable="false">
      <el-button @click="getSubjectList">重试</el-button>
    </el-alert>
    <!-- 搜索工作栏 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="68px"
    >
      <el-form-item label="产品名称" prop="productName">
        <el-input
          v-model="queryParams.productName"
          placeholder="请输入产品名称"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="支付主体" prop="paymentSubjectId">
        <el-select
          v-model="queryParams.paymentSubjectId"
          placeholder="请选择支付主体"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="item in subjectList"
            :key="item.id"
            :label="item.subjectName"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="handleBatchConfig"
          v-hasPermi="['zsjos:product-payment-subject:configure']"
          :disabled="selectedRows.length === 0"
        >
          <Icon icon="ep:setting" class="mr-5px" /> 批量配置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-alert v-if="!canQuery" title="暂无产品支付配置查询权限" type="warning" :closable="false" />
    <el-alert v-else-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button @click="getList">重试</el-button>
    </el-alert>
    <el-table
      v-else
      v-loading="loading"
      :data="list"
      stripe
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="产品编号" align="center" prop="productId" width="100" />
      <el-table-column label="产品名称" align="center" prop="productName" min-width="200" />
      <el-table-column label="已配置支付主体" align="center" prop="subjectName" min-width="240">
        <template #default="scope">
          <el-tag v-if="scope.row.paymentSubjectId == null" type="info">
            未配置（按规则使用学校）
          </el-tag>
          <el-tag v-else-if="scope.row.subjectName" type="success">{{ scope.row.subjectName }}</el-tag>
          <el-tag v-else type="danger">关联主体异常</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="配置时间"
        align="center"
        prop="configTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" width="120" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="handleConfig(scope.row)"
            v-hasPermi="['zsjos:product-payment-subject:configure']"
          >
            配置主体
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

  <!-- 配置弹窗 -->
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="500px">
    <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="100px">
      <el-form-item label="支付主体" prop="paymentSubjectId">
        <el-select
          v-model="configForm.paymentSubjectId"
          placeholder="请选择支付主体"
          style="width: 100%"
        >
          <el-option
            v-for="item in activeSubjectList"
            :key="item.id"
            :label="item.subjectName"
            :value="item.id"
          >
            <span style="float: left">{{ item.subjectName }}</span>
            <span style="float: right; color: var(--el-text-color-secondary); font-size: 13px">
              {{ item.subjectCode }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-alert
        v-if="isBatchConfig"
        :title="`将为 ${selectedRows.length} 个产品配置支付主体`"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      />
    </el-form>
    <template #footer>
      <el-button
        @click="submitConfig"
        type="primary"
        :loading="configLoading"
        :disabled="subjectLoading || !!subjectError"
        >确 定</el-button
      >
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts" name="ProductPaymentSubject">
import { dateFormatter } from '@/utils/formatTime'
import { hasPermission } from '@/directives/permission/hasPermi'
import * as ProductPaymentSubjectApi from '@/api/zsjos/payment/productSubject'
import * as PaymentSubjectApi from '@/api/zsjos/payment/subject'

const message = useMessage()

const loading = ref(false)
const loadError = ref('')
const subjectError = ref('')
const subjectLoading = ref(false)
const canQuery = computed(() => hasPermission(['zsjos:product-payment-subject:query']))
const list = ref<ProductPaymentSubjectApi.ProductPaymentSubjectVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive<ProductPaymentSubjectApi.ProductPaymentSubjectPageParams>({
  pageNo: 1,
  pageSize: 10,
  productName: undefined,
  paymentSubjectId: undefined
})

// 支付主体列表
const subjectList = ref<PaymentSubjectApi.PaymentSubjectSummaryVO[]>([])
const activeSubjectList = computed(() => subjectList.value.filter((item) => item.status === 0))

// 选中的行
const selectedRows = ref<ProductPaymentSubjectApi.ProductPaymentSubjectVO[]>([])
const handleSelectionChange = (selection: ProductPaymentSubjectApi.ProductPaymentSubjectVO[]) => {
  selectedRows.value = selection
}

/** 查询支付主体列表 */
const getSubjectList = async () => {
  if (
    !hasPermission([
      'zsjos:payment-subject:query',
      'zsjos:product-payment-subject:query',
      'zsjos:product-payment-subject:configure'
    ])
  )
    return
  subjectLoading.value = true
  subjectError.value = ''
  try {
    subjectList.value = await PaymentSubjectApi.getPaymentSubjectSimpleList()
  } catch {
    subjectList.value = []
    subjectError.value = '支付主体选项加载失败，请重试'
  } finally {
    subjectLoading.value = false
  }
}

/** 查询列表 */
const getList = async () => {
  if (!canQuery.value) return
  loading.value = true
  loadError.value = ''
  selectedRows.value = []
  try {
    const data = await ProductPaymentSubjectApi.getProductPaymentSubjectPage(queryParams)
    list.value = data.list
    total.value = data.total
  } catch {
    list.value = []
    total.value = 0
    loadError.value = '产品支付配置加载失败，请重试'
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

// 配置相关
const dialogVisible = ref(false)
const dialogTitle = ref('')
const configLoading = ref(false)
const isBatchConfig = ref(false)
const configFormRef = ref()
const configForm = reactive<{ productIds: number[]; paymentSubjectId?: number }>({
  productIds: [],
  paymentSubjectId: undefined
})
const configRules = reactive({
  paymentSubjectId: [{ required: true, message: '请选择支付主体', trigger: 'change' }]
})

/** 单个配置 */
const handleConfig = (row: ProductPaymentSubjectApi.ProductPaymentSubjectVO) => {
  dialogVisible.value = true
  dialogTitle.value = '配置支付主体'
  isBatchConfig.value = false
  configForm.productIds = [row.productId]
  configForm.paymentSubjectId = row.paymentSubjectId
}

/** 批量配置 */
const handleBatchConfig = () => {
  if (selectedRows.value.length === 0) {
    message.warning('请选择要配置的产品')
    return
  }
  dialogVisible.value = true
  dialogTitle.value = '批量配置支付主体'
  isBatchConfig.value = true
  configForm.productIds = selectedRows.value.map((item) => item.productId)
  configForm.paymentSubjectId = undefined
}

/** 提交配置 */
const submitConfig = async () => {
  await configFormRef.value.validate()
  if (configForm.paymentSubjectId === undefined || subjectLoading.value || subjectError.value)
    return
  configLoading.value = true
  if (!activeSubjectList.value.some((item) => item.id === configForm.paymentSubjectId)) {
    configLoading.value = false
    message.warning('请选择启用的支付主体')
    return
  }
  try {
    await ProductPaymentSubjectApi.configProductPaymentSubject({
      productIds: configForm.productIds,
      paymentSubjectId: configForm.paymentSubjectId
    })
    message.success('配置成功')
    dialogVisible.value = false
    await getList()
  } finally {
    configLoading.value = false
  }
}

/** 初始化 **/
onMounted(async () => {
  await Promise.all([getSubjectList(), getList()])
})
</script>
