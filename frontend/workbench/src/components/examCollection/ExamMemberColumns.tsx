import type { ProColumns } from '@ant-design/pro-components';
import { Tag, Tooltip } from 'antd';
import type { ExamPlannerMember } from '../../services/examCollection';

type MemberIdentity = Partial<Pick<ExamPlannerMember, 'studentName' | 'mobile' | 'archiveName' | 'archiveMobile' | 'archiveIdCard' | 'className' | 'absenceReason'>>;

export function examMemberColumns<T extends MemberIdentity>(isFilled: (row: T) => boolean): ProColumns<T>[] {
  return [
    { title: '学员·姓名', dataIndex: 'studentName', width: 140 },
    { title: '学员·手机号', dataIndex: 'mobile', width: 160 },
    { title: '档案·姓名', dataIndex: 'archiveName', width: 140 },
    { title: '档案·手机号', dataIndex: 'archiveMobile', width: 160 },
    { title: '档案身份证号', dataIndex: 'archiveIdCard', width: 200 },
    { title: '通知时班级', dataIndex: 'className', width: 220 },
    { title: '本次填写', key: 'filled', width: 120, render: (_, row) => row.absenceReason != null
      ? <Tooltip title={`不参加原因：${row.absenceReason || '未填写'}`}><Tag color="default">不参加</Tag></Tooltip>
      : <Tag color={isFilled(row) ? 'success' : 'warning'}>{isFilled(row) ? '已填写' : '未填写'}</Tag> },
  ];
}
