import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import OrderHistoryFacts from '../src/views/zsjos/components/OrderHistoryFacts.vue'
createApp({ render: () => h('div', [h('h1', '中视界订单历史快照验收'),
 h(OrderHistoryFacts, { order: { submitterUserName: '提交时姓名', formalSalesUserName: '成交时姓名', paymentMethodLabelSnapshot: '已删除字典的支付方式' } })]) }).use(ElementPlus).mount('#app')
