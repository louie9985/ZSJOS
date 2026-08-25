import HrmProTable from '../components/HrmProTable'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Alert, Button, Descriptions, Drawer, Empty, Select, Skeleton, Space, Tag, message } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { api, type HrmInsuranceRecord } from '../services/api'
import { fmtAmount, yearOptions } from '../services/hrm'
import type { ColumnsType } from 'antd/es/table'

/** 员工端我的社保。 */
export default function HrmMyInsurancePage() {
  const [year, setYear] = useState(new Date().getFullYear())
  const [records, setRecords] = useState<HrmInsuranceRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const version = useRef(0)

  const [detail, setDetail] = useState<HrmInsuranceRecord>()
  const [detailLoading, setDetailLoading] = useState(false)

  const load = useCallback(async (targetYear: number) => {
    const current = ++version.current
    setLoading(true); setError('')
    try {
      const list = await api.hrm.portal.insurance.recordList({ year: targetYear })
      if (current !== version.current) return
      setRecords(list)
    } catch (e) {
      if (current === version.current) setError(e instanceof Error ? e.message : '社保记录加载失败')
    } finally {
      if (current === version.current) setLoading(false)
    }
  }, [])

  useEffect(() => { void load(year) }, [load, year])

  const openDetail = async (row: HrmInsuranceRecord) => {
    setDetailLoading(true); setDetail(undefined)
    try {
      const result = await api.hrm.portal.insurance.recordGet(row.id)
      setDetail(result)
    } catch (e) { message.error(e instanceof Error ? e.message : '详情加载失败') }
    finally { setDetailLoading(false) }
  }

  const columns: ColumnsType<HrmInsuranceRecord> = [
    { title: '年份', dataIndex: 'year', width: 100, align: 'center' },
    { title: '月份', dataIndex: 'month', width: 90, align: 'center', render: (value: number) => `${value} 月` },
    { title: '社保方案', dataIndex: 'schemeName', width: 160, ellipsis: true, render: (value?: string) => value || '-' },
    { title: '参保城市', dataIndex: 'schemeCity', width: 120, render: (value?: string) => value || '-' },
    { title: '个人社保', dataIndex: 'personalInsuranceAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '个人公积金', dataIndex: 'personalProvidentFundAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '公司社保', dataIndex: 'corporateInsuranceAmount', width: 130, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
    { title: '参保状态', dataIndex: 'status', width: 90, align: 'center', render: (value?: number) => value != null ? <Tag>{value === 1 ? '参保' : '未参保'}</Tag> : '-' },
    {
      title: '操作', width: 90, align: 'center', fixed: 'right',
      render: (_, row) => <Button type="link" size="small" onClick={() => void openDetail(row)}>明细</Button>
    }
  ]

  const content = loading && !records.length ? <Skeleton active paragraph={{ rows: 8 }}/>
    : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void load(year)}>重试</Button>}/>
      : !records.length ? <Empty description={`${year} 年暂无社保记录`}/>
        : <HrmProTable<HrmInsuranceRecord> advanced persistenceKey="my-insurance" onReload={() => load(year)} rowKey="id" columns={columns} dataSource={records} loading={loading}
          pagination={false} scroll={{ x: 1000 }}/>

  return <section className="workspace-page hrm-page hrm-my-insurance-page">
    <div className="page-heading">
      <Select value={year} onChange={setYear} options={yearOptions(10)} style={{ width: 110 }}/>
      <Button icon={<ReloadOutlined/>} onClick={() => void load(year)}>刷新</Button>
    </div>
    <div className="hrm-table-area">{content}</div>

    <Drawer title={detail ? `社保明细 · ${detail.year} 年 ${detail.month} 月` : ''} width="min(840px, 96vw)"
      open={!!detail} onClose={() => setDetail(undefined)} destroyOnClose>
      {detailLoading && !detail ? <Skeleton active paragraph={{ rows: 8 }}/> : detail && <>
        <Descriptions className="hrm-summary" size="small" column={2} bordered items={[
          { key: 'scheme', label: '社保方案', children: detail.schemeName || '-' },
          { key: 'city', label: '参保城市', children: detail.schemeCity || '-' },
          { key: 'personalInsurance', label: '个人社保', children: detail.personalInsuranceAmount != null ? `¥${fmtAmount(detail.personalInsuranceAmount)}` : '-' },
          { key: 'personalFund', label: '个人公积金', children: detail.personalProvidentFundAmount != null ? `¥${fmtAmount(detail.personalProvidentFundAmount)}` : '-' },
          { key: 'corporateInsurance', label: '公司社保', children: detail.corporateInsuranceAmount != null ? `¥${fmtAmount(detail.corporateInsuranceAmount)}` : '-' },
          { key: 'corporateFund', label: '公司公积金', children: detail.corporateProvidentFundAmount != null ? `¥${fmtAmount(detail.corporateProvidentFundAmount)}` : '-' }
        ]}/>
        <h4 className="hrm-drawer-subtitle">缴费明细</h4>
        {detail.projects?.length
          ? <HrmProTable rowKey={project => (project.schemeProjectId ?? project.name ?? '0').toString()} size="small" pagination={false} columns={[
            { title: '项目', dataIndex: 'name', render: (value?: string) => value || '-' },
            { title: '基数', dataIndex: 'baseAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
            { title: '企业比例', dataIndex: 'corporateRate', width: 90, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
            { title: '个人比例', dataIndex: 'personalRate', width: 90, align: 'right', render: (value?: number) => value != null ? `${value}%` : '-' },
            { title: '企业金额', dataIndex: 'corporateAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' },
            { title: '个人金额', dataIndex: 'personalAmount', width: 120, align: 'right', render: (value?: number) => value != null ? `¥${fmtAmount(value)}` : '-' }
          ]} dataSource={detail.projects}/>
          : <Empty description="暂无缴费细项"/>}
      </>}
    </Drawer>
  </section>
}
