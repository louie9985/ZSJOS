import { describe, expect, it } from 'vitest'
import { InboxPageBuffer } from './inboxAppend'
type Row={id:number;value:string}
const key=(row:Row)=>row.id
describe('inbox page accumulation',()=>{
  it('preserves patches and removals made by detail actions while appending',()=>{
    const pages=new InboxPageBuffer<Row>()
    pages.merge('all',1,[{id:1,value:'old'},{id:2,value:'removed'}],key)
    expect(pages.merge('all',2,[{id:3,value:'next'}],key,[{id:1,value:'updated'}])).toEqual([{id:1,value:'updated'},{id:3,value:'next'}])
  })
  it('preserves first-page records and updates duplicates without duplicating cards',()=>{
    const pages=new InboxPageBuffer<Row>()
    pages.merge('all',1,[{id:1,value:'old'},{id:2,value:'two'}],key)
    expect(pages.merge('all',2,[{id:1,value:'new'},{id:3,value:'three'}],key)).toEqual([{id:1,value:'new'},{id:2,value:'two'},{id:3,value:'three'}])
  })
  it('retries replace a page while keeping previous pages',()=>{
    const pages=new InboxPageBuffer<Row>()
    pages.merge('all',1,[{id:1,value:'one'}],key)
    pages.merge('all',2,[{id:2,value:'two'}],key)
    expect(pages.merge('all',2,[{id:2,value:'updated'}],key)).toEqual([{id:1,value:'one'},{id:2,value:'updated'}])
  })
  it('resets accumulated results on refresh and scope changes',()=>{
    const pages=new InboxPageBuffer<Row>()
    pages.merge('all',1,[{id:1,value:'one'}],key);pages.merge('all',2,[{id:2,value:'two'}],key)
    expect(pages.merge('all',1,[{id:3,value:'refreshed'}],key)).toEqual([{id:3,value:'refreshed'}])
    expect(pages.merge('filtered',1,[{id:4,value:'filtered'}],key)).toEqual([{id:4,value:'filtered'}])
  })
  it('keys service records by service relationship rather than Person',()=>{
    const pages=new InboxPageBuffer<{person:number;service:number}>()
    pages.merge('students',1,[{person:1,service:10}],row=>row.service)
    expect(pages.merge('students',2,[{person:1,service:11}],row=>row.service)).toHaveLength(2)
  })
})
