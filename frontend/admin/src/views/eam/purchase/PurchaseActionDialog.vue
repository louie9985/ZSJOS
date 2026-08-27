<template>
  <Dialog v-model="visible" :title="title" width="760px">
    <el-form ref="formRef" :model="formData" label-width="110px">
      <template v-if="action !== 'expense'">
        <el-form-item
          label="采购明细"
          prop="purchaseItemId"
          :rules="[{ required: true, message: '请选择采购明细', trigger: 'change' }]"
        >
          <el-select v-model="formData.purchaseItemId" class="!w-full" @change="changeItem">
            <el-option
              v-for="item in availableItems"
              :key="item.id"
              :label="`${item.name}（可处理 ${availableQuantity(item)} ${item.unit || ''}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          label="数量"
          prop="quantity"
          :rules="[{ required: true, message: '请输入数量', trigger: 'change' }]"
        >
          <el-input-number
            v-model="formData.quantity"
            :min="1"
            :max="selectedItem ? availableQuantity(selectedItem) : 1"
            :precision="0"
            class="!w-full"
          />
        </el-form-item>
      </template>

      <template v-if="action === 'receive'">
        <el-form-item label="实际单价"
          ><el-input-number v-model="formData.unitPrice" :min="0" :precision="2" class="!w-full"
        /></el-form-item>
        <el-form-item v-if="selectedItem?.managementMode === 1" label="序列号/标识">
          <el-input
            v-model="formData.serialText"
            type="textarea"
            :rows="4"
            :placeholder="'可留空；如填写，每行一个序列号且数量必须与本次入库数量一致'"
          />
        </el-form-item>
        <DynamicFields
          v-if="selectedItem"
          :category-id="selectedItem.categoryId"
          :model-value="formData.actualExtFields"
          context="collection"
          @update:model-value="formData.actualExtFields = $event"
        />
      </template>

      <template v-if="action === 'return'">
        <el-form-item v-if="selectedItem?.managementMode === 1" label="序列号/资产编号" required>
          <el-input
            v-model="formData.serialText"
            type="textarea"
            :rows="4"
            placeholder="每行一个入库序列号或系统资产编号"
          />
        </el-form-item>
        <el-form-item v-else-if="selectedItem" label="退货库存品项" required>
          <el-select
            v-model="formData.stockBalanceId"
            class="!w-full"
            placeholder="选择原入库库存品项"
          >
            <el-option
              v-for="balance in matchingBalances"
              :key="balance.id"
              :label="`${balance.name}（在库 ${balance.onHandQuantity} ${balance.unit}）`"
              :value="balance.id"
            />
          </el-select>
        </el-form-item>
      </template>

      <el-form-item
        v-if="action === 'close'"
        label="关闭原因"
        prop="reason"
        :rules="[{ required: true, message: '请填写关闭原因', trigger: 'blur' }]"
      >
        <el-input v-model="formData.reason" type="textarea" :rows="3" />
      </el-form-item>

      <template v-if="action === 'expense'">
        <el-descriptions :column="1" border class="mb-16px">
          <el-descriptions-item label="采购单">{{ purchase?.no }}</el-descriptions-item>
          <el-descriptions-item label="付款方式">{{
            purchase?.paymentModeLabelSnapshot
          }}</el-descriptions-item>
        </el-descriptions>
        <el-form-item
          label="实际金额"
          prop="actualAmount"
          :rules="[{ required: true, message: '请输入实际金额', trigger: 'change' }]"
        >
          <el-input-number
            v-model="formData.actualAmount"
            :min="0"
            :precision="2"
            class="!w-full"
          />
        </el-form-item>
      </template>

      <template v-if="action !== 'close'">
        <el-form-item label="票据/附件"
          ><UploadFile v-model="formData.fileUrls" :limit="5"
        /></el-form-item>
      </template>
      <el-form-item v-if="action === 'receive' || action === 'return'" label="异常或备注">
        <el-input v-model="formData.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as ProcurementApi from '@/api/eam/procurement'
import { UploadFile } from '@/components/UploadFile'
import DynamicFields from '@/views/eam/asset/DynamicFields.vue'

defineOptions({ name: 'EamPurchaseActionDialog' })
type ActionType = 'receive' | 'return' | 'close' | 'expense'
const emit = defineEmits(['success'])
const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const action = ref<ActionType>('receive')
const purchase = ref<ProcurementApi.PurchaseVO>()
const balances = ref<ProcurementApi.StockBalanceVO[]>([])
const formRef = ref()
const formData = reactive({
  purchaseItemId: undefined as number | undefined,
  stockBalanceId: undefined as number | undefined,
  quantity: 1,
  unitPrice: undefined as number | undefined,
  actualAmount: undefined as number | undefined,
  serialText: '',
  actualExtFields: {} as Record<string, any>,
  fileUrls: [] as string[],
  reason: '',
  remark: ''
})
const title = computed(
  () =>
    ({
      receive: '采购入库 / 数字交付',
      return: '供应商退货',
      close: '少到关闭',
      expense: '提交采购费用审批'
    })[action.value]
)
const availableQuantity = (item: ProcurementApi.PurchaseItemVO) =>
  action.value === 'receive' || action.value === 'close'
    ? item.quantity - (item.receivedQuantity || 0) - (item.shortClosedQuantity || 0)
    : (item.receivedQuantity || 0) - (item.returnedQuantity || 0)
