import { ElMessageBox } from 'element-plus'
export const useLocaleStore = () => ({getCurrentLocale:{lang:'zh-CN'}})
export const useUpload = () => ({})
export const checkPermi = () => !location.search.includes('readonly')
export const useMessage = () => ({success:()=>{}, confirm:(s:string)=>ElMessageBox.confirm(s)})
const initial = {revision:0,publishedVersion:0,draftBody:'<p>{{学员姓名}} {{联系电话}} {{课程名称}} {{实际费用}}</p>'}
const read = (id:string) => JSON.parse(localStorage.getItem('agreement.' + id) || JSON.stringify(initial))
const write = (id:string, data:object) => localStorage.setItem('agreement.' + id,JSON.stringify(data))
export default {
 get: ({url,params}:any) => url.endsWith('/products') ? ['产品甲','产品乙'].map(name=>({name,productRef:name,categoryPath:[{name:'分类'}]})) : read(params.productRef),
 put: ({data}:any) => write(data.productRef,{...read(data.productRef),revision:data.revision+1,draftBody:data.body}),
 post: ({data}:any) => write(data.productRef,{...read(data.productRef),revision:data.revision+1,publishedVersion:data.revision+1})
}
