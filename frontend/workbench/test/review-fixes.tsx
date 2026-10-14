import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { App, ConfigProvider, Button } from 'antd'
import AttachmentCard from '../src/components/AttachmentCard'
import CalendarColorEditor, { useCalendarPalette } from '../src/components/CalendarColorEditor'
import { calendarColors } from '../src/services/calendarColors'
import { PointsAttachments } from '../src/components/PointsAttachments'
import { pointsApi } from '../src/services/pointsRedemptionApi'
import { contentReviewStudentApi } from '../src/services/contentReviewStudent'
import { http } from '../src/services/api'
const metrics = { content:0, metadata:0, points:0, marker:false, paletteReads:0, safe:false, revoked:0 }
const revoke = URL.revokeObjectURL.bind(URL)
URL.revokeObjectURL = url => {metrics.revoked++;revoke(url)}
let oldRead: ((value:any)=>void) | undefined
const old = {presets:[],colors:[{id:'a',name:'旧色',color:'#112233'}]}
http.defaults.adapter = async config => {
 const content=config.url?.endsWith('file-content')
 if(content) metrics.content++; else metrics.metadata++
 return {config,headers:{},status:200,statusText:'OK',data:content
  ? metrics.safe ? new Blob(['%PDF-1.4\n%%EOF'],{type:'application/pdf'}) : new Blob(['<script>parent.reviewMetrics.marker=true</scr'+'ipt>'],{type:'text/html'})
  : {code:0,data:{id:1,name:'伪装.pdf',type:'application/pdf',size:100}}}
}
calendarColors.get=async()=>{metrics.paletteReads++; if(metrics.paletteReads===1)return old; return new Promise(resolve=>{oldRead=resolve})}
calendarColors.save=async(_,data)=>({presets:[],colors:[{id:'a',...data}]})
pointsApi.get=async()=>{metrics.points++;await new Promise(r=>setTimeout(r,100));return {attachments:pointFiles} as any}
const pointFiles=Array.from({length:10},(_,i)=>({id:i+1,name:`文档${i}.txt`,url:'https://example.invalid/file',type:'text/plain'}))
Object.assign(window,{reviewMetrics:metrics,resolveOldPalette:()=>oldRead?.(old)})
function Fixture(){const palette=useCalendarPalette('PERSONAL');const [visible,setVisible]=useState(true)
 return <div style={{padding:16,maxWidth:800}}><Button onClick={()=>setVisible(v=>!v)}>切换附件</Button>{visible&&<AttachmentCard name="伪装.pdf" load={()=>contentReviewStudentApi.file(1,2,'profile',1)}/>}
 <div data-testid="palette">{palette.palette.colors.map(c=>c.name).join(',')}</div>
 <Button onClick={palette.reload}>延迟刷新</Button><CalendarColorEditor value="#112233" type="PERSONAL" canManage state={palette}/>
 <PointsAttachments requestId={1} files={pointFiles}/></div>}
createRoot(document.getElementById('root')!).render(<ConfigProvider><App><Fixture/></App></ConfigProvider>)
