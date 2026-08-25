<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {showToast} from 'vant'
import dayjs from 'dayjs'
import {decidePositioning,getPositioningCard,type PositioningConfirmation} from '@/api/positioning'

const confirmation=ref<PositioningConfirmation>()
const loading=ref(true),error=ref(''),submitting=ref(false),completed=ref<'agree'|'request_changes'>()
const revisionOpen=ref(false),comment=ref('')
const token=()=>new URLSearchParams(window.location.hash.slice(1)).get('token')||''
const fields=computed(()=>confirmation.value?.fields?.filter(field=>field.enabled!==false)||[])
const legacySections=computed(()=>Object.entries(confirmation.value?.legacySections||{}).filter(([,value])=>Object.keys(value||{}).length))
const displayValue=(key:string)=>{
  const snapshot=confirmation.value?.dictSnapshots?.[key] as {labelSnapshot?:string}|Array<{labelSnapshot?:string}>|undefined
  if(Array.isArray(snapshot)){
    const labels=snapshot.map(item=>item.labelSnapshot).filter(Boolean)
    if(labels.length)return labels.join('、')
  }else if(snapshot?.labelSnapshot)return snapshot.labelSnapshot
  const value=confirmation.value?.values?.[key]
  if(Array.isArray(value))return value.join('、')
  if(typeof value==='boolean')return value?'是':'否'
  return value==null||value===''?'未填写':String(value)
}
const load=async()=>{loading.value=true;error.value='';try{if(!token())throw new Error('确认链接无效或已失效');confirmation.value=await getPositioningCard(token())}catch(cause){error.value=cause instanceof Error?cause.message:'定位卡加载失败'}finally{loading.value=false}}
const decide=async(decision:'agree'|'request_changes')=>{if(decision==='request_changes'&&!comment.value.trim()){showToast('请填写修改意见');return}submitting.value=true;try{await decidePositioning(token(),decision,decision==='request_changes'?comment.value.trim():undefined);completed.value=decision;revisionOpen.value=false}catch(cause){showToast(cause instanceof Error?cause.message:'提交失败')}finally{submitting.value=false}}
onMounted(load)
</script>

<template>
  <main class="positioning-share-page">
    <van-nav-bar title="账号定位卡确认" />
    <van-loading v-if="loading" class="positioning-share-state" vertical>正在加载定位卡</van-loading>
    <van-empty v-else-if="error" :description="error"><van-button size="small" type="primary" @click="load">重试</van-button></van-empty>
    <van-empty v-else-if="completed||confirmation?.state==='processed'" :description="completed==='request_changes'?'修改意见已提交':'该定位卡已完成确认'" />
    <template v-else-if="confirmation?.state==='ready'">
      <section class="positioning-share-heading">
        <h1>{{ confirmation.accountName || '账号定位卡' }}</h1>
        <p>{{ confirmation.platformLabel || '账号平台' }} · 提交于 {{ confirmation.submittedAt ? dayjs(confirmation.submittedAt).format('YYYY-MM-DD HH:mm') : '历史时间未记录' }}</p>
      </section>
      <van-cell-group inset title="定位内容">
        <van-cell v-for="field in fields" :key="field.key" :title="field.title" :label="displayValue(field.key)" />
        <van-cell title="试运行结束日期" :value="confirmation.trialEndDate || '未填写'" />
      </van-cell-group>
      <van-cell-group v-for="[title,section] in legacySections" :key="title" inset :title="title">
        <van-cell v-for="(value,key) in section" :key="key" :title="String(key)" :label="typeof value==='object'?JSON.stringify(value):String(value)" />
      </van-cell-group>
      <div class="positioning-share-actions">
        <van-button type="primary" block :loading="submitting" @click="decide('agree')">同意定位卡</van-button>
        <van-button block :disabled="submitting" @click="revisionOpen=true">提出修改</van-button>
      </div>
    </template>
    <van-popup v-model:show="revisionOpen" position="bottom" round>
      <section class="positioning-share-revision">
        <h2>提出修改</h2>
        <van-field v-model="comment" type="textarea" maxlength="500" show-word-limit rows="5" placeholder="请填写需要调整的内容" />
        <van-button type="primary" block :loading="submitting" @click="decide('request_changes')">提交修改意见</van-button>
      </section>
    </van-popup>
  </main>
</template>

<style scoped>
.positioning-share-page{min-height:100vh;background:#f5f6f8;padding-bottom:96px;color:#1f2329}
.positioning-share-state{padding-top:96px}
.positioning-share-heading{padding:24px 20px 16px;background:#fff}
.positioning-share-heading h1{margin:0 0 8px;font-size:22px;letter-spacing:0}
.positioning-share-heading p{margin:0;color:#646a73;font-size:14px;letter-spacing:0}
.positioning-share-actions{position:sticky;bottom:0;display:grid;gap:10px;padding:16px 20px;background:#fff}
.positioning-share-revision{padding:20px 20px 28px}
.positioning-share-revision h2{margin:0 0 16px;font-size:18px;letter-spacing:0}
.positioning-share-revision .van-button{margin-top:16px}
</style>
