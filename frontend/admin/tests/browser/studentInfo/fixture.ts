import { ElMessage, ElMessageBox } from 'element-plus'
import type { Config, Save, Version } from '../../../src/api/zsjos/studentInfoFormConfig'
// Test-only adapter. Never imported by the production application.
const labels = ['报名分类','技能等级名称','姓名','性别','年龄','身份证号码','户籍所在地','手机号','现学历层次','毕业院校','毕业时间','工作单位','岗位','您报名的学习目的','邮寄地址','报名老师']
const keys = ['registration_category','skill_level_name','name','gender','age','id_card','household_area','mobile','education_level','school','graduation_time','employer','job','study_purpose','mailing_address','registration_teacher']
const state: Config = { presets: labels.map((label, i) => ({ key: keys[i], label, type: [0,1,3,8].includes(i) ? 'dict' : i === 6 ? 'area' : [13,14].includes(i) ? 'textarea' : 'text', enabled: true, required: i === 2, sort: i*10, sensitive: [5,7].includes(i), dictType: i === 3 ? 'system_user_sex' : 'fixture' })) }
const query = new URLSearchParams(location.search)
export const readonly = query.has('readonly')
let loadFail = query.has('error')
export const checkPermi = () => !readonly
export const getConfig = async () => {
  if (loadFail) { loadFail = false; throw new Error('测试加载失败') }
  return structuredClone(state)
}
export const getSimpleDictTypeList = async () => [{ type: 'fixture', name: '测试字典' }, { type: 'system_user_sex', name: '性别字典' }]
export const saveDraft = async (request: Save): Promise<Version> => {
  state.draft = { id: 1, versionNo: (state.published?.versionNo ?? 0) + 1, revision: request.revision + 1, status: 'DRAFT', fields: JSON.parse(JSON.stringify(request.fields)) }
  return structuredClone(state.draft)
}
export const publish = async () => { state.published = { ...state.draft!, status: 'PUBLISHED' }; state.draft = undefined; return true }
export const useMessage = () => ({ success: ElMessage.success, error: ElMessage.error, confirm: ElMessageBox.confirm })
