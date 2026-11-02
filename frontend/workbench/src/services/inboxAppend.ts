import { useCallback, useRef } from 'react'

export function mergeInboxRows<T>(pages: T[][], key: (row:T)=>string|number): T[] {
  const merged=new Map<string|number,T>()
  pages.forEach(rows=>rows.forEach(row=>merged.set(key(row),row)))
  return [...merged.values()]
}

export class InboxPageBuffer<T> {
  private scope=''
  private pages=new Map<number,T[]>()
  merge(scope:string,page:number,rows:T[],key:(row:T)=>string|number,current?:T[]) {
    if(this.scope!==scope||page===1){this.scope=scope;this.pages.clear()}
    else if(current){
      const visible=new Map(current.map(row=>[key(row),row]))
      // Detail commands can patch/remove a visible row between page responses.
      for(const [number,items] of this.pages) this.pages.set(number,items.filter(row=>visible.has(key(row))).map(row=>visible.get(key(row))!))
    }
    this.pages.set(page,rows)
    return mergeInboxRows([...this.pages.entries()].sort((a,b)=>a[0]-b[0]).map(([,items])=>items),key)
  }
}

/** Keeps page ownership local; loading/authorization and stale-response guards remain with the caller. */
export function useInboxPageMerge<T>(scope: string, enabled: boolean, key: (row:T)=>string|number) {
  const state=useRef(new InboxPageBuffer<T>())
  const keyRef=useRef(key);keyRef.current=key
  return useCallback((page:number,rows:T[],current?:T[])=>{
    if(!enabled)return rows
    return state.current.merge(scope,page,rows,keyRef.current,current)
  },[scope,enabled])
}
