<template>
  <ContentWrap>
    <div class="config-toolbar">
      <div>
        <h3>学员信息收集表配置</h3>
        <span>{{
          config?.published ? `当前生效版本 V${config.published.versionNo}` : '尚未发布'
        }}</span>
      </div>
      <div class="config-actions">
        <el-button :disabled="busy" @click="load">刷新</el-button>
        <el-button
          v-hasPermi="['zsjos:student-info-form:config:update']"
          :disabled="busy || !!error"
          :loading="saving"
          @click="save"
          >保存草稿</el-button
        >
        <el-button
          v-hasPermi="['zsjos:student-info-form:config:publish']"
          type="primary"
          :disabled="busy || dirty || !config?.draft"
          @click="publish"
          >发布草稿</el-button
        >
      </div>
    </div>
  </ContentWrap>
  <ContentWrap v-loading="loading">
    <el-alert title="字段配置保存并发布后对新表单生效；未提交表单的附件限制按当前上传规则执行。" type="info" :closable="false" />
    <el-form label-position="top">
      <el-form-item label="学历提升分类（包含所有下级分类）"
        ><el-tree-select
          v-model="educationCategoryIds"
          :data="categories"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          multiple
          check-strictly
          :disabled="!editable || busy"
          placeholder="选择适用产品分类"
          class="!w-full"
      /></el-form-item>
      <el-alert
        title="命中上述分类时，报名层次、拟报名院校、拟报名专业显示且必填；其他产品隐藏这三个字段。"
        type="info"
        :closable="false"
      />
      <el-form-item label="选择产品预览条件"
        ><el-select
          v-model="previewProductId"
          filterable
          remote
          :remote-method="searchProducts"
          :loading="productsLoading"
          placeholder="输入产品名称搜索"
          clearable
          ><el-option v-for="p in products" :key="p.id" :value="p.id" :label="p.name" /></el-select
        ><el-button :disabled="!previewProductId || busy" @click="preview"
          >预览</el-button
        ></el-form-item
      >
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <el-button link @click="load">重试</el-button>
    </el-alert>
    <el-table v-else :data="fields" row-key="key">
      <el-table-column prop="label" label="字段" min-width="170" fixed />
      <el-table-column label="分组" min-width="150"
        ><template #default="{ row }"
          ><el-select v-model="row.group" :disabled="!editable || busy"
            ><el-option
              v-for="g in groups"
              :key="g.value"
              :value="g.value"
              :label="g.label" /></el-select></template
      ></el-table-column>
      <el-table-column label="附件限制" min-width="230"
        ><template #default="{ row }"
          ><template v-if="row.type === 'images'"
            ><span v-if="row.key === 'identity_images'">正面、反面各一张</span
            ><el-input-number
              v-else
              v-model="row.maxImages"
              :min="2"
              :max="9"
              :disabled="!editable || busy"
            />
            个；{{ row.maxSizeMb }}
            MB/个</template
          ><span v-else>—</span></template
        ></el-table-column
      >
      <el-table-column label="类型" width="110"
        ><template #default="{ row }">{{ types[row.type] }}</template></el-table-column
      >
      <el-table-column label="数据来源" min-width="220">
        <template #default="{ row }">
          <el-select
            v-if="row.type === 'dict'"
            v-model="row.dictType"
            filterable
            :disabled="!editable || busy || row.key === 'gender'"
            placeholder="选择系统字典"
          >
            <el-option
              v-for="item in dictTypes"
              :key="item.type"
              :label="item.name"
              :value="item.type"
            />
          </el-select>
          <span v-else>{{ row.type === 'area' ? '系统地区管理' : row.type === 'images' ? (row.allowedExtensions || ['jpg','jpeg','png']).join('/').toUpperCase() : '文本' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80"
        ><template #default="{ row }"
          ><el-switch v-model="row.enabled" :disabled="!editable || busy" /></template
      ></el-table-column>
      <el-table-column label="必填" width="80"
        ><template #default="{ row }"
          ><el-switch
            v-model="row.required"
            :disabled="!editable || busy || !row.enabled" /></template
      ></el-table-column>
      <el-table-column label="排序" width="145"
        ><template #default="{ row }"
          ><el-input-number
            v-model="row.sort"
            :min="0"
            :max="10000"
            :precision="0"
            controls-position="right"
            :disabled="!editable || busy"
            class="sort-input" /></template
      ></el-table-column>
      <el-table-column label="备注" min-width="250"
        ><template #default="{ row }"
          ><el-input
            v-model="row.note"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :disabled="!editable || busy" /></template
      ></el-table-column>
    </el-table>
  </ContentWrap>
  <el-dialog v-model="previewOpen" title="产品适用表单预览" width="760px"
    ><el-descriptions :column="2" border
      ><el-descriptions-item v-for="f in previewFields" :key="f.key" :label="f.label"
        >{{ types[f.type] }} · {{ f.required ? '必填' : '选填'
        }}<span v-if="f.type === 'images'">
          ·
          {{ f.key === 'identity_images' ? '正反面各一张' : '最多' + f.maxImages + '张' }}
          JPG/PNG</span
        ></el-descriptions-item
      ></el-descriptions
    ></el-dialog
  >
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/studentInfoFormConfig'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import * as ProductApi from '@/api/zsjos/product'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'ZsjosStudentInfoFormConfig' })
const message = useMessage()
const config = ref<Api.Config>()
const fields = ref<Api.Field[]>([])
const educationCategoryIds = ref<number[]>([])
const categories = ref<ProductApi.ZsjosProductCategoryVO[]>([])
const products = ref<ProductApi.ZsjosProductVO[]>([])
const productsLoading = ref(false)
const previewProductId = ref<number>()
const previewOpen = ref(false)
const previewFields = ref<Api.Field[]>([])
const groups = [
  { value: 'basic', label: '基本信息' },
  { value: 'education', label: '学历与报考' },
  { value: 'career', label: '职业与学习目的' },
  { value: 'materials', label: '证明材料' }
]
const serialized = () =>
  JSON.stringify({ fields: fields.value, educationCategoryIds: educationCategoryIds.value })
const searchProducts = async (name: string) => {
  productsLoading.value = true
  try {
    products.value = (await ProductApi.getProductPage({ pageNo: 1, pageSize: 50, name })).list
  } catch (e) {
    products.value = []
    message.error(e instanceof Error ? e.message : '产品加载失败，请重试')
  } finally {
    productsLoading.value = false
  }
}
const preview = async () => {
  try {
    const data = await Api.preview({
      fields: fields.value,
      revision: 0,
      educationCategoryIds: educationCategoryIds.value,
      productId: previewProductId.value
    })
    previewFields.value = data.fields
    previewOpen.value = true
  } catch (e) {
    message.error(e instanceof Error ? e.message : '预览失败，请检查配置后重试')
  }
}
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const error = ref('')
const saved = ref('')
const busy = computed(() => loading.value || saving.value || publishing.value)
const dirty = computed(() => serialized() !== saved.value)
const editable = computed(() => checkPermi(['zsjos:student-info-form:config:update']))
const types: Record<string, string> = {
  text: '单行文本',
  textarea: '多行文本',
  dict: '下拉选择',
  area: '地区树',
  images: '材料附件'
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const [result, dictionaries, categoryTree] = await Promise.all([
      Api.getConfig(),
      DictTypeApi.getSimpleDictTypeList(),
      ProductApi.getCategoryTree()
    ])
    config.value = result
    categories.value = categoryTree
    educationCategoryIds.value =
      result.draft?.educationCategoryIds ?? result.published?.educationCategoryIds ?? []
    dictTypes.value = dictionaries
    fields.value = structuredClone(
      result.draft?.fields ?? result.published?.fields ?? result.presets
    )
    saved.value = serialized()
  } catch (e: any) {
    error.value = e?.message || '配置加载失败'
  } finally {
    loading.value = false
  }
}
const save = async () => {
  if (busy.value) return
  saving.value = true
  try {
    const draft = await Api.saveDraft({
      id: config.value?.draft?.id,
      revision: config.value?.draft?.revision ?? 0,
      fields: fields.value,
      educationCategoryIds: educationCategoryIds.value
    })
    if (config.value) config.value.draft = draft
    fields.value = structuredClone(draft.fields)
    saved.value = serialized()
    message.success('草稿已保存')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败，请重试')
  } finally {
    saving.value = false
  }
}
const publish = async () => {
  if (!config.value?.draft || dirty.value || busy.value) return
  try {
    await message.confirm('发布后，新生成的收集表使用此版本。确认发布？')
  } catch {
    return
  }
  publishing.value = true
  try {
    await Api.publish({ id: config.value.draft.id, revision: config.value.draft.revision })
    message.success('配置已发布')
    await load()
  } finally {
    publishing.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.config-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}
.config-toolbar h3 {
  margin: 0 0 8px;
  font-size: 18px;
}
.config-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.config-actions .el-button {
  margin-left: 0;
}
.sort-input {
  width: 120px;
}
</style>
