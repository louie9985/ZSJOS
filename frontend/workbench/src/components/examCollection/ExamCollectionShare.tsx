import { useState } from 'react';
import { Alert, App, Button, Popover, Typography } from 'antd';
import { CopyOutlined, QrcodeOutlined } from '@ant-design/icons';
import ResourceLink from '../ResourceLink';
import StudentInfoQrCard from '../StudentInfoQrCard';
import '../../styles/pages/exam-collection.css';

export default function ExamCollectionShare({url,open=true}:{url:string;open?:boolean}) {
  const [error,setError]=useState('');
  const {message}=App.useApp();
  const copy=()=>void navigator.clipboard.writeText(url).then(()=>message.success('链接已复制')).catch(()=>message.error('复制失败，请通过链接菜单复制地址'));
  // The token-bearing URL is only needed for copying; showing host/path keeps the row to one line.
  return <section className="exam-collection-share" aria-label="收集链接与二维码">
    <div className="exam-collection-share-main">
      <Typography.Text strong>统一收集链接</Typography.Text>
      <span className="exam-collection-share-link"><ResourceLink href={url}/></span>
      <span className="exam-collection-share-actions">
        <Button size="small" icon={<CopyOutlined/>} onClick={copy}>复制链接</Button>
        <Popover trigger="click" placement="bottomRight" destroyOnHidden content={<div className="exam-collection-share-qr">
          {error&&<Alert type="error" title={error}/>}
          <StudentInfoQrCard compact url={url} title="考试资料收集表" subtitle="扫码填写，提交本次考试资料" notice={open?'重复填写会更新档案并保留历史':'收集已结束，停止接收新提交'} onError={setError}/>
        </div>}>
          <Button size="small" icon={<QrcodeOutlined/>}>二维码</Button>
        </Popover>
      </span>
    </div>
    <Typography.Text type="secondary" className="exam-collection-share-hint">{open?'所有学员使用同一链接，重复填写会更新档案并保留历史。':'收集已结束，链接展示结束提示并停止接收新提交。'}</Typography.Text>
  </section>;
}
