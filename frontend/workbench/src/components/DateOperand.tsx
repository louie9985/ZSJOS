import { DatePicker, InputNumber, Select, Space } from 'antd'
import dayjs from 'dayjs'
import { bases, units, isRelativeTime, initialRelativeTime } from './relativeTime'
export default function DateOperand({value,onChange}: {value:unknown;onChange:(value:unknown)=>void}) {
 const relative = isRelativeTime(value)
 return <Space direction="vertical" style={{width:'100%'}}>
  <Select aria-label="时间类型" style={{width:'100%'}} value={relative?'relative':'fixed'} options={[{value:'fixed',label:'固定时间'},{value:'relative',label:'相对时间点'}]} onChange={mode=>onChange(mode==='relative'?initialRelativeTime():undefined)}/>
  {relative ? <>
   <Select aria-label="时间基准" style={{width:'100%'}} value={value.base} options={bases} onChange={base=>onChange({...value,base})}/>
   <Space wrap><InputNumber aria-label="时间偏移量" min={-10000} max={10000} precision={0} value={value.offset} onChange={offset=>onChange({...value,offset:offset??0})}/><Select aria-label="偏移单位" value={value.unit} options={units} onChange={unit=>onChange({...value,unit})}/></Space>
   <small>北京时间；负数为之前，正数为之后，每次查询重新计算。</small>
  </> : <DatePicker aria-label="固定日期时间" showTime value={value == null ? null : dayjs(Number(value))} onChange={date=>onChange(date?.valueOf())}/>}
 </Space>
}
