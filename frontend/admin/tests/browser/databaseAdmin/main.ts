import { createApp, defineComponent, h } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import DatabaseAdmin from '../../../src/views/infra/databaseAdmin/index.vue'
import Dialog from '../../../src/components/Dialog/src/Dialog.vue'
import { lastCommand, readonly } from './fixture'
import './style.css'

const app = createApp(
  defineComponent({
    setup: () => () => [
      h(DatabaseAdmin),
      h('output', { 'data-testid': 'last-command' }, lastCommand.value)
    ]
  })
)
app.use(ElementPlus)
app.component('Dialog', Dialog)
app.component(
  'ContentWrap',
  defineComponent({
    setup:
      (_, { slots }) =>
      () =>
        h('section', slots.default?.())
  })
)
app.component('Icon', defineComponent({ setup: () => () => h('span') }))
app.component('Pagination', defineComponent({ setup: () => () => h('div') }))
app.directive('hasPermi', {
  mounted: (element) => {
    if (readonly) element.remove()
  }
})
app.mount('#app')
