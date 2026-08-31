<template>
  <Dialog v-model="dialogVisible" title="申请报废" width="600px">
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
      <el-form-item label="报废原因" prop="reasonType">
        <el-select v-model="formData.reasonType" class="!w-full" placeholder="请选择报废原因">
          <el-option
            v-for="dict in getIntDictOptions('eam_scrap_reason')"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明" prop="reason">
        <el-input
          v-model="formData.reason"
          type="textarea"
          :rows="3"
          placeholder="如 主板损坏，维修成本超过残值"
        />
      </el-form-item>
      <el-form-item label="报废日期" prop="scrapDate">
        <el-date-picker
          v-model="formData.scrapDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-full"
          placeholder="留空则取当前日期"
        />
      </el-form-item>
      <el-alert
        title="提交后资产将进入「待报废」状态并冻结流转，审批通过后变为已报废终态"
        type="warning"
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
import { getIntDictOptions, getDictLabel } from '@/utils/dict'
import * as ScrapApi from '@/api/eam/scrap'
import * as AssetApi from '@/api/eam/asset'

defineOptions({ name: 'EamScrapForm' })

const emit = defineEmits(['success'])

const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formData = ref<ScrapApi.ScrapVO>(buildEmptyForm())
const formRef = ref()

const assetOptions = ref<AssetApi.AssetVO[]>([])
const assetLoading = ref(false)

const formRules = reactive({
  assetId: [{ required: true, message: '资产不能为空', trigger: 'change' }],
  reasonType: [{ required: true, message: '报废原因不能为空', trigger: 'change' }]
})

function buildEmptyForm(): ScrapApi.ScrapVO {
  return {
    assetId: undefined as any,
    reasonType: undefined as any,
    reason: '',
    scrapDate: undefined
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
    await ScrapApi.createScrap(formData.value)
    message.success('已提交报废申请')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
