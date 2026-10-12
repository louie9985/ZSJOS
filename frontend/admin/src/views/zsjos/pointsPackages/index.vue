<template>
  <ContentWrap>
    <el-alert title="每次申请生成一个兑换码。套餐修改只影响新申请。" type="info" :closable="false" />
    <el-button v-hasPermi="['zsjos:points-redemption:package-save']" type="primary" @click="edit()">新增套餐</el-button>
    <el-button @click="load">刷新</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="rows">
      <el-table-column prop="name" label="套餐名称" />
      <el-table-column prop="creditsAmount" label="积分" />
      <el-table-column label="有效期"><template #default="{ row }">{{ row.validDays ? `${row.validDays}天` : '永久有效' }}</template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button v-hasPermi="['zsjos:points-redemption:package-save']" link type="primary" @click="edit(row)">编辑</el-button></template></el-table-column>
    </el-table>
  </ContentWrap>
  <Dialog v-model="open" title="积分套餐">
    <el-form ref="form" :model="values" label-width="100px" :rules="rules">
      <el-form-item label="套餐名称" prop="name"><el-input v-model="values.name" maxlength="100" /></el-form-item>
      <el-form-item label="兑换积分" prop="creditsAmount"><el-input-number v-model="values.creditsAmount" :min="1" :max="1000000" :precision="0" /></el-form-item>
      <el-form-item label="有效天数"><el-input-number v-model="values.validDays" :min="1" :max="36500" :precision="0" /><span>留空表示永久有效</span></el-form-item>
      <el-form-item label="启用"><el-switch v-model="values.enabled" /></el-form-item>
    </el-form>
    <template #footer><el-button :loading="saving" type="primary" @click="save">保存</el-button><el-button @click="open = false">取消</el-button></template>
  </Dialog>
</template>
<script setup lang="ts">
import * as Points from '@/api/zsjos/pointsRedemption'
defineOptions({ name: 'ZsjosPointsPackages' })
const rows = ref<Points.PointsPackage[]>([]), loading = ref(false), saving = ref(false), error = ref(''), open = ref(false)
const values = ref<Points.PointsPackage>({ name: '', creditsAmount: 1, enabled: true }), form = ref()
const rules = { name: [{ required: true, whitespace: true, message: '请输入名称' }], creditsAmount: [{ required: true, message: '请输入积分' }] }
const load = async () => { loading.value = true; error.value = ''; try { rows.value = await Points.list() } catch (e) { error.value = e instanceof Error ? e.message : '套餐加载失败' } finally { loading.value = false } }
const edit = (row?: Points.PointsPackage) => { values.value = row ? { ...row } : { name: '', creditsAmount: 1, enabled: true }; open.value = true }
const save = async () => { await form.value.validate(); saving.value = true; try { await Points.save({ ...values.value, validDays: values.value.validDays || null }); open.value = false; await load() } finally { saving.value = false } }
onMounted(load)
</script>
