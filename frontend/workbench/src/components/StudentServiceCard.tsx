import { ReadOutlined, TeamOutlined } from '@ant-design/icons'
import type { MyStudent } from '../services/api'
import { assignedName } from './StudentServiceColumns'
import '../styles/components/student-service-card.css'

export default function StudentServiceCard({ service }: { service: MyStudent['services'][number] }) {
  const people = [
    { key: 'planner', label: '学习规划师（班主任）', id: service.ownerUserId, name: service.ownerUserName },
    { key: 'director', label: '编导', id: service.contentDirectorUserId, name: service.contentDirectorUserName },
    { key: 'career', label: '职业规划师', id: service.careerPlannerUserId, name: service.careerPlannerUserName },
    { key: 'operator', label: '运营负责人', id: service.operatorUserId, name: service.operatorUserName },
  ]

  return <section className="lead-card student-service-card" aria-label="当前服务">
    <h4><ReadOutlined aria-hidden />当前服务</h4>
    <div className="student-service-card-course">{service.courseName || service.skuName || '历史课程信息缺失'}</div>
    <div className="student-service-card-class"><TeamOutlined aria-hidden /><span>{service.className || '未分班'}</span></div>
    <dl className="student-service-card-people">
      {people.map(person => <div key={person.key} className="student-service-card-person">
        <dt>{person.label}</dt>
        <dd className={!person.id && !person.name ? 'is-unassigned' : undefined}>{assignedName(person.id, person.name)}</dd>
      </div>)}
    </dl>
  </section>
}
