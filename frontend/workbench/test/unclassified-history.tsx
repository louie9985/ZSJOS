import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import UnclassifiedHistory from '../src/pages/performance/UnclassifiedHistory'
import DeliveryUnclassifiedHistory from '../src/pages/deliveryPerformance/UnclassifiedHistory'
const domain=new URLSearchParams(location.search).get('domain')==='delivery'?'delivery':'sales'
const permissions=new URLSearchParams(location.search).has('denied')?[]:['query','self','detail'].map(x=>'zsjos:'+domain+'-performance:'+x)
const content=<App>{domain==='delivery'?<DeliveryUnclassifiedHistory permissions={permissions}/>:<UnclassifiedHistory permissions={permissions}/>}
</App>
createRoot(document.getElementById('root')!).render(content)
