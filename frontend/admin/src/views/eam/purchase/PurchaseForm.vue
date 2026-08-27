<template>
  <Dialog v-model="visible" title="新建轻量办公采购单" width="960px">
    <el-form ref="formRef" v-loading="loading" :model="formData" label-width="100px">
      <el-alert
        v-if="paymentOptions.length === 0"
        type="warning"
        :closable="false"
        class="mb-12px"
        title="请先由管理员配置 eam_purchase_payment_mode 字典选项"
      />
      <el-alert
        v-if="sourceError"
        type="warning"
        :closable="false"
        class="mb-12px"
        :title="sourceError"
      />
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item
            label="付款方式"
            prop="paymentMode"
            :rules="[{ required: true, message: '请选择付款方式', trigger: 'change' }]"
          >
            <el-select
              v-model="formData.paymentMode"
              class="!w-full"
              placeholder="请选择管理员配置的付款方式"
            >
              <el-option
                v-for="option in paymentOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="预计到货">
            <el-date-picker
              v-model="formData.expectedArrivalDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="供应商名称"
            ><el-input v-model="formData.supplierNameSnapshot"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="供应商联系"
            ><el-input v-model="formData.supplierContactSnapshot"
          /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="预计金额">
            <el-input-number
              v-model="formData.estimatedAmount"
              :min="0"
              :precision="2"
              class="!w-full"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="票据附件"
            ><UploadFile v-model="formData.fileUrls" :limit="5"
          /></el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注"
            ><el-input v-model="formData.remark" type="textarea" :rows="2"
          /></el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">采购明细</el-divider>
      <div
        v-for="(item, index) in formData.items"
        :key="index"
        class="mb-16px border border-solid border-[var(--el-border-color)] p-16px"
      >
        <div class="mb-12px flex items-center justify-between">
          <span class="font-medium">明细 {{ index + 1 }}</span>
          <el-button
            v-if="formData.items.length > 1"
            link
            type="danger"
            @click="formData.items.splice(index, 1)"
            >删除</el-button
          >
        </div>
        <el-form-item label="来源需求">
          <el-select
            v-model="item.demandItemId"
            filterable
            clearable
            class="!w-full"
            placeholder="可选；留空表示行政补库"
            @change="applyDemandSource(item, $event)"
          >
            <el-option
              v-for="source in demandSources"
              :key="source.item.id"
              :label="source.label"
              :value="source.item.id"
            />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12">
            <el-form-item
              label="物品名称"
              :prop="`items.${index}.name`"
              :rules="[{ required: true, message: '请输入物品名称', trigger: 'blur' }]"
            >
              <el-input v-model="item.name" :disabled="!!item.demandItemId" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item
              label="资产分类"
              :prop="`items.${index}.categoryId`"
              :rules="[{ required: true, message: '请选择分类', trigger: 'change' }]"
            >
              <el-tree-select
                v-model="item.categoryId"
                :data="categoryTree"
                :props="treeProps"
                node-key="id"
                check-strictly
                filterable
                :disabled="!!item.demandItemId"
                class="!w-full"
                @change="changeCategory(item)"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item
              label="采购数量"
              :prop="`items.${index}.quantity`"
              :rules="[{ required: true, message: '请输入采购数量', trigger: 'change' }]"
            >
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :max="sourceMaximum(item)"
                :precision="0"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="单位"><el-input v-model="item.unit" disabled /></el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="预计单价"
              ><el-input-number v-model="item.unitPrice" :min="0" :precision="2" class="!w-full"
            /></el-form-item>
          </el-col>
          <el-col v-if="!item.demandItemId" :xs="24" :sm="12">
            <el-form-item label="定向员工">
              <el-select
                v-model="item.targetEmployeeId"
                filterable
                clearable
                class="!w-full"
                placeholder="留空则进入公共库存"
              >
                <el-option
                  v-for="employee in employees"
                  :key="employee.id"
                  :label="employeeLabel(employee)"
                  :value="employee.id"
                  :disabled="!employee.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <DynamicFields
          v-if="!item.demandItemId"
          :category-id="item.categoryId"
          :model-value="item.extFields || {}"
          @update:model-value="item.extFields = $event"
        />
        <el-descriptions v-else :column="2" size="small" border>
          <el-descriptions-item label="交付">{{
            item.deliveryModeLabelSnapshot
          }}</el-descriptions-item>
          <el-descriptions-item label="持有">{{
            item.custodyModeLabelSnapshot
          }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-button plain class="!w-full" @click="formData.items.push(emptyItem())">
        <Icon icon="ep:plus" class="mr-5px" />增加明细
      </el-button>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="paymentOptions.length === 0"
        @click="submit"
      >
        提交采购审批
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
import * as CategoryApi from '@/api/eam/category'
import * as EmployeeApi from '@/api/hrm/employee'
import { UploadFile } from '@/components/UploadFile'
import { getIntDictOptions } from '@/utils/dict'
import { handleTree } from '@/utils/tree'
import DynamicFields from '@/views/eam/asset/DynamicFields.vue'

defineOptions({ name: 'EamPurchaseForm' })
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const sourceError = ref('')
const formRef = ref()
const categories = ref<CategoryApi.CategoryVO[]>([])
const demands = ref<ProcurementApi.DemandVO[]>([])
const paymentOptions = getIntDictOptions('eam_purchase_payment_mode')
const treeProps = { label: 'name', children: 'children', value: 'id' }

type PurchaseEditorItem = ProcurementApi.PurchaseItemVO & { targetEmployeeId?: number }
type PurchaseEditor = Omit<ProcurementApi.PurchaseVO, 'fileUrls' | 'items'> & {
  fileUrls: string[]
  items: PurchaseEditorItem[]
}
type EmployeeOption = EmployeeApi.HrmEmployeeVO & { id: number }
type DemandSource = {
  demand: ProcurementApi.DemandVO
  item: ProcurementApi.DemandItemVO & { id: number }
  label: string
}
const employees = ref<EmployeeOption[]>([])
const emptyItem = (): PurchaseEditorItem => ({
  name: '',
  categoryId: undefined as unknown as number,
  quantity: 1,
  extFields: {}
})
const formData = reactive<PurchaseEditor>({
  paymentMode: undefined,
  supplierNameSnapshot: '',
  supplierContactSnapshot: '',
  fileUrls: [],
  remark: '',
  items: [emptyItem()]
})
const categoryTree = computed(() =>
  handleTree(
    categories.value.filter(
      (item) => item.status === 0 && item.effectiveDeliveryMode && item.effectiveCustodyMode
    ) as any,
    'id',
    'parentId'
  )
)
const demandSources = computed<DemandSource[]>(() =>
  demands.value.flatMap((demand) =>
    demand.status === 2
      ? demand.items
          .filter((item) => item.id != null && demandRemaining(item) > 0)
          .map((item) => ({
            demand,
            item: item as ProcurementApi.DemandItemVO & { id: number },
            label: `${demand.no} / ${item.name} / 缺口 ${demandRemaining(item)} ${item.unit || ''}`
          }))
      : []
  )
)

const demandRemaining = (item: ProcurementApi.DemandItemVO) =>
  item.quantity - (item.reservedQuantity || 0) - (item.purchasedQuantity || 0)
const employeeLabel = (employee: EmployeeApi.HrmEmployeeVO) =>
  `${employee.name}${employee.jobNumber ? `（${employee.jobNumber}）` : ''}${employee.userId ? '' : '（未绑定账号）'}`
const sourceMaximum = (item: PurchaseEditorItem) => {
  if (!item.demandItemId) return 999999
  const source = demandSources.value.find((row) => row.item.id === item.demandItemId)
  return source ? demandRemaining(source.item) : Math.max(1, item.quantity)
}
const applyDemandSource = (item: PurchaseEditorItem, demandItemId?: number) => {
  if (!demandItemId) {
    Object.assign(item, emptyItem())
    return
  }
  const source = demandSources.value.find((row) => row.item.id === demandItemId)
  if (!source) return
  Object.assign(item, {
    demandItemId,
    name: source.item.name,
    categoryId: source.item.categoryId,
    quantity: demandRemaining(source.item),
    unit: source.item.unit,
    extFields: { ...(source.item.extFields || {}) },
    deliveryModeLabelSnapshot: source.item.deliveryModeLabelSnapshot,
    custodyModeLabelSnapshot: source.item.custodyModeLabelSnapshot
  })
}
const changeCategory = (item: PurchaseEditorItem) => {
  const category = categories.value.find((row) => row.id === item.categoryId)
  item.unit = category?.unit
  item.extFields = {}
}

const open = async () => {
  visible.value = true
  sourceError.value = ''
  Object.assign(formData, {
    paymentMode: undefined,
    supplierNameSnapshot: '',
    supplierContactSnapshot: '',
    estimatedAmount: undefined,
    expectedArrivalDate: undefined,
    fileUrls: [],
    remark: '',
    items: [emptyItem()]
  })
  formRef.value?.clearValidate()
  loading.value = true
  try {
    const [categoryRows, employeePage] = await Promise.all([
      CategoryApi.getCategoryList(),
      EmployeeApi.getEmployeeSimplePage({ pageNo: 1, pageSize: 100 })
    ])
    categories.value = categoryRows as CategoryApi.CategoryVO[]
    employees.value = employeePage.list.filter(
      (employee) => employee.id != null
    ) as EmployeeOption[]
    try {
      demands.value = await ProcurementApi.getDemandList()
    } catch (e: any) {
      demands.value = []
      sourceError.value = e?.msg || e?.message || '需求来源加载失败；仍可创建行政补库明细'
    }
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const submit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload: ProcurementApi.PurchaseCreateReqVO = {
      paymentMode: formData.paymentMode as number,
      supplierName: formData.supplierNameSnapshot,
      supplierContact: formData.supplierContactSnapshot,
      estimatedAmount: formData.estimatedAmount,
      expectedArrivalDate: formData.expectedArrivalDate,
      fileUrls: formData.fileUrls,
      remark: formData.remark,
      items: (formData.items as PurchaseEditorItem[]).map((item) => ({
        demandItemId: item.demandItemId,
        targetEmployeeId: item.targetEmployeeId,
        name: item.name,
        categoryId: item.categoryId,
        quantity: item.quantity,
        unit: item.unit,
        unitPrice: item.unitPrice,
        extFields: item.extFields
      }))
    }
    await ProcurementApi.createPurchase(payload)
    message.success('采购单已提交统一审批中心')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
