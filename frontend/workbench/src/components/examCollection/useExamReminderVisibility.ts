import { useLayoutEffect, useState } from 'react';
import { useOverlayCoordinator } from '../OverlayCoordinator';

/** Legacy modal hosts do not all register with the coordinator. This is presentation-only deferral. */
export function useExamReminderVisibility(ready:boolean) {
  const {businessOverlayCount}=useOverlayCoordinator();
  const [foreignOverlay,setForeignOverlay]=useState(true);
  useLayoutEffect(()=>{
    if(!ready){setForeignOverlay(true);return;}
    let frame=0;
    const update=()=>{
      frame=0;
      const focus=document.activeElement;
      const editing=focus instanceof HTMLElement&&!focus.closest('.exam-collection-notice-overlay')
        &&focus.matches('iframe,input:not([type=checkbox]):not([type=radio]):not([type=button]),textarea,[contenteditable=true]');
      const visible=[...document.querySelectorAll<HTMLElement>('.ant-modal-wrap,.ant-drawer-open,[role=dialog],dialog[open]')].some(element=>{
        if(element.closest('.exam-collection-notice-overlay'))return false;
        const style=getComputedStyle(element);
        return style.display!=='none'&&style.visibility!=='hidden'&&element.getClientRects().length>0;
      });
      setForeignOverlay(document.visibilityState!=='visible'||editing||visible);
    };
    const schedule=()=>{if(!frame)frame=requestAnimationFrame(update)};
    update();
    const observer=new MutationObserver(schedule);observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['class','style','aria-hidden','open']});
    document.addEventListener('focusin',schedule);document.addEventListener('focusout',schedule);document.addEventListener('visibilitychange',schedule);window.addEventListener('resize',schedule);
    return()=>{observer.disconnect();if(frame)cancelAnimationFrame(frame);document.removeEventListener('focusin',schedule);document.removeEventListener('focusout',schedule);document.removeEventListener('visibilitychange',schedule);window.removeEventListener('resize',schedule)};
  },[ready]);
  return ready&&businessOverlayCount===0&&!foreignOverlay;
}
