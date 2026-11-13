import { describe, expect, it } from 'vitest';
import { archiveChanges } from './studentArchiveDiff';
import type { StudentArchive } from './studentArchive';

const snapshot = (values:Record<string,string>, images:StudentArchive['information']['images'] = []) => ({
  information:{values,images,fields:[
    {key:'name',label:'姓名',type:'text',enabled:true,sensitive:false},
    {key:'mobile',label:'手机号',type:'text',enabled:true,sensitive:true},
    {key:'portrait',label:'证件照片',type:'images',enabled:true,sensitive:false},
  ]},
}) as StudentArchive;
describe('archive snapshot differences',()=>{
  it('shows changed and cleared values without reporting unchanged values',()=>{
    const result=archiveChanges(snapshot({name:'原姓名',mobile:'13800000000'}),snapshot({name:'' ,mobile:'13800000000'}),true);
    expect(result.changes.map(c=>[c.key,c.before,c.after])).toEqual([['name','原姓名','']]);
  });
  it('does not infer equality or changes from masked sensitive values',()=>{
    const result=archiveChanges(snapshot({mobile:'138****0000'}),snapshot({mobile:'139****0000'}),false);
    expect(result.hidden).toBe(true);expect(result.changes).toEqual([]);
  });
  it('distinguishes attachment sources with the same id and filename',()=>{
    const image={id:1,fieldKey:'portrait',name:'相同名称.png',contentType:'image/png',size:1,side:''};
    const before=snapshot({},[{...image,source:'registration'}]);
    const after=snapshot({},[{...image,source:'exam'}]);
    expect(archiveChanges(before,after,true).changes[0]?.key).toBe('portrait');
    expect(archiveChanges(before,snapshot({},[image]),true).changes).toEqual([]);
  });
});
