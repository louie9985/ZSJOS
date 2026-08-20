<template>
  <ContentWrap>
    <div class="toolbar"><div><h3>学员联系配置</h3><span>发布后仅新建联系任务使用新版本</span></div><div><el-button :disabled="!!config?.draft" @click="copy">复制已发布版本</el-button><el-button type="primary" :disabled="!draft" :loading="saving" @click="save">保存草稿</el-button><el-button :disabled="!draft" @click="publish">发布</el-button></div></div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon />
    <el-empty v-else-if="!draft" description="暂无草稿，请先复制已发布版本" />
    <template v-else>
      <el-form inline><el-form-item label="首次联系最大间隔（分钟）"><el-input-number v-model="draft.firstContactTimeoutMinutes" :min="1" :max="10080" /></el-form-item><el-form-item label="制定学习计划最大间隔（分钟）"><el-input-number v-model="draft.studyPlanTimeoutMinutes" :min="1" :max="43200" /></el-form-item></el-form>
      <h4>首联任务清单</h4><el-table :data="draft.checklist" row-key="key"><el-table-column label="事项" min-width="280"><template #default="{ row }"><el-input v-model="row.title" /></template></el-table-column><el-table-column label="启用" width="100"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column label="附件项" width="100"><template #default="{ row }"><el-switch v-model="row.attachmentRequired" /></template></el-table-column></el-table>
      <h4>快捷备注模板</h4><el-tag v-for="note in draft.quickNotes" :key="note" class="mr-8px">{{ note }}</el-tag><el-empty v-if="!draft.quickNotes.length" description="暂无快捷备注，由管理员维护" :image-size="60" />
    </template>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/studentContactConfig'
defineOptions({ name: 'ZsjosStudentContactConfig' })
const message = useMessage(); const loading = ref(false); const saving = ref(false); const error = ref(''); const config = ref<Api.Config>(); const draft = computed(() => config.value?.draft)
const load = async () => { loading.value = true; error.value = ''; try { config.value = await Api.getStudentContactConfig() } catch (e: any) { error.value = e?.msg || e?.message || '配置加载失败' } finally { loading.value = false } }
const copy = async () => { try { await Api.copyStudentContactDraft(); await load(); message.success('已创建草稿') } catch (e: any) { message.error(e?.msg || '复制失败') } }
const save = async () => { if (!draft.value) return; saving.value = true; try { await Api.saveStudentContactDraft({ ...draft.value }); await load(); message.success('草稿已保存') } catch (e: any) { message.error(e?.msg || '保存失败') } finally { saving.value = false } }
const publish = async () => { if (!draft.value) return; try { await Api.publishStudentContactConfig(draft.value.id, draft.value.version); await load(); message.success('配置已发布') } catch (e: any) { message.error(e?.msg || '发布失败') } }
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px }
.toolbar h3 { margin: 0 }
.toolbar span { color: var(--el-text-color-secondary) }
h4 { margin: 20px 0 12px }
</style>
