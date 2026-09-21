import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { App, Button } from 'antd'
import ThemeProvider from '../src/components/Theme/ThemeProvider'
import SalesOrderDetailCards from '../src/components/SalesOrderDetailCards'
import { api, type SalesOrder } from '../src/services/api'
import '../src/styles/index.css'
api.dictDataByType = async () => { throw new Error('History must not query dictionaries') }
const base = {
  id: 1, orderNo: '测试订单-历史快照', personId: 1, orderType: 'first_purchase', status: 'effective',
  studentName: '测试学员', buyerName: '测试购买方', totalAmount: 100,
  studentNature: 'retired', paymentMethod: 'deleted', servicePeriod: 'old', studentSource: 'old', feeMode: 'old',
  studentNatureLabelSnapshot: '录单时学员性质', servicePeriodLabelSnapshot: '录单时服务周期',
  studentSourceLabelSnapshot: '录单时来源', feeModeLabelSnapshot: '录单时收费方式', paymentMethodLabelSnapshot: '已删除字典的支付方式',
  submitterUserName: '提交时姓名', formalSalesUserName: '成交时姓名',
  registrationApproval: { status: 'approved', reviewerUserName: '审批时姓名', endTime: 1 },
  financeApproval: { status: 'approved', reviewerUserName: '财务审批时姓名', endTime: 1 },
  items: [], paymentVouchers: [], submittedAt: 1, customerPaidAt: 1, version: 0
} as SalesOrder
function Fixture() {
  const [missing, setMissing] = useState(false)
  const value = missing ? { ...base, studentNatureLabelSnapshot: undefined, servicePeriodLabelSnapshot: undefined,
    studentSourceLabelSnapshot: undefined, feeModeLabelSnapshot: undefined, paymentMethodLabelSnapshot: undefined,
    registrationApproval: { status: 'approved' as const }, financeApproval: { status: 'approved' as const },
    historyMissingFields: { leadProfile: 'history_not_recorded' as const } } : base
  return <ThemeProvider><App><Button onClick={() => setMissing(!missing)}>切换历史缺失</Button><SalesOrderDetailCards mode="mine" order={value}/></App></ThemeProvider>
}
createRoot(document.getElementById('root')!).render(<Fixture/> )
