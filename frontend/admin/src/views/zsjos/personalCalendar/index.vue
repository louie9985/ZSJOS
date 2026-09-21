<template>
  <ContentWrap>
    <div class="heading"><div><h2>我的日历</h2><span>{{ readScope === 'SELF' ? '本人的个人日程' : '管理员只读查看' }}</span></div><div><el-button @click="load">刷新</el-button><el-button v-if="readScope === 'SELF'" v-hasPermi="['zsjos:personal-calendar:create']" type="primary" @click="openCreate">新增日程</el-button></div></div>
    <el-space v-if="userStore.dataAccess.tenantReadAll" wrap>
      <el-select v-model="readScope" aria-label="查看范围" style="width: 180px"><el-option label="本人" value="SELF"/><el-option label="全部（只读）" value="ALL"/><el-option label="指定人员（只读）" value="USER"/></el-select>
      <el-select v-if="readScope === 'USER'" v-model="targetUserId" placeholder="请选择人员" filterable :loading="usersLoading" style="width: 180px"><el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id"/></el-select>
      <span v-if="readScope !== 'SELF'">只读查看，不代替他人办理业务</span>
      <el-alert v-if="usersError" type="error" :title="usersError"><el-button @click="loadUsers">重试</el-button></el-alert>
    </el-space>
    <el-alert v-if="error" class="mt-16px" type="error" :title="error" show-icon :closable="false"><el-button link @click="load">重试</el-button></el-alert>
    <el-calendar v-else v-model="anchor" v-loading="loading" class="mt-16px">
      <template #date-cell="{ data }"><div class="date-cell"><strong>{{ dayjs(data.day).date() }}</strong><div v-for="event in eventsFor(data.day)" :key="event.id" class="event"><span>{{ event.title }}{{ readScope !== 'SELF' && event.ownerName ? ` · ${event.ownerName}` : '' }}</span><span><el-button v-if="readScope === 'SELF'" v-hasPermi="['zsjos:personal-calendar:update']" link :icon="Edit" @click.stop="openEdit(event)" /><el-button v-if="readScope === 'SELF'" v-hasPermi="['zsjos:personal-calendar:delete']" link type="danger" :icon="Delete" @click.stop="remove(event)" /></span></div></div></template>
    </el-calendar>
  </ContentWrap>
  <Dialog v-model="visible" :title="editing ? '编辑日程' : '新增日程'" width="560px"><el-form :model="form" label-width="70px"><el-form-item label="标题" required><el-input v-model="form.title" maxlength="100" /></el-form-item><el-form-item label="时间" required><el-date-picker v-model="form.range" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" class="!w-100%" /></el-form-item><el-form-item label="全天"><el-switch v-model="form.allDay" /></el-form-item><el-form-item label="说明"><el-input v-model="form.description" type="textarea" maxlength="2000" /></el-form-item></el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template></Dialog>
