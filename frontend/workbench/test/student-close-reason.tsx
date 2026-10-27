import { createRoot } from 'react-dom/client'
import { StudentServiceSummaryDetail, studentServiceColumnDefinitions, type ServiceStudent } from '../src/components/StudentServiceColumns'
import BusinessTable from '../src/components/BusinessTable'
import '../src/styles/index.css'
const rows: ServiceStudent[] = ['closed', 'active', 'closed'].map((status, i) => ({
  personId: i + 1, serviceRelationId: i + 1, name: `测试学员${i + 1}`,
  services: [{ serviceRelationId: i + 1, status }],
  summary: { closeReason: i === 2 ? undefined : '操作人填写的关闭原因：学员申请结束当前课程服务' },
}))
createRoot(document.getElementById('root')!).render(<>
  <BusinessTable rowKey="serviceRelationId" dataSource={rows} pagination={false} columns={studentServiceColumnDefinitions().filter(c => ['name', 'closeReason'].includes(String(c.key)))} />
  <StudentServiceSummaryDetail student={rows[0]} />
</>)
