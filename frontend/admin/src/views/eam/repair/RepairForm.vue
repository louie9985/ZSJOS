<template>
  <Dialog v-model="dialogVisible" title="送修登记" width="600px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="资产" prop="assetId">
        <el-select
          v-model="formData.assetId"
          filterable
          remote
          clearable
          class="!w-full"
          :remote-method="searchAssets"
          :loading="assetLoading"
          placeholder="输入资产名称搜索"
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
      <el-form-item label="故障描述" prop="faultDesc">
        <el-input
          v-model="formData.faultDesc"
          type="textarea"
          :rows="3"
          placeholder="请描述故障现象"
        />
      </el-form-item>
      <el-form-item label="维修方" prop="repairVendor">
        <el-input v-model="formData.repairVendor" placeholder="如 Apple 授权服务中心" />
      </el-form-item>
      <el-form-item label="预估费用" prop="cost">
        <el-input-number
          v-model="formData.cost"
          :min="0"
          :precision="2"
          :controls="false"
          class="!w-full"
          placeholder="可留空，完成时再填"
        />
      </el-form-item>
      <el-form-item label="送修时间" prop="startTime">
        <el-date-picker
          v-model="formData.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="!w-full"
          placeholder="留空则取当前时间"
        />
      </el-form-item>
      <el-alert
        title="登记后资产状态将变更为「维修中」，维修完成时自动恢复原状态"
        type="info"
        :closable="false"
        show-icon
      />
    </el-form>

    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { getDictLabel } from '@/utils/dict'
import * as RepairApi from '@/api/eam/repair'
import * as AssetApi from '@/api/eam/asset'

defineOptions({ name: 'EamRepairForm' })

const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formData = ref<RepairApi.RepairVO>(buildEmptyForm())
const formRef = ref()

const assetOptions = ref<AssetApi.AssetVO[]>([])
const assetLoading = ref(false)

const formRules = reactive({
  assetId: [{ required: true, message: '资产不能为空', trigger: 'change' }],
  faultDesc: [{ required: true, message: '故障描述不能为空', trigger: 'blur' }]
})

function buildEmptyForm(): RepairApi.RepairVO {
  return {
    assetId: undefined as any,
    faultDesc: '',
    repairVendor: '',
    cost: undefined,
    startTime: undefined
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
    await RepairApi.createRepair(formData.value)
    message.success(t('common.createSuccess'))
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