</template>
<script lang="ts" setup>
import { Delete, Edit } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { onMounted, reactive, ref, watch } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import { useUserStore } from '@/store/modules/user'
import { getSimpleUserOptions, type UserSimpleVO } from '@/api/system/user'
import * as CalendarApi from '@/api/zsjos/calendar'
defineOptions({ name: 'ZsjosPersonalCalendar' })
const message=useMessage(), anchor=ref(new Date()), events=ref<CalendarApi.PersonalCalendarEvent[]>([]), loading=ref(false), error=ref(''), visible=ref(false), saving=ref(false), editing=ref<CalendarApi.PersonalCalendarEvent>()
const userStore = useUserStore()
const readScope = ref<'SELF' | 'ALL' | 'USER'>('SELF'), targetUserId = ref<number>()
const users = ref<UserSimpleVO[]>([]), usersLoading = ref(false), usersError = ref('')
const loadUsers = async () => { usersLoading.value = true; usersError.value = ''; try { users.value = await getSimpleUserOptions() } catch { usersError.value = '人员加载失败' } finally { usersLoading.value = false } }
let requestSequence = 0
watch(readScope, () => { targetUserId.value = undefined; visible.value = false; if (readScope.value === 'USER') void loadUsers() })
const form=reactive({ title:'', description:'', range:[] as string[], allDay:false })
const load=async()=>{
  const sequence = ++requestSequence
  events.value=[];error.value=''
  if(readScope.value==='USER' && targetUserId.value===undefined){loading.value=false;return}
  loading.value=true;const value=dayjs(anchor.value)
  try{const rows=await CalendarApi.getPersonalCalendar({readScope:readScope.value,targetUserId:targetUserId.value,rangeStart:value.startOf('month').startOf('week').format('YYYY-MM-DDTHH:mm:ss'),rangeEnd:value.endOf('month').endOf('week').add(1,'second').format('YYYY-MM-DDTHH:mm:ss')});if(sequence===requestSequence)events.value=rows}
  catch(e:any){if(sequence===requestSequence)error.value=e?.msg||e?.message||'个人日程加载失败'}finally{if(sequence===requestSequence)loading.value=false}
}
const eventTouchesDay=(event:CalendarApi.PersonalCalendarEvent,date:string)=>{const dayStart=dayjs(date).startOf('day');const dayEnd=dayStart.add(1,'day');const start=dayjs(event.startTime);const end=dayjs(event.endTime);return start.isSame(end)?!start.isBefore(dayStart)&&start.isBefore(dayEnd):start.isBefore(dayEnd)&&end.isAfter(dayStart)}
const eventsFor=(date:string)=>events.value.filter(item=>eventTouchesDay(item,date)).slice(0,3)
const openCreate=()=>{editing.value=undefined;Object.assign(form,{title:'',description:'',range:[dayjs().hour(9).format('YYYY-MM-DDTHH:mm:ss'),dayjs().hour(10).format('YYYY-MM-DDTHH:mm:ss')],allDay:false});visible.value=true}
const openEdit=(event:CalendarApi.PersonalCalendarEvent)=>{editing.value=event;Object.assign(form,{title:event.title,description:event.description||'',range:[dayjs(event.startTime).format('YYYY-MM-DDTHH:mm:ss'),dayjs(event.endTime).format('YYYY-MM-DDTHH:mm:ss')],allDay:event.allDay});visible.value=true}
const save=async()=>{if(!form.title.trim()||form.range.length!==2){message.error('请填写标题和有效时间');return}saving.value=true;try{const data={title:form.title.trim(),description:form.description.trim()||undefined,startTime:form.range[0],endTime:form.range[1],allDay:form.allDay};if(editing.value)await CalendarApi.updatePersonalCalendar(editing.value.id,data);else await CalendarApi.createPersonalCalendar(data);message.success('日程已保存');visible.value=false;await load()}catch(e:any){if(e?.msg||e?.message)message.error(e.msg||e.message);else message.error('日程保存失败，请重试')}finally{saving.value=false}}
const remove=async(event:CalendarApi.PersonalCalendarEvent)=>{try{await ElMessageBox.confirm(`确定删除“${event.title}”？`,'删除日程');await CalendarApi.deletePersonalCalendar(event.id);message.success('日程已删除');await load()}catch(e:any){if(e==='cancel'||e==='close')return;if(e?.msg||e?.message)message.error(e.msg||e.message);else message.error('日程删除失败，请重试');await load()}}
watch([anchor,readScope,targetUserId],load);onMounted(load)
</script>
<style scoped>.heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.heading h2{margin:0 0 4px}.heading span{color:var(--el-text-color-secondary)}.date-cell{height:100%;overflow:hidden}.event{display:flex;align-items:center;justify-content:space-between;margin-top:4px;padding:2px 6px;background:var(--el-color-primary-light-9);border-left:3px solid var(--el-color-primary);font-size:12px}.event>span:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}</style>
