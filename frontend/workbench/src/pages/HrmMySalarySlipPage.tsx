import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Badge, Button, Card, DatePicker, Descriptions, Drawer, Empty, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmSalarySlip } from '../services/api'
import { useDict } from '../services/useDict'
import { HRM_DICT, SLIP_READ_STATUS, fmtAmount } from '../services/hrm'
import SalaryOptionTable from '../components/SalaryOptionTable'
import dayjs from 'dayjs'

function fmtTime(value?: number | null) { return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-' }

/**
 * 员工端我的工资条。列表为卡片流而非表格——工资条是一份份「单据」，
 * 卡片能直接露出实发金额与未读状态，比表格行更贴合阅读场景。
 */
export default function HrmMySalarySlipPage() {
  const [slips, setSlips] = useState<HrmSalarySlip[]>([])
  const [unread, setUnread] = useState(0)
  const [reminder, setReminder] = useState<string>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const listVersion = useRef(0)

  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null)
  const [detail, setDetail] = useState<HrmSalarySlip>()

  const optionType = useDict(HRM_DICT.SALARY_OPTION_TYPE)

  const load = useCallback(async (period: [dayjs.Dayjs, dayjs.Dayjs] | null) => {
    const version = ++listVersion.current
    setLoading(true); setError('')
    try {
      const [list, summary] = await Promise.all([
        api.hrm.portal.salary.slipList(period
          ? { startMonth: period[0].format('YYYY-MM'), endMonth: period[1].format('YYYY-MM') }
          : undefined),
        api.hrm.portal.salary.unreadSummary()
      ])
      if (version !== listVersion.current) return
      setSlips(list); setUnread(summary.unreadCount); setReminder(summary.reminder)
    } catch (e) {
      if (version === listVersion.current) setError(e instanceof Error ? e.message : '工资条加载失败')
    } finally {
      if (version === listVersion.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load(range) }, [load, range])

  /** 打开详情即视为已读，与 admin 端行为一致 */
  const openDetail = async (slip: HrmSalarySlip) => {
    setDetail(slip)
    if (slip.readStatus === SLIP_READ_STATUS.UNREAD) {
      try {
        await api.hrm.portal.salary.read([slip.id])
        setSlips(current => current.map(item => item.id === slip.id ? { ...item, readStatus: SLIP_READ_STATUS.READ } : item))
        setUnread(count => Math.max(0, count - 1))
      } catch (e) { message.error(e instanceof Error ? e.message : '标记已读失败') }
    }
  }

  const markAllRead = async () => {
    const unreadIds = slips.filter(slip => slip.readStatus === SLIP_READ_STATUS.UNREAD).map(slip => slip.id)
    if (!unreadIds.length) return
    try {
      await api.hrm.portal.salary.read(unreadIds)
      setSlips(current => current.map(item => ({ ...item, readStatus: SLIP_READ_STATUS.READ })))
      setUnread(0)
      message.success('已全部标记为已读')
    } catch (e) { message.error(e instanceof Error ? e.message : '标记已读失败') }
  }

  const content = loading && !slips.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(range)}>重试</Button>}/>
      : !slips.length ? <Empty description="暂无工资条"/>
        : <div className="hrm-slip-grid">
          {slips.map(slip => {
            const isUnread = slip.readStatus === SLIP_READ_STATUS.UNREAD
            return <Card key={slip.id} hoverable size="small" className="hrm-slip-card" onClick={() => void openDetail(slip)}
              title={<Space>
                <span>{slip.year} 年 {slip.month} 月</span>
                {isUnread && <Badge status="processing" text="未读"/>}
              </Space>}>
              <div className="hrm-slip-amount">¥{fmtAmount(slip.realPaySalary)}</div>
              <div className="hrm-slip-caption">实发工资</div>
              {slip.remark && <div className="hrm-slip-caption">{slip.remark}</div>}
            </Card>
          })}
        </div>

  return <section className="workspace-page hrm-page hrm-my-salary-slip-page">
    {reminder && unread > 0 && <Alert className="hrm-inline-alert" type="info" showIcon message={reminder}/>}
    <div className="page-heading">
      <Space wrap>
        <DatePicker.RangePicker picker="month" value={range} onChange={value => setRange(value as [dayjs.Dayjs, dayjs.Dayjs] | null)}
          placeholder={['开始月份', '结束月份']}/>
        {unread > 0 && <Tag color="processing">{unread} 份未读</Tag>}
      </Space>
      <Space>
        {unread > 0 && <Button onClick={() => void markAllRead()}>全部标记已读</Button>}
        <Button icon={<ReloadOutlined/>} onClick={() => void load(range)}>刷新</Button>
      </Space>
    </div>
    {optionType.error && <Alert className="hrm-inline-alert" type="warning" showIcon message={`工资项类型字典加载失败：${optionType.error}`} action={<Button size="small" onClick={optionType.reload}>重试</Button>}/>}
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail ? `${detail.year} 年 ${detail.month} 月工资条` : ''} width="min(840px, 96vw)"
      open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detail && <>
        <Descriptions className="hrm-summary" size="small" column={2} bordered items={[
          { key: 'real', label: '实发工资', children: <strong>¥{fmtAmount(detail.realPaySalary)}</strong> },
          { key: 'period', label: '所属月份', children: `${detail.year} 年 ${detail.month} 月` },
          { key: 'create', label: '发放时间', children: fmtTime(detail.createTime) },
          { key: 'remark', label: '备注', children: detail.remark || '-' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">工资项明细</h4>
        {detail.options?.length
          ? <SalaryOptionTable options={detail.options} typeLabels={optionType.labels}/>
          : <Empty description="暂无工资项明细"/>}
      </>}
    </Drawer>
  </section>
}
