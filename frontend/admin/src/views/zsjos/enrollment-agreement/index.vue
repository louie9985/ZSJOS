<template>
  <ContentWrap v-loading="busy">
    <el-alert v-if="error" :title="error" type="error" :closable="false">
      <el-button link @click="load">重新加载</el-button>
    </el-alert>
    <h2>报名协议维护</h2>
    <el-cascader v-model="categoryId" :options="categories" :props="{ label: 'name', value: 'id', emitPath: false }"
      filterable :disabled="busy" placeholder="选择产品末级分类" aria-label="产品末级分类" @change="load" />
    <el-empty v-if="!busy && !error && !version" :description="categories.length ? '请选择要维护协议的末级分类' : '暂无产品分类'" />
    <template v-if="version">
      <p>{{ version.publishedVersion ? '当前发布版本 V' + version.publishedVersion : '该分类尚未发布协议' }} · 仅适用于本分类新生成的报名表</p>
      <el-space v-if="editable" wrap class="mb-12px">
        <span>插入订单字段</span>
        <el-button v-for="field in fields" :key="field" size="small" @click="insertField(field)">{{ field }}</el-button>
      </el-space>
      <Editor ref="editor" v-model="body" height="420px" :readonly="!editable" />
      <el-space wrap class="mt-12px">
        <el-button @click="preview = true">手机预览</el-button>
        <el-button v-hasPermi="['zsjos:agreement:update']" :disabled="busy" @click="save(false)">保存草稿</el-button>
        <el-button v-hasPermi="['zsjos:agreement:publish']" type="primary" :disabled="busy || !version.revision" @click="save(true)">发布版本</el-button>
      </el-space>
    </template>
  </ContentWrap>
  <el-dialog v-model="preview" title="协议预览（示例订单）" width="min(390px, 94vw)">
    <article v-dompurify-html="sample" class="preview"></article>
  </el-dialog>
</template>

<script setup lang="ts">
import type { EditorExpose } from '@/components/Editor'
import request from '@/config/axios'
import { checkPermi } from '@/utils/permission'
interface Template { revision: number; publishedVersion: number; draftBody: string }
const endpoint = '/zsjos/enrollment-agreement'
interface Category { id: number; name: string; children?: Category[] }
const categories = ref<Category[]>([])
const categoryId = ref<number>()
const version = ref<Template>()
const body = ref('')
const fields = ['学员姓名', '联系电话', '课程名称', '实际费用']
const editor = ref<EditorExpose>()
async function insertField(field: string) {
  const instance = await editor.value?.getEditorRef()
  instance?.restoreSelection()
  instance?.insertText('{{' + field + '}}')
}
const preview = ref(false)
const busy = ref(false)
const error = ref('')
const message = useMessage()
const editable = computed(() => checkPermi(['zsjos:agreement:update']))
const sample = computed(() => body.value.replaceAll('{{学员姓名}}', '示例学员').replaceAll('{{课程名称}}', '示例末级分类')
  .replaceAll('{{联系电话}}', '138****0000').replaceAll('{{实际费用}}', '1680.00'))
async function refresh() {
  const data = await request.get<Template>({ url: endpoint, params: { categoryId: categoryId.value } })
  version.value = data
  body.value = data.draftBody
}
async function load() {
  busy.value = true
  error.value = ''
  version.value = undefined
  preview.value = false
  try {
    if (!categories.value.length) categories.value = await request.get<Category[]>({ url: endpoint + '/categories' })
    if (categoryId.value) await refresh()
  }
  catch (cause) { error.value = String(cause) }
  finally { busy.value = false }
}
async function save(publish: boolean) {
  if (!version.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const data = { categoryId: categoryId.value, revision: version.value.revision, body: body.value }
    if (publish) {
      try { await message.confirm('将发布最近一次保存的草稿，随后生成的收集表会使用此版本。') }
      catch { return }
      await request.post({ url: endpoint + '/publish', data: { ...data, body: version.value.draftBody } })
    }
    else await request.put({ url: endpoint + '/draft', data })
    await refresh()
    message.success(publish ? '协议已发布' : '草稿已保存')
  } catch (cause) { error.value = String(cause) }
  finally { busy.value = false }
}
onMounted(load)
</script>
<style scoped>
.preview { line-height: 1.8; overflow-wrap: anywhere; max-height: 65vh; overflow-y: auto; }
</style>
