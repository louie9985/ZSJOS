<template>
  <Dialog v-model="dialogVisible" title="发起资产流转" width="640px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="流转类型" prop="type">
        <el-radio-group v-model="formData.type">
          <el-radio-button
            v-for="dict in getIntDictOptions('eam_transfer_type')"
            :key="dict.value"
            :value="dict.value"
          >
            {{ dict.label }}
          </el-radio-button>
        </el-radio-group>
        <div class="mt-1 w-full text-xs text-gray-500">
          {{ needApproval ? '该类型需要审批后才会生效' : '该类型提交后立即生效' }}
        </div>
      </el-form-item>

      <el-form-item label="资产" prop="assetId">
        <el-select
          v-model="formData.assetId"
          filterable
          remote
          clearable
          class="!w-full"
          :remote-method="searchAssets"
          :loading="assetLoading"
          placeholder="输入资产编号或名称搜索"
        >
          <el-option
            v-for="item in assetOptions"
            :key="item.id"
            :label="`${item.assetCode} ${item.name}`"
            :value="item.id!"
          >
            <span>{{ item.assetCode }} {{ item.name }}</span>
            <span class="ml-2 text-xs text-gray-400">
              {{ getDictLabel('eam_asset_status', item.status) }}
            </span>
          </el-option>
        </el-select>
      </el-form-item>

      <!-- 领用/借用/调拨需要指定接收方；退还/归还只是把资产收回，不需要 -->
      <template v-if="needReceiver">
        <el-form-item label="接收人" prop="toUserId">
          <el-select
            v-model="formData.toUserId"
            filterable
            clearable
            class="!w-full"
            placeholder="请选择接收人"
          >
            <el-option
              v-for="user in userList"
              :key="user.id"
              :label="user.nickname"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="接收部门" prop="toDeptId">
          <el-tree-select
            v-model="formData.toDeptId"
            :data="deptTree"
            :props="{ label: 'name', children: 'children', value: 'id' }"
            check-strictly
            node-key="id"
            clearable
            class="!w-full"
            placeholder="请选择接收部门"
          />
        </el-form-item>
      </template>

      <el-form-item
        v-if="formData.type === TransferType.BORROW"
        label="预计归还"
        prop="expectedReturnDate"
      >
        <el-date-picker
          v-model="formData.expectedReturnDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-full"
          placeholder="请选择预计归还日期"
        />
      </el-form-item>

      <el-form-item
        v-if="formData.type === TransferType.GIVE_BACK"
        label="实际归还"
        prop="actualReturnDate"
      >
        <el-date-picker
          v-model="formData.actualReturnDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-full"
          placeholder="请选择实际归还日期"
        />
      </el-form-item>

      <el-form-item label="事由" prop="reason">
        <el-input v-model="formData.reason" type="textarea" :rows="2" placeholder="请输入事由" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { getIntDictOptions, getDictLabel } from '@/utils/dict'
import { handleTree } from '@/utils/tree'
import * as TransferApi from '@/api/eam/transfer'
import { TransferType, NEED_APPROVAL_TYPES } from '@/api/eam/transfer'
import * as AssetApi from '@/api/eam/asset'
import * as DeptApi from '@/api/system/dept'
import * as UserApi from '@/api/system/user'

defineOptions({ name: 'EamTransferForm' })

const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formData = ref<TransferApi.TransferVO>(buildEmptyForm())
const formRef = ref()

const assetOptions = ref<AssetApi.AssetVO[]>([])
const assetLoading = ref(false)
const deptTree = ref<any[]>([])
const userList = ref<any[]>([])

const needReceiver = computed(() =>
  [TransferType.RECEIVE, TransferType.BORROW, TransferType.ALLOCATE].includes(
    formData.value.type as any
  )
)
const needApproval = computed(() => NEED_APPROVAL_TYPES.includes(formData.value.type))

const formRules = computed(() => ({
  type: [{ required: true, message: '流转类型不能为空', trigger: 'change' }],
  assetId: [{ required: true, message: '资产不能为空', trigger: 'change' }],
  toUserId: needReceiver.value
    ? [{ required: true, message: '接收人不能为空', trigger: 'change' }]
    : [],
  expectedReturnDate:
    formData.value.type === TransferType.BORROW
      ? [{ required: true, message: '预计归还日期不能为空', trigger: 'change' }]
      : []
}))

function buildEmptyForm(): TransferApi.TransferVO {
  return {
    type: TransferType.RECEIVE,
    assetId: undefined as any,
    toUserId: undefined,
    toDeptId: undefined,
    expectedReturnDate: undefined,
    actualReturnDate: undefined,
    reason: ''
  }
}

const searchAssets = async (keyword: string) => {
  assetLoading.value = true
  try {
    const data = await AssetApi.getAssetPage({
      pageNo: 1,
      pageSize: 20,
      name: keyword || undefined
    })
    assetOptions.value = data.list
  } finally {
    assetLoading.value = false
  }
}

const open = async () => {
  dialogVisible.value = true
  formData.value = buildEmptyForm()
  formRef.value?.resetFields()
  await searchAssets('')
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    await TransferApi.createTransfer(formData.value)
    message.success(needApproval.value ? '已提交，等待审批' : t('common.createSuccess'))
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

onMounted(async () => {
  deptTree.value = handleTree(await DeptApi.getSimpleDeptList())
  userList.value = await UserApi.getSimpleUserList()
})
</script>
