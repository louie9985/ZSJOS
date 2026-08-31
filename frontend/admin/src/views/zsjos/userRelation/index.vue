<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
      <el-form-item label="场景名称" prop="name">
        <el-input
          v-model="queryParams.name"
          clearable
          class="!w-220px"
          placeholder="请输入场景名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="场景编码" prop="code">
        <el-input
          v-model="queryParams.code"
          clearable
          class="!w-260px"
          placeholder="请输入场景编码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-160px" placeholder="全部">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          v-hasPermi="['zsjos:user-relation-scene:create']"
          type="primary"
          plain
          @click="openForm('create')"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增场景
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="场景编号" prop="id" width="100" align="center" />
      <el-table-column label="场景名称" prop="name" min-width="170" />
      <el-table-column label="场景编码" prop="code" min-width="220" show-overflow-tooltip />
      <el-table-column label="来源用户" min-width="210">
        <template #default="{ row }">
          <div>{{ row.sourceLabel }}</div>
          <div class="secondary-text">岗位：{{ postName(row.sourcePostCode) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="目标用户" min-width="210">
        <template #default="{ row }">
          <div>{{ row.targetLabel }}</div>
          <div class="secondary-text">岗位：{{ postName(row.targetPostCode) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="210" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['zsjos:user-relation-scene:update']"
            link
            type="primary"
            @click="openForm('update', row.id)"
            >修改</el-button
          >
          <router-link :to="'/user-relation/data/' + row.code">
            <el-button v-hasPermi="['zsjos:user-relation:query']" link type="primary">
              关系数据
            </el-button>
          </router-link>
          <ZsjosPopconfirm
            :action="`删除用户关系场景「${row.name}」`"
            danger
            @confirm="handleDelete(row.id)"
          >
            <el-button v-hasPermi="['zsjos:user-relation-scene:delete']" link type="danger"
              >删除</el-button
            >
          </ZsjosPopconfirm>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <SceneForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import * as PostApi from '@/api/system/post'
import * as UserRelationApi from '@/api/zsjos/userRelation'
import SceneForm from './SceneForm.vue'
import ZsjosPopconfirm from '../components/ZsjosPopconfirm.vue'

defineOptions({ name: 'ZsjosUserRelationScene' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<UserRelationApi.UserRelationSceneVO[]>([])
const postMap = ref<Map<string, string>>(new Map())
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  name: '',
  code: '',
  status: undefined
})

const postName = (code: string) => postMap.value.get(code) || code

const getList = async () => {
  loading.value = true
  try {
    const data = await UserRelationApi.getScenePage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const formRef = ref()
const openForm = (type: 'create' | 'update', id?: number) => formRef.value.open(type, id)

const handleDelete = async (id: number) => {
  try {
    await UserRelationApi.deleteScene(id)
    message.success('删除成功')
    await getList()
  } catch {}
}

onMounted(async () => {
  const posts = await PostApi.getSimplePostList()
  postMap.value = new Map(posts.map((post) => [post.code, post.name]))
  await getList()
})
</script>

<style scoped>
.secondary-text {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
