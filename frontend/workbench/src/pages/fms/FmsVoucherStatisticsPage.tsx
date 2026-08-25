import { useCallback, useRef, useState } from 'react'
import { Button, DatePicker, Space, Table, message } from 'antd'
import { DownloadOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import type { ColumnsType } from 'antd/es/table'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'
import { fmsVoucher, type FmsVoucherStatisticsRow } from '../../services/fms/voucher'
import { formatMoney, buildPeriodFilename } from '../../services/fms/format'
import { saveBlob } from '../../services/download'
import FmsProTable from '../../components/fms/FmsProTable'

export default function FmsVoucherStatisticsPage({ permissions }: { permissions: string[] }) {
  const { accountSet } = useFmsAccountSet()
  const accountSetId = accountSet?.id
  const now = dayjs().format('YYYY-MM')
  const [startMonth, setStartMonth] = useState(now)
  const [endMonth, setEndMonth] = useState(now)
  const [list, setList] = useState<FmsVoucherStatisticsRow[]>([])
  const [loading, setLoading] = useState(false)
  const [exportLoading, setExportLoading] = useState(false)
  const version = useRef(0)

  const getList = useCallback(async () => {
    if (!accountSetId) { setList([]); return }
    const v = ++version.current
    setLoading(true)
    try {
      const result = await fmsVoucher.statistics.list({ accountSetId, startMonth, endMonth })
      if (v !== version.current) return
      setList(result)
    } catch (e) {
      if (v !== version.current) return
      message.error(e instanceof Error ? e.message : '查询失败')
      setList([])
    } finally {
      if (v === version.current) setLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  const lastAccountSetId = useRef<number | undefined>(undefined)
  if (accountSetId !== lastAccountSetId.current) {
    lastAccountSetId.current = accountSetId
    if (accountSetId) setTimeout(() => getList(), 0)
    else setList([])
  }

  const handleExport = useCallback(async () => {
    if (!accountSetId) return
    setExportLoading(true)
    try {
      const blob = await fmsVoucher.statistics.exportExcel({ accountSetId, startMonth, endMonth })
      saveBlob(blob, buildPeriodFilename('凭证汇总表', startMonth, endMonth))
    } catch (e) {
      message.error(e instanceof Error ? e.message : '导出失败')
    } finally {
      setExportLoading(false)
    }
  }, [accountSetId, startMonth, endMonth])

  const totalDebit = list.reduce((sum, r) => sum + Number(r.debitAmount || 0), 0)
  const totalCredit = list.reduce((sum, r) => sum + Number(r.creditAmount || 0), 0)

  const columns: ColumnsType<FmsVoucherStatisticsRow> = [
    { title: '科目编码', dataIndex: 'subjectCode', width: 130 },
    { title: '科目名称', dataIndex: 'subjectName', ellipsis: true },
    { title: '级次', dataIndex: 'level', width: 80, align: 'center' },
    { title: '借方金额', dataIndex: 'debitAmount', width: 160, align: 'right', render: (v: number) => formatMoney(v) },
    { title: '贷方金额', dataIndex: 'creditAmount', width: 160, align: 'right', render: (v: number) => formatMoney(v) }
  ]

  const canExport = permissions.includes('fms:voucher:statistics:export')

  return (
    <section className="workspace-page fms-page">
      <div className="fms-search-area">
        <Space wrap>
          <DatePicker.RangePicker
            picker="month"
            value={[dayjs(startMonth), dayjs(endMonth)]}
            onChange={d => { if (d?.[0] && d?.[1]) { setStartMonth(d[0].format('YYYY-MM')); setEndMonth(d[1].format('YYYY-MM')) } }}
            allowClear={false}
          />
          <Button type="primary" icon={<SearchOutlined/>} onClick={getList}>查询</Button>
          {canExport && <Button icon={<DownloadOutlined/>} loading={exportLoading} onClick={handleExport}>导出</Button>}
        </Space>
      </div>
      <div className="fms-table-area">
        <FmsProTable<FmsVoucherStatisticsRow>
          rowKey={(_, i) => String(i)}
          columns={columns}
          dataSource={list}
          loading={loading}
          pagination={false}
          bordered
          size="small"
          scroll={{ y: 'calc(100vh - 300px)' }}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={3} align="right">合计</Table.Summary.Cell>
              <Table.Summary.Cell index={3} align="right">{formatMoney(totalDebit)}</Table.Summary.Cell>
              <Table.Summary.Cell index={4} align="right">{formatMoney(totalCredit)}</Table.Summary.Cell>
            </Table.Summary.Row>
          )}
        />
      </div>
    </section>
  )
}
