import { ElMessageBox } from 'element-plus'
export const useLocaleStore = () => ({getCurrentLocale:{lang:'zh-CN'}})
export const useUpload = () => ({})
export const checkPermi = () => !location.search.includes('readonly')
export const useMessage = () => ({success:()=>{}, confirm:(s:string)=>ElMessageBox.confirm(s)})
const initial = {revision:0,publishedVersion:0,draftBody:'<p>{{学员姓名}} {{联系电话}} {{课程名称}} {{实际费用}}</p>'}
const read = (id:number) => JSON.parse(localStorage.getItem('agreement.' + id) || JSON.stringify(initial))
const write = (id:number, data:object) => localStorage.setItem('agreement.' + id,JSON.stringify(data))
export default {
 get: ({url,params}:any) => url.endsWith('/categories') ? [{id:1,name:'父分类',children:[{id:11,name:'分类甲'},{id:12,name:'分类乙'}]}] : read(params.categoryId),
 put: ({data}:any) => write(data.categoryId,{...read(data.categoryId),revision:data.revision+1,draftBody:data.body}),
 post: ({data}:any) => write(data.categoryId,{...read(data.categoryId),revision:data.revision+1,publishedVersion:data.revision+1})
}
