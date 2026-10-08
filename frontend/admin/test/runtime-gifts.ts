import { createApp, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import GiftPurchase from '../src/views/zsjos/gift-purchase/index.vue'
createApp({ render: () => h('main', { style: 'padding:16px' }, [h(GiftPurchase)]) })
  .component('ContentWrap', { render() { return h('section', this.$slots.default?.()) } })
  .use(ElementPlus).mount('#app')
