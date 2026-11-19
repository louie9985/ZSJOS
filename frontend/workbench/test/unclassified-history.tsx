import '../src/styles/index.css'
import { createRoot } from 'react-dom/client'
import { App } from 'antd'
import UnclassifiedHistory from '../src/pages/performance/UnclassifiedHistory'
const permissions=new URLSearchParams(location.search).has('denied')?[]:['query','self','detail'].map(x=>'zsjos:sales-performance:'+x)
const content=<App><UnclassifiedHistory permissions={permissions}/>
</App>
createRoot(document.getElementById('root')!).render(content)
