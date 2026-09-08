<template>
  <ContentWrap>
    <div class="heading"><div><h2>账号日历</h2><span>仅展示当前账号责任范围内的维护排期</span></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-form :inline="true" :model="query" class="mt-16px">
      <el-form-item label="日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" @change="load" /></el-form-item>
      <el-form-item label="关键字"><el-input v-model="query.keyword" clearable @keyup.enter="load" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.currentStatusValue" clearable class="!w-180px"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="阶段"><el-select v-model="query.stageValue" clearable class="!w-180px"><el-option v-for="item in stages" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="编导"><el-select v-model="query.directorUserId" clearable class="!w-180px"><el-option v-for="item in candidates.directors" :key="item.id" :label="item.nickname" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="运营"><el-select v-model="query.operatorUserId" clearable class="!w-180px"><el-option v-for="item in candidates.operators" :key="item.id" :label="item.nickname" :value="item.id" /></el-select></el-form-item>
      <el-button type="primary" @click="load">查询</el-button>
    </el-form>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <el-table v-else v-loading="loading" :data="rows" empty-text="当前范围内没有已排期账号">
      <el-table-column prop="accountNo" label="账号编号" min-width="150" /><el-table-column prop="nickname" label="账号昵称" min-width="150" /><el-table-column prop="studentName" label="学员" min-width="120" /><el-table-column prop="directorUserName" label="编导" min-width="110" /><el-table-column prop="operatorUserName" label="运营" min-width="110" /><el-table-column prop="currentStatusLabelSnapshot" label="状态" min-width="120" /><el-table-column label="排期" min-width="220"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </ContentWrap>
</template>
<script lang="ts" setup>
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'
import * as CalendarApi from '@/api/zsjos/calendar'
import { getStrDictOptions } from '@/utils/dict'
defineOptions({ name: 'ZsjosMediaCalendar' })
const dates = ref<[string,string]>([dayjs().startOf('month').format('YYYY-MM-DD'), dayjs().endOf('month').format('YYYY-MM-DD')])
const statuses = computed(() => getStrDictOptions('zsjos_media_account_current_status')), stages = computed(() => getStrDictOptions('zsjos_media_account_stage'))
const query = reactive({ pageNo: 1, pageSize: 50, keyword: '', currentStatusValue: undefined as string|undefined, stageValue: undefined as string|undefined, directorUserId: undefined as number|undefined, operatorUserId: undefined as number|undefined })
const candidates = reactive<CalendarApi.MediaCalendarCandidates>({ directors: [], operators: [] }), rows = ref<CalendarApi.MediaCalendarItem[]>([]), total = ref(0), loading = ref(false), error = ref('')
const load = async () => { loading.value=true; error.value=''; try { const result=await CalendarApi.getMediaCalendar({ ...query, keyword: query.keyword || undefined, rangeStart: dates.value[0], rangeEnd: dates.value[1] }); rows.value=result.list; total.value=result.total } catch (e:any) { rows.value=[]; total.value=0; error.value=e?.msg || e?.message || '账号日历加载失败' } finally { loading.value=false } }
onMounted(async () => { try { Object.assign(candidates, await CalendarApi.getMediaCalendarCandidates()) } finally { await load() } })
</script>
<style scoped>.heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.heading h2{margin:0 0 4px}.heading span{color:var(--el-text-color-secondary)}</style>
