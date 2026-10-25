<template>
  <ContentWrap title="学员录入">
    <el-alert
      v-if="!checkPermi(['zsjos:student:create'])"
      type="warning"
      title="暂无添加学员权限"
      :closable="false"
    />
    <template v-else>
      <el-radio-group v-model="type" :disabled="busy" class="mb-20px">
        <el-radio-button value="media">编导运营学员</el-radio-button>
        <el-radio-button value="teaching">教学学员</el-radio-button>
      </el-radio-group>
      <StudentEntryForm
        :key="`${type}:${revision}`"
        :type="type"
        @busy="busy = $event"
        @saved="saved"
      />
    </template>
  </ContentWrap>
</template>
<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { checkPermi } from '@/utils/permission'
import type { EntryResult, EntryType } from '@/api/zsjos/studentEntry'
import StudentEntryForm from './StudentEntryForm.vue'
defineOptions({ name: 'ZsjosStudentEntry' })
const type = ref<EntryType>('media'),
  revision = ref(0),
  busy = ref(false)
const saved = (result: EntryResult) => {
  ElMessage.success(`学员 ${result.personNo} 已添加`)
  revision.value++
}
</script>
