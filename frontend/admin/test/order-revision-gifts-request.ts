import { ElMessage } from 'element-plus'
const scenario = new URLSearchParams(location.search).get('scenario') || 'default'
const historical = { code: 'old', name: '历史礼品名称', path: ['历史分类', '历史礼品名称'] }
export const fixture = { scenario, submissions: [] as unknown[], fail: scenario === 'failure', catalogRequests: 0 }
Object.assign(window, { orderGiftFixture: fixture })
export const useMessage = () => ({ warning: ElMessage.warning, success: ElMessage.success })
export const getSimpleDictDataList = async () => []
export default {
  async get({ url }: { url: string }) {
    if (url === '/zsjos/gift-config/list') {
      fixture.catalogRequests++
      if (fixture.scenario === 'loading') await new Promise(resolve => setTimeout(resolve, 1200))
      if (fixture.scenario === 'forbidden') throw { code: 403 }
      if (fixture.fail) throw new Error('fixture catalog unavailable')
      return fixture.scenario === 'empty' ? [] : [
        { id: 1, code: 'old', name: '当前已改名', status: 0, sort: 0 },
        { id: 2, code: 'new', name: '新礼品', status: 0, sort: 1 }
      ]
    }
    if (url.startsWith('/zsjos/sales-order/management/')) return {
      id: 1, orderNo: 'FIXTURE-ORDER', studentName: '合成学员', studentMobile: '13800000000', studentWechatId: 'fixture-wechat',
      status: 'revision_required', canRevise: true, canTerminate: false,
      collectionMode: 'offline_paid', transactionLocked: false, items: [], paymentVouchers: [],
      studentNatureLabelSnapshot: '历史性质', servicePeriodLabelSnapshot: '历史周期', studentSourceLabelSnapshot: '历史来源',
      feeModeLabelSnapshot: '历史收费', paymentMethodLabelSnapshot: '历史支付',
      giftItemsInvalid: scenario === 'invalid',
      giftItems: scenario === 'empty' ? '[]' : JSON.stringify([scenario === 'legacy' ? JSON.stringify(historical) : historical]),
      giftShippingAddress: '原礼品邮寄地址'
    }
    return []
  },
  async put({ data }: { data: unknown }) { fixture.submissions.push(JSON.parse(JSON.stringify(data))); return 2 }
}
