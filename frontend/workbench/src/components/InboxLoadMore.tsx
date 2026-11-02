import { useEffect, useRef } from 'react'
import { Button, Spin } from 'antd'

/** Uses the inbox's own scroll root, never the window or a permanently visible footer sentinel. */
export default function InboxLoadMore({page,total,pageSize,loading,error,rootSelector,onMore,onRetry}: {
  page:number;total:number;pageSize:number;loading:boolean;error?:string;rootSelector:string;
  onMore:()=>void;onRetry:()=>void
}) {
  const footer=useRef<HTMLDivElement>(null), requested=useRef('')
  const more=page*pageSize<total
  const requestMore=()=>{const token=`${page}:${total}`;if(loading||error||requested.current===token)return;requested.current=token;onMore()}
  useEffect(()=>{
    if(loading||error||!more)return
    let host=footer.current?.parentElement
    let root:HTMLElement|null=null
    while(host&&!root){root=host.querySelector<HTMLElement>(rootSelector);host=host.parentElement}
    if(!root)return
    const check=()=>{
      if(root.clientHeight<=0)return
      const token=`${page}:${total}`
      const nearEnd=root.scrollHeight>root.clientHeight+1
        ? root.scrollHeight-root.scrollTop-root.clientHeight<=80
        : root.getBoundingClientRect().bottom<=window.innerHeight+80
      if(nearEnd&&requested.current!==token){requested.current=token;onMore()}
    }
    root.addEventListener('scroll',check,{passive:true});window.addEventListener('scroll',check,{passive:true});const resize=new ResizeObserver(check);resize.observe(root)
    const frame=requestAnimationFrame(check)
    return()=>{root.removeEventListener('scroll',check);window.removeEventListener('scroll',check);resize.disconnect();cancelAnimationFrame(frame)}
  },[page,total,loading,error,more,rootSelector,onMore])
  useEffect(()=>{if(!loading)requested.current=''},[page,total,loading,error])
  return <div ref={footer} className="inbox-load-more" role="status" style={{flexShrink:0,textAlign:'center'}}>
    {loading?<Spin size="small"/>:error?<><span>{error}</span><Button size="small" onClick={()=>{requested.current='';onRetry()}}>重试加载</Button></>:more?<Button type="text" size="small" onClick={requestMore}>继续下滑加载</Button>:total>0?'已加载全部':null}
  </div>
}
