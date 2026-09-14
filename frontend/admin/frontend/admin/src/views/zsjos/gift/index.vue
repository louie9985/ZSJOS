<template>
  <ContentWrap v-loading="loading">
    <div class="mb-16px"><el-button v-hasPermi="['zsjos:gift-config:create']" type="primary" @click="openForm()">新增礼品节点</el-button><el-button @click="load">刷新</el-button></div>
    <el-table :data="rows" row-key="id" default-expand-all border>
      <el-table-column prop="name" label="名称" min-width="220" />
      <el-table-column prop="code" label="编码" min-width="180" />
      <el-table-column prop="parentId" label="父节点" width="100" />
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="180" fixed="right"><template #default="{ row }"><el-button v-hasPermi="['zsjos:gift-config:update']" link type="primary" @click="openForm(row)">编辑</el-button><el-button v-hasPermi="['zsjos:gift-config:delete']" link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑礼品节点' : '新增礼品节点'" width="500px"><el-form ref="formRef" :model="form" label-width="90px"><el-form-item label="父节点"><el-input-number v-model="form.parentId" :min="0" /></el-form-item><el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item><el-form-item label="编码" required><el-input v-model="form.code" maxlength="128" /></el-form-item><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item><el-form-item label="排序"><el-input-number v-model="form.sort" :min="0" /></el-form-item></el-form><template #footer><el-button @click="dialogVisible=false">取消</el-button><el-button type="primary" @click="submit">保存</el-button></template></el-dialog>
  </ContentWrap>
</template>
<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import * as GiftApi from '@/api/zsjos/gift'
const loading = ref(false); const dialogVisible = ref(false); const rows = ref<GiftApi.GiftConfigVO[]>([])
const form = reactive<GiftApi.GiftConfigSaveReqVO>({ parentId: 0, name: '', code: '', status: 1, sort: 0 })
const load = async () => { loading.value = true; try { rows.value = await GiftApi.getGiftConfigList() as GiftApi.GiftConfigVO[] } finally { loading.value = false } }
const openForm = (row?: GiftApi.GiftConfigVO) => { Object.assign(form, row || { id: undefined, parentId: 0, name: '', code: '', status: 1, sort: 0 }); dialogVisible.value = true }
const submit = async () => { if (!form.name || !form.code) return ElMessage.warning('请填写名称和编码'); if (form.id) await GiftApi.updateGiftConfig(form); else await GiftApi.createGiftConfig(form); ElMessage.success('保存成功'); dialogVisible.value = false; await load() }
const remove = async (row: GiftApi.GiftConfigVO) => { await ElMessageBox.confirm(`确认删除礼品节点「${row.name}」？`); await GiftApi.deleteGiftConfig(row.id); ElMessage.success('删除成功'); await load() }
onMounted(load)
</script>
