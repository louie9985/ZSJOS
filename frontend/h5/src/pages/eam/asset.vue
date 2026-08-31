<template>
  <div class="asset-page">
    <van-nav-bar title="资产台账" />
    <van-loading v-if="loading" class="state" />
    <van-empty v-else-if="error" description="链接无效或资产不存在" />
    <template v-else-if="asset">
      <van-cell-group inset title="资产信息">
        <van-cell v-for="field in asset.fields" :key="field.key" :title="field.label" :value="String(field.value ?? '-')" />
      </van-cell-group>
      <van-cell-group inset title="编辑资产" class="edit-box">
        <van-field v-model="code" label="个人口令" maxlength="6" placeholder="请输入 6 位口令" :disabled="editing" />
        <van-button v-if="!editing" block type="primary" :loading="saving" @click="unlock">验证口令并编辑</van-button>
        <template v-else>
          <van-field
            v-for="field in editableFields"
            :key="field.key"
            :model-value="field.type === 'select' ? displayForm[field.key] : form[field.key]"
            :label="field.label"
            :type="field.type === 'textarea' ? 'textarea' : field.type === 'number' ? 'number' : field.type === 'date' ? 'date' : 'text'"
            :readonly="field.type === 'select'"
            :is-link="field.type === 'select'"
            @update:model-value="onFieldInput(field, $event)"
            @click="field.type === 'select' && openPicker(field)"
          />
          <van-button block type="primary" :loading="saving" @click="save">保存修改</van-button>
          <van-button block plain type="danger" :loading="clearing" @click="clearUsage">
            清除使用人和部门并置为闲置
          </van-button>
        </template>
      </van-cell-group>
      <van-popup v-model:show="pickerVisible" position="bottom">
        <van-picker
          v-if="isTreePicker"
          v-model="pickerValues"
          :columns="treePickerOptions"
          :columns-field-names="treeFieldNames"
          @confirm="onPick"
          @cancel="pickerVisible = false"
        />
        <van-picker
          v-else
          v-model="pickerValues"
          :columns="pickerOptions"
          @confirm="onPick"
          @cancel="pickerVisible = false"
        />
      </van-popup>
      <van-cell-group inset title="现有附件" v-if="asset.fileUrls?.length">
        <van-cell v-for="(url, index) in asset.fileUrls" :key="url" :title="`附件 ${index + 1}`" is-link :url="url" />
      </van-cell-group>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { useRoute } from 'vue-router'
import {
  clearPublicAssetUsage,
  getPublicAsset,
  updatePublicAsset,
  verifyPublicEditCode,
  type OptionValue,
  type PublicAsset,
  type PublicField,
  type TreeOption
} from '@/api/eamPublic'
const route = useRoute(); const assetCode = String(route.query.assetCode || '')
const asset = ref<PublicAsset>(); const loading = ref(true); const error = ref(false); const code = ref(''); const editing = ref(false); const saving = ref(false); const clearing = ref(false)
const form = reactive<Record<string, any>>({}); const displayForm = reactive<Record<string, any>>({})
const pickerVisible = ref(false); const pickerField = ref<PublicField>(); const pickerOptions = ref<{ text: string; value: OptionValue }[]>([])
const pickerValues = ref<OptionValue[]>([])
const treeFieldNames = { text: 'label', value: 'value', children: 'children' }
const editableFields = computed(() => asset.value?.fields.filter((field) => field.editable) || [])
const isTreePicker = computed(() => pickerField.value?.key === 'categoryId' || pickerField.value?.key === 'useDeptId')
const treePickerOptions = computed(() => pickerField.value?.key === 'categoryId'
  ? asset.value?.categoryTree || []
  : asset.value?.departmentTree || [])
