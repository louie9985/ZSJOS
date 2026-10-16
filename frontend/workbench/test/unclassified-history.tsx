import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import UnclassifiedHistory from '../src/pages/performance/UnclassifiedHistory'
import { DeliveryPerformanceModule } from '../src/pages/performance/PerformanceModule'
const domain=new URLSearchParams(location.search).get('domain')==='delivery'?'delivery':'sales'
const permissions=new URLSearchParams(location.search).has('denied')?[]:['query','self','detail'].map(x=>'zsjos:'+domain+'-performance:'+x)
const content=<App><UnclassifiedHistory permissions={permissions}/>
</App>
createRoot(document.getElementById('root')!).render(domain==='delivery'?<DeliveryPerformanceModule>{content}</DeliveryPerformanceModule>:content)
