<script setup lang="ts">
import { computed, ref, watch } from 'vue'
const props=defineProps<{id:string;label:string;modelValue:string;options:{value:string;label:string}[];disabled:boolean}>()
const emit=defineEmits<{ 'update:modelValue':[string] }>()
const open=ref(false),search=ref(''),selected=ref<string[]>([])
const longList=computed(()=>props.options.length>10)
const display=computed(()=>props.options.find(x=>x.value===props.modelValue)?.label || '')
const filtered=computed(()=>props.options.filter(x=>x.label.includes(search.value.trim())))
watch(open,value=>{if(value){selected.value=[props.modelValue||props.options[0]?.value||''];search.value=''}})
watch(()=>props.disabled,value=>{if(value)open.value=false})
function confirm(){if(!props.disabled){emit('update:modelValue',selected.value[0]||'');open.value=false}}
</script>
<template>
 <div class="collection-select norem">
  <button :id="id" type="button" class="collection-select-trigger norem" :class="{placeholder:!display}" :disabled="disabled" :aria-label="label" aria-haspopup="dialog" :aria-expanded="open" @click="open=true"><span>{{display||'请选择'}}</span><van-icon name="arrow-down" /></button>
  <van-popup v-model:show="open" position="bottom" round class="collection-select-popup norem">
   <template v-if="longList"><div class="collection-select-toolbar norem"><button type="button" @click="open=false">取消</button><strong>选择{{label}}</strong><button type="button" class="confirm" :disabled="!selected[0]" @click="confirm">确定</button></div><van-search v-model="search" :placeholder="'搜索'+label"/><div class="collection-select-options norem" role="listbox" :aria-label="label"><button v-for="option in filtered" :key="option.value" type="button" role="option" :aria-selected="selected[0]===option.value" @click="selected=[option.value]"><span>{{option.label}}</span><van-icon v-if="selected[0]===option.value" name="success" /></button><van-empty v-if="!filtered.length" description="没有匹配选项"/></div></template>
   <van-picker v-else v-model="selected" :title="'选择'+label" :columns="options.map(x=>({text:x.label,value:x.value}))" @confirm="confirm" @cancel="open=false" />
  </van-popup>
 </div>
</template>
<style scoped>
.collection-select.norem{flex:1;min-width:0}.collection-select-trigger.norem{width:100%;border:0;border-bottom:1px solid var(--h5-border);background:transparent;display:flex;align-items:center;justify-content:space-between;gap:12px;min-height:44px;padding:8px 0;font:inherit;color:var(--h5-text-primary);text-align:left;cursor:pointer}.collection-select-trigger.placeholder{color:var(--h5-text-placeholder)}.collection-select-trigger:disabled{color:var(--h5-text-secondary);cursor:default}.collection-select-trigger span{overflow-wrap:anywhere}.collection-select-popup.norem{max-width:640px;left:50%;transform:translateX(-50%);width:100%;color:var(--h5-text-primary)}.collection-select-toolbar.norem{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;gap:12px}.collection-select-toolbar button{background:none;border:0;color:var(--h5-text-secondary);font:inherit;min-height:44px}.collection-select-toolbar .confirm{color:var(--h5-primary)}.collection-select-options.norem{max-height:360px;overflow:auto;padding:0 16px 24px}.collection-select-options button{display:flex;justify-content:space-between;align-items:center;width:100%;min-height:48px;padding:12px 14px;border:0;border-radius:10px;background:transparent;color:var(--h5-text-primary);font:inherit;text-align:left}.collection-select-options button[aria-selected=true]{background:var(--h5-primary-opacity);color:var(--h5-primary)}@media(max-width:600px){.collection-select-trigger.norem{font-size:16px}}
</style>