type AvailablePurchaseItem = ProcurementApi.PurchaseItemVO & { id: number }
const availableItems = computed<AvailablePurchaseItem[]>(
  () =>
    (purchase.value?.items || []).filter(
      (item) => item.id != null && availableQuantity(item) > 0
    ) as AvailablePurchaseItem[]
)
const selectedItem = computed(() =>
  purchase.value?.items.find((item) => item.id === formData.purchaseItemId)
)
const matchingBalances = computed(() =>
  selectedItem.value
    ? balances.value.filter(
        (balance) =>
          balance.categoryId === selectedItem.value?.categoryId &&
          balance.managementMode === selectedItem.value?.managementMode &&
          balance.deliveryMode === selectedItem.value?.deliveryMode &&
          balance.custodyMode === selectedItem.value?.custodyMode &&
          balance.unit === selectedItem.value?.unit &&
          balance.onHandQuantity > 0
      )
    : []
)

const changeItem = () => {
  formData.quantity = 1
  formData.stockBalanceId = undefined
  formData.serialText = ''
  formData.unitPrice = selectedItem.value?.unitPrice
  formData.actualExtFields = { ...(selectedItem.value?.extFields || {}) }
}
const open = (
  nextAction: ActionType,
  row: ProcurementApi.PurchaseVO,
  stockRows: ProcurementApi.StockBalanceVO[] = []
) => {
  action.value = nextAction
  purchase.value = row
  balances.value = stockRows
  Object.assign(formData, {
    purchaseItemId: undefined,
    stockBalanceId: undefined,
    quantity: 1,
    unitPrice: undefined,
    actualAmount: row.actualAmount ?? row.estimatedAmount,
    serialText: '',
    actualExtFields: {},
    fileUrls: [],
    reason: '',
    remark: ''
  })
  visible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
defineExpose({ open })

const identities = () =>
  formData.serialText
    .split(/[\n,，]/)
    .map((value) => value.trim())
    .filter(Boolean)
const submit = async () => {
  await formRef.value.validate()
  if (!purchase.value?.id) return
  const item = selectedItem.value
  if (action.value !== 'expense' && !item) return
  if (action.value === 'return' && item?.managementMode === 2 && !formData.stockBalanceId) {
    message.error('请选择原入库库存品项')
    return
  }
  if (
    action.value === 'receive' &&
    item?.managementMode === 1 &&
    identities().length > 0 &&
    identities().length !== formData.quantity
  ) {
    message.error('序列号数量必须与本次入库数量一致，或全部留空')
    return
  }
  if (
    action.value === 'return' &&
    item?.managementMode === 1 &&
    identities().length !== formData.quantity
  ) {
    message.error('退货标识数量必须与退货数量一致')
    return
  }
  submitting.value = true
  try {
    if (action.value === 'receive') {
      await ProcurementApi.receivePurchase(purchase.value.id, {
        remark: formData.remark,
        fileUrls: formData.fileUrls,
        items: [
          {
            purchaseItemId: item!.id!,
            quantity: formData.quantity,
            unitPrice: formData.unitPrice,
            serialNumbers: identities(),
            actualExtFields: formData.actualExtFields
          }
        ]
      })
      message.success('本次入库/交付已记录')
    } else if (action.value === 'return') {
      await ProcurementApi.returnPurchase(purchase.value.id, {
        remark: formData.remark,
        fileUrls: formData.fileUrls,
        items: [
          {
            purchaseItemId: item!.id!,
            stockBalanceId: formData.stockBalanceId,
            quantity: formData.quantity,
            serialNumbers: identities()
          }
        ]
      })
      message.success('供应商退货已记录')
    } else if (action.value === 'close') {
      await ProcurementApi.shortClosePurchase(purchase.value.id, {
        purchaseItemId: item!.id!,
        quantity: formData.quantity,
        reason: formData.reason
      })
      message.success('少到数量已关闭')
    } else {
      await ProcurementApi.submitExpense(purchase.value.id, {
        actualAmount: formData.actualAmount!,
        fileUrls: formData.fileUrls
      })
      message.success('费用已提交统一审批中心')
    }
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}
</script>
