import { ConfigProvider, Table, Button, Input, Card } from 'antd'
import { FONT_SCALE_TABLE, FONT_SCALE_SIZE, FONT_SCALES, DENSITY_SCALE } from '../constants'
const cols=[{title:'姓名',dataIndex:'n'},{title:'部门',dataIndex:'d'},{title:'工号',dataIndex:'i'}]
const data=[{key:1,n:'张三',d:'销售一部',i:'E10023'},{key:2,n:'李四',d:'教研中心',i:'E10088'}]
export default function FontProbe(){
  return <div style={{display:'flex',gap:16,padding:20,background:'#f0f2f5',alignItems:'flex-start'}}>
    {FONT_SCALES.map(t=>{
      const cell=FONT_SCALE_TABLE[t],sp=DENSITY_SCALE.default
      return <ConfigProvider key={t} theme={{token:{fontSize:FONT_SCALE_SIZE[t]},components:{
        Table:{cellPaddingBlock:cell.cellBlock,cellPaddingInline:cell.cellInline},
        Card:{bodyPadding:sp.cardPad,headerPadding:sp.cardPad}}}}>
        <div style={{flex:1}}>
          <div style={{font:'11px monospace',color:'#999',marginBottom:6}}>字号 {t}: fontSize={FONT_SCALE_SIZE[t]}</div>
          <Card size="small" title="员工列表" style={{marginBottom:8}}>
            <div style={{marginBottom:8}}><Button size="small" type="primary">新增</Button> <Input size="small" style={{width:120}} placeholder="搜索" /></div>
            <Table columns={cols} dataSource={data} pagination={false} size="small"/>
          </Card>
        </div>
      </ConfigProvider>
    })}
  </div>
}
