<template>
  <ContentWrap>
    <div class="header-row">
      <div><h2>{{ positioning ? '定位卡模板配置' : '采访表单配置' }}</h2><span>字段和枚举选项均由服务端维护，发布后历史草稿继续使用原版本。</span></div>
      <el-space><el-button v-if="positioning" v-hasPermi="['zsjos:positioning-template:create']" @click="createTemplate">新增模板</el-button><el-button :loading="loading" @click="load">重试</el-button></el-space>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="mb-16px" />
    <el-empty v-if="!loading && !templates.length" description="暂无可用模板" />
    <el-tabs v-else v-model="selectedId" @tab-change="selectTemplate">
      <el-tab-pane v-for="item in templates" :key="item.id" :name="item.id" :label="item.name" />
    </el-tabs>
    <div v-if="current" v-loading="loading" class="designer">
      <section class="field-list">
        <div class="section-title"><strong>字段列表</strong><el-button v-if="positioning" size="small" @click="addField">新增字段</el-button></div>
        <button v-for="(field,index) in fields" :key="field.key" class="field-row" :class="{ active: selectedIndex===index }" @click="selectedIndex=index">
          <span><b>{{ field.title }}</b><small>{{ field.key }} · {{ field.type }}</small></span>
          <el-space><el-button link :disabled="index===0" @click.stop="move(index,-1)">上移</el-button><el-button link :disabled="index===fields.length-1" @click.stop="move(index,1)">下移</el-button></el-space>
        </button>
      </section>
      <section v-if="activeField" class="properties">
        <div class="section-title"><strong>字段属性</strong><el-tag v-if="activeField.systemField">系统字段</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="字段标题"><el-input v-model="activeField.title" /></el-form-item>
          <el-form-item label="字段编码"><el-input v-model="activeField.key" :disabled="activeField.systemField" /></el-form-item>
          <el-form-item label="控件类型"><el-select v-model="activeField.type" :disabled="activeField.systemField" class="w-100%"><el-option v-for="type in fieldTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></el-form-item>
          <el-form-item v-if="enumField" label="关联系统字典" required><el-select v-model="activeField.dictType" filterable class="w-100%" @change="previewDict"><el-option v-for="item in dictTypes" :key="item.type" :label="`${item.name} (${item.type})`" :value="item.type" /></el-select><div class="dict-preview">当前启用项 {{ dictCount }} 个<span v-if="dictError">，加载失败，请重试</span></div></el-form-item>
          <el-form-item label="分组"><el-input v-model="activeField.group" /></el-form-item>
          <el-form-item label="填写备注"><el-input v-model="activeField.description" type="textarea" :maxlength="500" show-word-limit /></el-form-item>
          <el-space><el-switch v-model="activeField.enabled" active-text="启用" /><el-switch v-model="activeField.required" active-text="必填" /></el-space>
        </el-form>
      </section>
      <section class="preview"><div class="section-title"><strong>表单预览</strong><el-radio-group v-model="previewMode" size="small"><el-radio-button value="desktop">桌面</el-radio-button><el-radio-button value="mobile">移动</el-radio-button></el-radio-group></div><div class="preview-body" :class="previewMode"><el-form label-position="top"><el-form-item v-for="field in fields.filter(x=>x.enabled)" :key="field.key" :label="field.title" :required="field.required"><el-input disabled placeholder="预览控件" /><div v-if="field.description?.trim()" class="field-remark">{{ field.description }}</div></el-form-item></el-form></div></section>
    </div>
    <div v-if="current" class="footer-actions"><el-button @click="copy">复制为新草稿</el-button><el-button type="primary" @click="save">保存草稿</el-button><el-button type="success" @click="publishDraft">发布</el-button></div>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/director'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import * as DictDataApi from '@/api/system/dict/dict.data'