const employeeOptions = computed(() => {
  const options = asset.value?.employeeOptions || []
  const deptId = form.useDeptId
  return deptId == null ? options : options.filter((item) => item.deptId === deptId)
})
async function load() { loading.value = true; error.value = false; document.title = '中世健资产管理'; try { if (!assetCode) throw new Error('missing asset code'); asset.value = await getPublicAsset(assetCode); Object.assign(form, asset.value.editFields); const ext = asset.value.editFields.extFields || {}; asset.value.fields.filter((field) => field.key.startsWith('ext.')).forEach((field) => { form[field.key] = ext[field.key.slice(4)] }); asset.value.fields.forEach((field) => { displayForm[field.key] = field.value }) } catch { error.value = true } finally { loading.value = false } }
function findTreePath(nodes: TreeOption[], value: unknown, parents: OptionValue[] = []): OptionValue[] {
  for (const node of nodes) {
    const path = [...parents, node.value]
    if (node.value === value) return path
    const childPath = findTreePath(node.children || [], value, path)
    if (childPath.length) return childPath
  }
  return []
}
function openPicker(field: PublicField) {
  pickerField.value = field
  if (field.key === 'categoryId') {
    pickerValues.value = findTreePath(asset.value?.categoryTree || [], form.categoryId)
  } else if (field.key === 'useDeptId') {
    pickerValues.value = findTreePath(asset.value?.departmentTree || [], form.useDeptId)
  } else {
    const options = field.key === 'useEmployeeId' ? employeeOptions.value : field.options
    if (!options.length) {
      showToast(field.key === 'useEmployeeId' && form.useDeptId ? '该部门暂无可选员工' : '暂无可选项')
      return
    }
    pickerOptions.value = options.map((item) => ({ text: item.label, value: item.value }))
    pickerValues.value = form[field.key] == null ? [] : [form[field.key]]
  }
  pickerVisible.value = true
}
function onFieldInput(field: PublicField, value: unknown) {
  if (field.type !== 'select') form[field.key] = value
}
function onPick({ selectedOptions }: { selectedOptions: Array<Record<string, any> | undefined> }) {
  const options = selectedOptions.filter(Boolean) as Array<Record<string, any>>
  const option = options[options.length - 1]
  if (!pickerField.value || !option) return
  const fieldKey = pickerField.value.key
  form[fieldKey] = option.value
  displayForm[fieldKey] = isTreePicker.value
    ? options.map((item) => item.label).join(' / ')
    : option.text
  if (fieldKey === 'useDeptId' && form.useEmployeeId != null
      && !employeeOptions.value.some((item) => item.value === form.useEmployeeId)) {
    form.useEmployeeId = undefined
    displayForm.useEmployeeId = ''
  }
  pickerVisible.value = false
}
async function unlock() { if (!/^[A-HJ-NP-Z2-9]{6}$/.test(code.value)) { showToast('请输入 6 位大写英数字口令'); return }; saving.value = true; try { await verifyPublicEditCode(assetCode, code.value); editing.value = true; showToast('验证成功，请修改资产资料') } catch { showToast('口令错误、已锁定或无权编辑') } finally { saving.value = false } }
function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
async function save() {
  saving.value = true
  try {
    const payload = Object.fromEntries(Object.entries(form)
      .filter(([key]) => key !== 'extFields' && !key.startsWith('ext.')))
    payload.extFields = Object.fromEntries(Object.entries(form)
      .filter(([key]) => key.startsWith('ext.'))
      .map(([key, value]) => [key.slice(4), value]))
    await updatePublicAsset(assetCode, payload, code.value)
    showToast('保存成功')
    editing.value = false
    await load()
  } catch (error) {
    showToast(errorMessage(error, '保存失败，可能存在版本冲突，请重新加载后重试'))
  } finally {
    saving.value = false
  }
}
async function clearUsage() {
  if (!asset.value) return
  try {
    await showConfirmDialog({
      title: '确认置为闲置',
      message: '将清除当前使用人和使用部门。存在未完成领用或退还记录时，系统会拒绝本次操作。',
      confirmButtonText: '确认置闲'
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await clearPublicAssetUsage(assetCode, asset.value.version, code.value)
    showToast('已清除使用归属并置为闲置')
    editing.value = false
    await load()
  } catch (error) {
    showToast(errorMessage(error, '置闲失败，请刷新后重试'))
  } finally {
    clearing.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.asset-page { min-height: 100vh; background: #f7f8fa; padding-bottom: 24px; }
.state { padding: 48px 0; text-align: center; }
.edit-box { margin-top: 16px; padding-bottom: 12px; }
.edit-box .van-button { margin: 12px auto 0; width: calc(100% - 32px); }
</style>
