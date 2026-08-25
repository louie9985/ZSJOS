<template>
  <ContentWrap>
    <div class="toolbar"><div><h3>业务表单配置</h3><span>发布后仅新建联系任务使用新版本</span></div><div><el-button v-hasPermi="['zsjos:student-contact-config:update']" :disabled="!!config?.draft || !config?.published" :loading="copying" @click="copy">复制已发布版本</el-button><el-button v-hasPermi="['zsjos:student-contact-config:update']" type="primary" :disabled="!draft" :loading="saving" @click="save">保存草稿</el-button><span v-hasPermi="['zsjos:student-contact-config:update']"><el-button v-hasPermi="['zsjos:student-contact-config:publish']" :disabled="!draft" :loading="publishing" @click="publish">保存并发布</el-button></span></div></div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon />
    <el-empty v-else-if="!draft" description="暂无草稿，请先复制已发布版本" />
    <template v-else>
      <el-form inline><el-form-item label="首次联系最大间隔（分钟）"><el-input-number v-model="draft.firstContactTimeoutMinutes" :min="5" :max="10080" /></el-form-item><el-form-item label="制定学习计划最大间隔（分钟）"><el-input-number v-model="draft.studyPlanTimeoutMinutes" :min="5" :max="43200" /></el-form-item></el-form>
      <h4>首联任务清单</h4><el-table :data="draft.checklist" row-key="key"><el-table-column label="事项" min-width="280"><template #default="{ row }"><el-input v-model="row.title" /></template></el-table-column><el-table-column label="启用" width="100"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column label="附件项" width="100"><template #default="{ row }"><el-switch v-model="row.attachmentRequired" /></template></el-table-column></el-table>
      <h4>快捷备注模板</h4><el-tag v-for="note in draft.quickNotes" :key="note" class="mr-8px">{{ note }}</el-tag><el-empty v-if="!draft.quickNotes.length" description="暂无快捷备注，由管理员维护" :image-size="60" />
      <h4>业务表单字段（JSON）</h4>
      <el-alert type="info" :closable="false" title="字段类型支持 text、textarea、number、date、datetime、select、multi_select、radio、checkbox_group、checkbox、dict、attachment；所有枚举字段必须关联系统字典，保存时由服务端校验字典有效性。" />
      <el-input v-model="formJson" type="textarea" :rows="14" placeholder="按阶段编码配置字段数组，例如 first_contact、study_plan、supervision" />
    </template>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/studentContactConfig'
defineOptions({ name: 'ZsjosBusinessFormConfig' })
const message = useMessage(); const loading = ref(false); const saving = ref(false); const copying = ref(false); const publishing = ref(false); const error = ref(''); const config = ref<Api.Config>(); const formJson = ref('{}'); const draft = computed(() => config.value?.draft)
const load = async () => { loading.value = true; error.value = ''; try { config.value = await Api.getStudentContactConfig(); formJson.value = JSON.stringify(config.value?.draft?.forms || {}, null, 2) } catch (e: any) { error.value = e?.msg || e?.message || '配置加载失败' } finally { loading.value = false } }
const copy = async () => { const published = config.value?.published; if (!published) return; copying.value = true; try { await Api.copyStudentContactDraft(published.id, published.version, crypto.randomUUID()); await load(); message.success('已创建草稿') } catch (e: any) { message.error(e?.msg || '复制失败') } finally { copying.value = false } }
const validateDraft = (value: Api.Version) => value.firstContactTimeoutMinutes >= 5 && value.studyPlanTimeoutMinutes >= 5 && value.checklist.some((item) => item.enabled) && value.checklist.every((item) => item.title.trim().length > 0)
const persistDraft = async (value: Api.Version) => { if (!validateDraft(value)) throw new Error('请检查时间限制和首联任务清单'); let forms: Api.Version['forms']; try { forms = JSON.parse(formJson.value || '{}') } catch { throw new Error('业务表单 JSON 格式错误') } await Api.saveStudentContactDraft({ ...value, forms, idempotencyKey: crypto.randomUUID() }) }
const save = async () => { if (!draft.value) return; saving.value = true; try { await persistDraft(draft.value); await load(); message.success('草稿已保存') } catch (e: any) { message.error(e?.msg || e?.message || '保存失败') } finally { saving.value = false } }
const publish = async () => { if (!draft.value) return; publishing.value = true; try { await persistDraft(draft.value); config.value = await Api.getStudentContactConfig(); const current = config.value?.draft; if (!current) throw new Error('草稿状态已变化，请刷新后重试'); await Api.publishStudentContactConfig(current.id, current.version, crypto.randomUUID()); await load(); message.success('配置已发布') } catch (e: any) { message.error(e?.msg || e?.message || '发布失败') } finally { publishing.value = false } }
onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px }
.toolbar h3 { margin: 0 }
.toolbar span { color: var(--el-text-color-secondary) }
h4 { margin: 20px 0 12px }
</style>
