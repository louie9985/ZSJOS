import { useCallback, useEffect, useRef } from 'react';
import { Modal } from 'antd';
import { useInRouterContext } from 'react-router-dom';
import { useApprovalLeaveGuard } from '../bpm/useApprovalLeaveGuard';

function RouteGuard({check,blocked}:{check:()=>Promise<boolean>;blocked:()=>boolean}) {
  useApprovalLeaveGuard(check,blocked); return null;
}

/** Reuse the workbench's tab/anchor/POP guard instead of creating another navigation mechanism. */
export function useExamLeaveGuard(dirty:boolean,busy:boolean,description='填写内容尚未提交，离开将丢失本次修改。') {
  const [modal,holder]=Modal.useModal();
  const inRouter=useInRouterContext();
  const state=useRef({dirty,busy,description});state.current={dirty,busy,description};
  const pending=useRef<Promise<boolean>|undefined>(undefined);
  const mounted=useRef(true);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false}},[]);
  const blocked=useCallback(()=>state.current.dirty||state.current.busy,[]);
  const check=useCallback(()=>{
    if(state.current.busy)return Promise.resolve(false);
    if(!state.current.dirty)return Promise.resolve(true);
    if(pending.current)return pending.current;
    const promise=Promise.resolve(modal.confirm({title:'放弃未保存的内容？',content:state.current.description,okText:'离开',cancelText:'继续填写'}))
      .then(allowed=>Boolean(allowed)&&mounted.current&&!state.current.busy).finally(()=>{if(pending.current===promise)pending.current=undefined});
    pending.current=promise;return promise;
  },[modal]);
  useEffect(()=>{const beforeUnload=(event:BeforeUnloadEvent)=>{if(blocked()){event.preventDefault();event.returnValue=''}};
    window.addEventListener('beforeunload',beforeUnload);return()=>window.removeEventListener('beforeunload',beforeUnload);
  },[blocked]);
  return {check,guard:<>{holder}{inRouter&&<RouteGuard check={check} blocked={blocked}/>}</>};
}
