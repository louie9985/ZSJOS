import { useRef, useState } from 'react'
import { Button, QRCode, Space, message } from 'antd'
import { CopyOutlined, DownloadOutlined } from '@ant-design/icons'

export default function StudentInfoQrCard({url,onError}:{url:string;onError:(message:string)=>void}) {
 const card=useRef<HTMLDivElement>(null);const [saving,setSaving]=useState(false);const [copying,setCopying]=useState(false)
 const renderImage=async()=>{
  const qr=card.current?.querySelector('canvas');if(!qr)throw Error('二维码未就绪')
  const logo=new Image();logo.src='/collection/logo.png';await logo.decode()
  const canvas=document.createElement('canvas');canvas.width=800;canvas.height=1080;const ctx=canvas.getContext('2d');if(!ctx)throw Error('无法创建图片')
  ctx.fillStyle='#ffffff';ctx.fillRect(0,0,800,1080);ctx.drawImage(logo,110,72,580,580*logo.naturalHeight/logo.naturalWidth)
  ctx.textAlign='center';ctx.fillStyle='#18324d';ctx.font='600 38px "Microsoft YaHei",sans-serif';ctx.fillText('学员报名信息收集表',400,302)
  ctx.font='23px "Microsoft YaHei",sans-serif';ctx.fillStyle='#607389';ctx.fillText('扫码填写，完成本次报名资料提交',400,354)
  ctx.fillStyle='#f4f8fc';ctx.fillRect(139,382,522,522);ctx.fillStyle='#ffffff';ctx.fillRect(157,400,486,486);ctx.imageSmoothingEnabled=false;ctx.drawImage(qr,169,412,462,462)
  ctx.font='22px "Microsoft YaHei",sans-serif';ctx.fillStyle='#607389';ctx.fillText('微信扫一扫 · 手机与电脑均可填写',400,958);ctx.font='20px "Microsoft YaHei",sans-serif';ctx.fillText('请核对后提交，每份表单仅可提交一次',400,1004)
  return new Promise<Blob>((resolve,reject)=>canvas.toBlob(value=>value?resolve(value):reject(Error('导出失败')),'image/png'))
 }
 const save=async()=>{onError('');setSaving(true);try{
  const blob=await renderImage();const src=URL.createObjectURL(blob);const a=document.createElement('a');a.href=src;a.download='学员报名信息收集表.png';a.click();setTimeout(()=>URL.revokeObjectURL(src),1000)
 }catch(e){onError(e instanceof Error?e.message:'保存图片失败，请重试')}finally{setSaving(false)}}
 const copy=async()=>{
  if(!navigator.clipboard?.write||typeof ClipboardItem==='undefined'){onError('当前浏览器不支持复制图片，请保存图片后发送');return}
  onError('');setCopying(true)
  try{
   const image=renderImage()
   // Start the clipboard write within the click gesture; some browsers reject writes after image decoding.
   // Also handle rendering rejection if the browser refuses the write before consuming the image promise.
   void image.catch(()=>undefined)
   await navigator.clipboard.write([new ClipboardItem({'image/png':image})])
   message.success('图片已复制，可粘贴发送')
  }catch{onError('复制图片失败，请允许剪贴板访问后重试，或保存图片后发送')}finally{setCopying(false)}
 }
 return <div style={{width:'100%'}}><div ref={card} aria-label="学员报名信息收集表二维码卡片" style={{background:'#fff',border:'1px solid #e3ebf3',borderRadius:16,padding:'24px 16px',textAlign:'center',color:'#18324d',maxWidth:390,margin:'0 auto 16px'}}>
  <img src="/collection/logo.png" alt="中世健教育" style={{width:'88%',maxWidth:290,height:'auto',display:'block',margin:'0 auto 20px'}} />
  <div style={{fontSize:22,fontWeight:600,marginBottom:8}}>学员报名信息收集表</div><div style={{fontSize:13,color:'#607389',marginBottom:20}}>扫码填写，完成本次报名资料提交</div>
  <div style={{background:'#f4f8fc',borderRadius:16,padding:14,width:'fit-content',margin:'auto'}}><QRCode value={url} size={232} color="#142e47" bgColor="#fff" bordered={false} /></div>
  <div style={{fontSize:13,color:'#607389',marginTop:20}}>微信扫一扫 · 手机与电脑均可填写</div><div style={{fontSize:12,color:'#8492a3',marginTop:8}}>请核对后提交，每份表单仅可提交一次</div>
 </div><Space orientation="vertical" style={{width:'100%'}}><Button block type="primary" icon={<CopyOutlined/>} loading={copying} disabled={saving||copying} onClick={()=>void copy()}>复制图片</Button><Button block icon={<DownloadOutlined/>} loading={saving} disabled={saving||copying} onClick={()=>void save()}>保存二维码图片</Button></Space></div>
}
