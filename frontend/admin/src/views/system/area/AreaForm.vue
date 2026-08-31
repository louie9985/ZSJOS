<template>
  <Dialog v-model="dialogVisible" :title="formType === 'create' ? '新增地区' : '修改地区'">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="行政区编码" prop="id">
        <el-input-number
          v-model="formData.id"
          :disabled="formType === 'update'"
          :min="1"
          :controls="false"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item v-if="formType === 'update'" label="提交编码">
        <el-input v-model="formData.selectionCode" disabled />
      </el-form-item>
      <el-form-item label="上级地区" prop="parentId">
        <el-cascader
          v-model="formData.parentId"
          :options="parentOptions"
          :props="cascaderProps"
          filterable
          class="!w-100%"
          placeholder="请选择上级地区"
        />
      </el-form-item>
      <el-form-item label="地区名称" prop="name">
        <el-input v-model="formData.name" maxlength="100" placeholder="请输入地区名称" />
      </el-form-item>
      <el-form-item label="显示排序" prop="sort">
        <el-input-number
          v-model="formData.sort"
          :disabled="formData.selectionCode === 'OTHER'"
          :min="0"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio
            v-for="item in getIntDictOptions(DICT_TYPE.COMMON_STATUS)"
            :key="item.value"
            :value="item.value"
            >{{ item.label }}</el-radio
          >
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="areaType === 2" label="省级可直选" prop="leafSelectable">
        <el-switch v-model="formData.leafSelectable" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确定</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { CommonStatusEnum } from '@/utils/constants'
import { listToTree } from '@/utils/tree'
import * as AreaApi from '@/api/system/area'

defineOptions({ name: 'SystemAreaForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const parentOptions = ref<AreaApi.AreaVO[]>([])
const allAreas = ref<AreaApi.AreaVO[]>([])
const cascaderProps = {
  label: 'name',
  value: 'id',
  children: 'children',
  emitPath: false,
  checkStrictly: true
}
const formData = ref({
  id: undefined as number | undefined,
  name: '',
  selectionCode: '',
  type: undefined as number | undefined,
  parentId: 1,
  sort: 0,
  status: CommonStatusEnum.ENABLE,
  leafSelectable: false
})
const areaType = computed(() => {
  const parent = allAreas.value.find((item) => item.id === formData.value.parentId)
  return parent ? parent.type + 1 : formData.value.type
})
const formRules = reactive<FormRules>({
  id: [{ required: true, message: '行政区编码不能为空', trigger: 'blur' }],
  parentId: [{ required: true, message: '上级地区不能为空', trigger: 'change' }],
  name: [{ required: true, message: '地区名称不能为空', trigger: 'blur' }],
  sort: [{ required: true, message: '显示排序不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})

const open = async (type: 'create' | 'update', id?: number) => {
  formType.value = type
  formData.value = {
    id: undefined,
    name: '',
    selectionCode: '',
    type: undefined,
    parentId: 1,
    sort: 0,
    status: CommonStatusEnum.ENABLE,
    leafSelectable: false
  }
  dialogVisible.value = true
  formLoading.value = true
  try {
    const all = (await AreaApi.getAreaList()) as AreaApi.AreaVO[]
    allAreas.value = all
    const candidates = all.filter((item) => item.type <= 3 && item.id !== id)
    parentOptions.value = listToTree<AreaApi.AreaVO>(candidates, {
      id: 'id',
      pid: 'parentId',
      children: 'children'
    })
    if (type === 'update' && id) formData.value = await AreaApi.getArea(id)
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

watch(areaType, (type) => {
  if (type !== 2) formData.value.leafSelectable = false
})

const emit = defineEmits(['success'])
const submitForm = async () => {
  if (!(await formRef.value?.validate())) return
  formLoading.value = true
  try {
    const data = formData.value as AreaApi.AreaVO
    if (formType.value === 'create') await AreaApi.createArea(data)
    else await AreaApi.updateArea(data)
    message.success(formType.value === 'create' ? '创建成功' : '修改成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
