import { useEffect,useState } from 'react'
export function useEducationResource<T>(load:()=>Promise<T>,dependencies:unknown[]){
 const [data,setData]=useState<T>(),[error,setError]=useState(''),[loading,setLoading]=useState(true),[version,setVersion]=useState(0)
 useEffect(()=>{let active=true;setData(undefined);setLoading(true);setError('');load().then(value=>{if(active)setData(value)}).catch(cause=>{if(active)setError(cause instanceof Error?cause.message:'加载失败，请重试')}).finally(()=>{if(active)setLoading(false)});return()=>{active=false}},[...dependencies,version])
 return {data,error,loading,reload:()=>setVersion(x=>x+1)}
}