import { useMessage } from '@/hooks/web/useMessage'
const route=useRoute(), message=useMessage(); const positioning=computed(()=>String(route.path).includes('positioning'))
const loading=ref(false), error=ref(''), templates=ref<Api.DirectorTemplate[]>([]), selectedId=ref<number>(), fields=ref<Api.DirectorField[]>([]), selectedIndex=ref(0), previewMode=ref('desktop'), dictTypes=ref<any[]>([]), dictCount=ref(0), dictError=ref(false)
const current=computed(()=>templates.value.find(x=>x.id===selectedId.value)); const activeField=computed(()=>fields.value[selectedIndex.value]); const enumField=computed(()=>['select','multi_select','radio','checkbox_group'].includes(activeField.value?.type||'')); const fieldTypes=[{value:'text',label:'单行文本'},{value:'textarea',label:'多行文本'},{value:'number',label:'数字'},{value:'select',label:'下拉单选'},{value:'multi_select',label:'下拉多选'},{value:'radio',label:'单选'},{value:'checkbox_group',label:'多选'},{value:'checkbox',label:'开关'},{value:'region',label:'地区'}]
const sync=()=>{const v=current.value?.draft||current.value?.published;fields.value=(v?.fields||[]).map(x=>({...x}));selectedIndex.value=0;void previewDict()}
const load=async()=>{loading.value=true;error.value='';try{dictTypes.value=await DictTypeApi.getSimpleDictTypeList();templates.value=await Api.getTemplates(positioning.value);selectedId.value=templates.value[0]?.id;sync()}catch(e:any){error.value=e?.message||'加载失败'}finally{loading.value=false}}
const selectTemplate=()=>sync(); const move=(i:number,d:number)=>{const a=[...fields.value];[a[i],a[i+d]]=[a[i+d],a[i]];fields.value=a.map((x,n)=>({...x,sort:(n+1)*10}));selectedIndex.value=i+d}
const addField=()=>{fields.value.push({key:`custom_${Date.now()}`,title:'新字段',type:'text',enabled:true,required:false,systemField:false,sort:(fields.value.length+1)*10});selectedIndex.value=fields.value.length-1}
const previewDict=async()=>{dictCount.value=0;dictError.value=false;if(!activeField.value?.dictType)return;try{dictCount.value=(await DictDataApi.getDictDataByType(activeField.value.dictType)).filter((x:any)=>x.status===0).length}catch{dictError.value=true}}
const copy=async()=>{if(!current.value)return;await Api.copyDraft(positioning.value,current.value.id,current.value.version);await load()}
const save=async()=>{const t=current.value,v=t?.draft;if(!t||!v)return message.warning('请先复制为草稿');if(fields.value.some(x=>['select','multi_select','radio','checkbox_group'].includes(x.type)&&!x.dictType))return message.error('枚举字段必须关联系统字典');await Api.saveDraft(positioning.value,t.id,{versionId:v.id,version:v.version,name:t.name,defaultTemplate:t.defaultTemplate,fields:fields.value});message.success('草稿已保存');await load()}
const publishDraft=async()=>{const t=current.value,v=t?.draft;if(!t||!v)return message.warning('没有可发布草稿');await Api.publish(positioning.value,t.id,{versionId:v.id,version:v.version});message.success('已发布');await load()}
const createTemplate=async()=>{await Api.createPositioning({templateCode:`positioning_${Date.now()}`,name:'新定位卡模板',defaultTemplate:false,fields:fields.value});message.success('模板已创建');await load()}
watch(()=>route.path,load);onMounted(load)
</script>
<style scoped>
.header-row,
.section-title,
.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-row {
  margin-bottom: 20px;
}

.header-row h2 {
  margin: 0 0 6px;
}

.header-row span {
  color: var(--el-text-color-secondary);
}

.designer {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) minmax(280px, 1fr) minmax(300px, 1.2fr);
  gap: 16px;
}

.field-list,
.properties,
.preview {
  min-height: 480px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
}

.field-row {
  display: flex;
  width: 100%;
  padding: 12px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  justify-content: space-between;
  align-items: center;
}

.field-row.active {
  background: var(--el-color-primary-light-9);
}

.field-row small {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.dict-preview,
.field-remark {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-remark {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.preview-body.mobile {
  max-width: 360px;
}

.footer-actions {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (width <= 1000px) {
  .designer {
    grid-template-columns: 1fr;
  }

  .field-list,
  .properties,
  .preview {
    min-height: auto;
  }
}
</style>
