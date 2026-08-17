<template>
  <ContentWrap>
    <div class="toolbar"><div><h3>履约清单配置</h3><span>发布后仅新建报名任务使用新版本</span></div><div><el-button :disabled="!!config?.draft" @click="copy">复制已发布版本</el-button><el-button type="primary" :disabled="!config?.draft" :loading="saving" @click="save">保存草稿</el-button><el-button :disabled="!config?.draft" @click="publish">发布</el-button></div></div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon><template #default><el-button link type="primary" @click="load">重试</el-button></template></el-alert>
    <el-empty v-else-if="!config?.draft" description="暂无草稿，请先复制已发布版本" />
    <el-table v-else :data="config.draft.items" row-key="id">
      <el-table-column prop="sort" label="顺序" width="90" />
      <el-table-column label="事项" min-width="360"><template #default="{ row }"><el-input v-model="row.title" :disabled="row.itemType === 'study_planner'" /><el-tag v-if="row.itemType === 'study_planner'" size="small" type="info">系统固定项</el-tag></template></el-table-column>
      <el-table-column label="启用" width="100"><template #default="{ row }"><el-switch v-model="row.enabled" :disabled="row.itemType === 'study_planner'" /></template></el-table-column>
    </el-table>
  </ContentWrap>
</template>
<script lang="ts" setup>
import * as Api from '@/api/zsjos/registrationChecklistConfig'
defineOptions({ name: 'ZsjosRegistrationChecklistConfig' })
const message = useMessage(); const loading = ref(false); const saving = ref(false); const error = ref(''); const config = ref<Api.ChecklistConfig>()
const load = async () => { loading.value = true; error.value = ''; try { config.value = await Api.getRegistrationChecklistConfig() } catch (e: any) { error.value = e?.msg || e?.message || '配置加载失败' } finally { loading.value = false } }
const copy = async () => { if (!config.value) return; try { await Api.copyRegistrationChecklistDraft(config.value.templateVersion); message.success('已创建草稿'); await load() } catch (e: any) { message.error(e?.msg || '复制失败') } }
const save = async () => { if (!config.value?.draft) return; saving.value = true; try { await Api.saveRegistrationChecklistDraft({ templateVersion: config.value.templateVersion, items: config.value.draft.items }); message.success('草稿已保存'); await load() } catch (e: any) { message.error(e?.msg || '保存失败') } finally { saving.value = false } }
const publish = async () => { if (!config.value) return; try { await Api.publishRegistrationChecklist(config.value.templateVersion); message.success('模板已发布'); await load() } catch (e: any) { message.error(e?.msg || '发布失败') } }
onMounted(load)
</script>
<style scoped>.toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px}.toolbar h3{margin:0 0 4px}.toolbar span{color:var(--el-text-color-secondary)}</style>
