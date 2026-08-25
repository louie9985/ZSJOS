import { useEffect, useState } from 'react'
import { Button, Card, Result, Steps } from 'antd'
import { useNavigate } from 'react-router-dom'
import { api } from '../services/api'

/** 员工端入职引导：未绑定员工档案时展示开通步骤。 */
export default function HrmOpeningGuidePage() {
  const navigate = useNavigate()
  const [bindStatus, setBindStatus] = useState(true)

  useEffect(() => {
    api.hrm.portal.employee.getBindStatus()
      .then(setBindStatus)
      .catch(() => setBindStatus(true))
  }, [])

  if (bindStatus) {
    return <section className="workspace-page hrm-page hrm-opening-guide-page">
      <Result status="success" title="员工端已开通"
        subTitle="你的账号已绑定员工档案，可以直接使用员工端功能"/>
    </section>
  }

  return <section className="workspace-page hrm-page hrm-opening-guide-page">
    <Card bordered={false} className="hrm-table-area">
      <Result status="info" title="当前账号尚未开通员工端"
        subTitle="请先在员工管理中创建员工档案，并将绑定用户设置为当前后台账号。"/>
      <Steps current={0} items={[
        { title: '进入员工档案', description: '前往员工管理的员工档案列表' },
        { title: '新增并绑定账号', description: '新增员工时绑定当前后台账号' },
        { title: '保存员工档案', description: '完善必填信息并保存后即可进入员工端' }
      ]}/>
    </Card>
  </section>
}
