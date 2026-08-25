import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import dayjs, { type Dayjs } from 'dayjs'
import { Alert, Button, Col, DatePicker, Empty, Form, Input, Modal, Row, Select, Skeleton, Space, Tag, message } from 'antd'
import FmsProTable from '../../components/fms/FmsProTable'
import { EditOutlined, PlusOutlined, ReloadOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { DICT_TYPE } from '../../constants'
import { api, type SimpleUser } from '../../services/api'
import { fmsConfig, FmsAccountUserLevel } from '../../services/fms'
import { FMS_CURRENCY_CODE, FMS_DEFAULT_SUBJECT_CODE_RULE, FMS_DEFAULT_SUBJECT_LEVEL, FMS_LEDGER_BALANCE_MODE } from '../../services/fms/constants'
import type { FmsAccountSetFullVO, FmsAccountSetVO, FmsAccountUserVO } from '../../services/fms/types'
import { useDict } from '../../services/useDict'
import { useFmsAccountSet } from '../../services/useFmsAccountSet'

type AccountForm = Omit<FmsAccountSetFullVO, 'id'> & { id?: number }
type InitForm = { currencyCode: string; startTime: Dayjs; standard: number; level: number; subjectCodeRule: string; ledgerBalanceMode: number }
const LEVEL_OPTIONS = Array.from({ length: 8 }, (_, index) => ({ value: index + 1, label: `${index + 1} 级` }))

export function toAccountSetStartTime(value: Dayjs): number {
  return value.startOf('month').valueOf()
}

export default function FmsConfigAccountSetPage({ permissions }: { permissions: string[] }) {
  const { accountSetList, loading, error, reloadList } = useFmsAccountSet()
  const standards = useDict(DICT_TYPE.FMS_ACCOUNTING_STANDARD)
  const balanceModes = useDict(DICT_TYPE.FMS_LEDGER_BALANCE_MODE)
  const memberLevels = useDict(DICT_TYPE.FMS_ACCOUNT_USER_LEVEL)
  const commonStatuses = useDict(DICT_TYPE.COMMON_STATUS)
  const canCreate = permissions.includes('fms:config:account-set:create')
  const canUpdate = permissions.includes('fms:config:account-set:update')
  const canInitialize = permissions.includes('fms:config:account-set:initialize')
  const canAuthorize = permissions.includes('fms:config:account-set:authorize')

  const [accountOpen, setAccountOpen] = useState(false)
  const [accountMode, setAccountMode] = useState<'create' | 'update'>('create')
  const [accountSaving, setAccountSaving] = useState(false)
  const [accountForm] = Form.useForm<AccountForm>()

  const openCreate = () => { accountForm.resetFields(); setAccountMode('create'); setAccountOpen(true) }
  const openUpdate = async (row: FmsAccountSetVO) => {
    if (!row.id) return
    accountForm.resetFields(); setAccountMode('update'); setAccountOpen(true); setAccountSaving(true)
    try { accountForm.setFieldsValue(await fmsConfig.accountSet.get(row.id)) }
    catch (e) { message.error(e instanceof Error ? e.message : '账套详情加载失败'); setAccountOpen(false) }
    finally { setAccountSaving(false) }
  }
  const saveAccount = async () => {
    const values = await accountForm.validateFields()
    setAccountSaving(true)
    try {
      if (accountMode === 'create') await fmsConfig.accountSet.create(values)
      else if (values.id) await fmsConfig.accountSet.update({ ...values, id: values.id })
      message.success(accountMode === 'create' ? '账套已创建' : '账套已更新')
      setAccountOpen(false)
      await reloadList()
    } catch (e) { message.error(e instanceof Error ? e.message : '账套保存失败') }
    finally { setAccountSaving(false) }
  }

  const [initRow, setInitRow] = useState<FmsAccountSetVO>()
  const [initSaving, setInitSaving] = useState(false)
  const [initForm] = Form.useForm<InitForm>()
  const openInitialize = (row: FmsAccountSetVO) => {
    initForm.setFieldsValue({
      currencyCode: FMS_CURRENCY_CODE.RMB,
      startTime: dayjs().startOf('month'),
      standard: standards.options[0]?.value,
      level: FMS_DEFAULT_SUBJECT_LEVEL,
      subjectCodeRule: FMS_DEFAULT_SUBJECT_CODE_RULE,
      ledgerBalanceMode: balanceModes.options.find(option => option.value === FMS_LEDGER_BALANCE_MODE.SAME_AS_SUBJECT)?.value ?? balanceModes.options[0]?.value
    })
    setInitRow(row)
  }
  const initialize = async () => {
    if (!initRow?.id) return
    const values = await initForm.validateFields(); setInitSaving(true)
    try {
      await fmsConfig.accountSet.initialize({ ...values, accountSetId: initRow.id, startTime: toAccountSetStartTime(values.startTime) })
      message.success('账套初始化成功'); setInitRow(undefined); await reloadList()
    } catch (e) { message.error(e instanceof Error ? e.message : '初始化失败') }
    finally { setInitSaving(false) }
  }

  const [memberRow, setMemberRow] = useState<FmsAccountSetVO>()
  const [members, setMembers] = useState<FmsAccountUserVO[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [membersSaving, setMembersSaving] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [users, setUsers] = useState<SimpleUser[]>([])
  const [usersLoading, setUsersLoading] = useState(false)
  const [addForm] = Form.useForm<{ userIds: number[]; level: number }>()
  const memberVersion = useRef(0)
  const loadMembers = useCallback(async (accountSetId: number) => {
    const current = ++memberVersion.current; setMembersLoading(true)
    try { const result = await fmsConfig.accountUser.list(accountSetId); if (memberVersion.current === current) setMembers(result) }
    catch (e) { if (memberVersion.current === current) message.error(e instanceof Error ? e.message : '成员加载失败') }
    finally { if (memberVersion.current === current) setMembersLoading(false) }
  }, [])
  useEffect(() => { if (memberRow?.id) void loadMembers(memberRow.id); else setMembers([]) }, [memberRow, loadMembers])
  const openAdd = async () => {
    addForm.resetFields(); setAddOpen(true)
    if (users.length) return
    setUsersLoading(true)
    try { setUsers(await api.simpleUsers()) }
    catch (e) { message.error(e instanceof Error ? e.message : '用户列表加载失败') }
    finally { setUsersLoading(false) }
  }
  const addMembers = async () => {
    const values = await addForm.validateFields()
    const selected = users.filter(user => values.userIds.includes(user.id))
    setMembers(current => [...current, ...selected.map(user => ({
      userId: user.id, nickname: user.nickname, deptName: user.deptName, status: user.status,
      defaultStatus: false, founder: false, level: values.level
    }))])
    setAddOpen(false)
  }
  const saveMembers = async () => {
    if (!memberRow?.id) return
    setMembersSaving(true)
    try {
      await fmsConfig.accountUser.update({ accountSetId: memberRow.id, members: members.map(member => ({ userId: member.userId, level: member.level })) })
      message.success('账套授权已保存'); setMemberRow(undefined); await reloadList()
    } catch (e) { message.error(e instanceof Error ? e.message : '授权保存失败') }
    finally { setMembersSaving(false) }
  }

  const memberColumns = useMemo<ColumnsType<FmsAccountUserVO>>(() => [
    { title: '姓名', render: (_, row) => <Space>{row.nickname || `用户 #${row.userId}`}{row.founder && <Tag color="green">创建人</Tag>}</Space> },
    { title: '部门', dataIndex: 'deptName', width: 140, render: value => value || '-' },
    { title: '手机号码', dataIndex: 'mobile', width: 140, render: value => value || '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: value => <Tag color={value === 0 ? 'success' : 'default'}>{commonStatuses.labels[String(value)] || value}</Tag> },
    { title: '权限级别', width: 140, render: (_, row) => row.founder
      ? <Tag>{memberLevels.labels[String(row.level)] || row.level}</Tag>
      : <Select size="small" value={row.level} options={memberLevels.options} style={{ width: 112 }} onChange={level => setMembers(current => current.map(member => member.userId === row.userId ? { ...member, level } : member))} /> },
    { title: '操作', width: 80, render: (_, row) => row.founder ? null : <Button type="link" danger onClick={() => setMembers(current => current.filter(member => member.userId !== row.userId))}>移出</Button> }
  ], [commonStatuses.labels, memberLevels.labels, memberLevels.options])

  const columns: ColumnsType<FmsAccountSetVO> = [
    { title: '账套名称', dataIndex: 'companyName', render: (name, row) => <Space>{name}{row.defaultStatus && <Tag color="blue">默认</Tag>}</Space> },
    { title: '公司编码', dataIndex: 'companyCode', width: 160 },
    { title: '状态', dataIndex: 'initialized', width: 100, render: initialized => <Tag color={initialized ? 'success' : 'warning'}>{initialized ? '已启用' : '待初始化'}</Tag> },
    { title: '操作', width: 250, render: (_, row) => <Space>
      {canUpdate && row.level === FmsAccountUserLevel.OWNER && <Button type="link" icon={<EditOutlined />} onClick={() => void openUpdate(row)}>编辑</Button>}
      {!row.initialized && canInitialize && row.level !== FmsAccountUserLevel.READ && <Button type="link" icon={<SettingOutlined />} onClick={() => openInitialize(row)}>初始化</Button>}
      {canAuthorize && row.level === FmsAccountUserLevel.OWNER && <Button type="link" icon={<UserOutlined />} onClick={() => setMemberRow(row)}>成员</Button>}
    </Space> }
  ]
  const dictError = standards.error || balanceModes.error

  return <section className="workspace-page fms-page">
    <div className="page-heading"><h4>账套管理</h4><Space>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增账套</Button>}<Button icon={<ReloadOutlined />} onClick={() => void reloadList()}>刷新</Button></Space></div>
    <div className="fms-table-area">{loading && !accountSetList.length ? <Skeleton active /> : error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void reloadList()}>重试</Button>} /> : !accountSetList.length ? <Empty description="暂无账套" /> : <FmsProTable rowKey="id" columns={columns} dataSource={accountSetList} loading={loading} pagination={false} scroll={{ x: 760 }} />}</div>

    <Modal title={accountMode === 'create' ? '新增账套' : '编辑账套'} open={accountOpen} onCancel={() => setAccountOpen(false)} onOk={() => void saveAccount()} confirmLoading={accountSaving} width={900} destroyOnHidden>
      <Form form={accountForm} layout="vertical" disabled={accountSaving}><Form.Item name="id" hidden><Input /></Form.Item><Row gutter={16}>
        <Col xs={24} md={12}><Form.Item name="companyCode" label="公司编码" rules={[{ required: true, message: '请输入公司编码' }]}><Input maxLength={64} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="companyName" label="公司名称" rules={[{ required: true, message: '请输入公司名称' }]}><Input maxLength={255} /></Form.Item></Col>
        <Col span={24}><Form.Item name="companyProfile" label="公司简介"><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="industry" label="所在行业"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="location" label="所在地"><Input maxLength={255} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="legalRepresentative" label="法人代表"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="legalRepresentativeIdNumber" label="法人身份证号"><Input maxLength={255} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="businessLicenseNumber" label="营业执照号"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="organizationCode" label="组织机构代码"><Input maxLength={255} /></Form.Item></Col>
        <Col span={24}><Form.Item name="remark" label="备注"><Input.TextArea rows={2} maxLength={500} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="contactName" label="联系人"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="officeTelephone" label="办公电话"><Input maxLength={32} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="mobile" label="手机号码"><Input maxLength={32} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="faxNumber" label="传真号码"><Input maxLength={32} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="qqNumber" label="QQ 号码"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="email" label="邮箱" rules={[{ type: 'email', message: '邮箱格式不正确' }]}><Input maxLength={255} /></Form.Item></Col>
        <Col xs={24} md={12}><Form.Item name="otherContact" label="其他联系方式"><Input maxLength={255} /></Form.Item></Col><Col xs={24} md={12}><Form.Item name="address" label="详细地址"><Input maxLength={255} /></Form.Item></Col>
      </Row></Form>
    </Modal>

    <Modal title={`开始记账 - ${initRow?.companyName || ''}`} open={Boolean(initRow)} onCancel={() => setInitRow(undefined)} onOk={() => void initialize()} okText="开始记账" okButtonProps={{ disabled: Boolean(dictError) }} confirmLoading={initSaving} width={620} destroyOnHidden>
      {dictError && <Alert type="error" showIcon message="初始化选项加载失败" description={dictError} action={<Button size="small" onClick={() => { void standards.reload(); void balanceModes.reload() }}>重试</Button>} />}
      <Form form={initForm} layout="vertical"><Form.Item name="currencyCode" label="本位币" rules={[{ required: true }]}><Select disabled options={[{ value: FMS_CURRENCY_CODE.RMB, label: '人民币（RMB）' }]} /></Form.Item><Form.Item name="startTime" label="启用期间" rules={[{ required: true, message: '请选择启用期间' }]}><DatePicker picker="month" style={{ width: '100%' }} /></Form.Item><Form.Item name="standard" label="会计制度" rules={[{ required: true }]}><Select options={standards.options} loading={standards.loading} /></Form.Item><Form.Item name="level" label="科目级次" rules={[{ required: true }]}><Select options={LEVEL_OPTIONS} /></Form.Item><Form.Item name="subjectCodeRule" label="科目编码规则" rules={[{ required: true }, { pattern: /^([2-5]-)*[2-5]$/, message: '各级编码长度必须为 2 至 5 位' }]}><Input placeholder="例如：4-2-2-2" /></Form.Item><Form.Item name="ledgerBalanceMode" label="余额方向" rules={[{ required: true }]}><Select options={balanceModes.options} loading={balanceModes.loading} /></Form.Item><Alert type="info" showIcon message="初始化后将建立本位币、财务参数和默认凭证字，启用期间不可随意变更" /></Form>
    </Modal>

    <Modal title={`账套授权 - ${memberRow?.companyName || ''}`} open={Boolean(memberRow)} onCancel={() => setMemberRow(undefined)} onOk={() => void saveMembers()} confirmLoading={membersSaving} width={820} destroyOnHidden>
      {(memberLevels.error || commonStatuses.error) && <Alert type="error" showIcon message={memberLevels.error || commonStatuses.error} action={<Button size="small" onClick={() => { void memberLevels.reload(); void commonStatuses.reload() }}>重试</Button>} />}
      <div style={{ textAlign: 'right', marginBottom: 12 }}><Button type="primary" icon={<PlusOutlined />} disabled={Boolean(memberLevels.error)} onClick={() => void openAdd()}>添加成员</Button></div>
      {membersLoading ? <Skeleton active /> : <FmsProTable rowKey="userId" columns={memberColumns} dataSource={members} pagination={false} size="small" scroll={{ x: 680 }} />}
    </Modal>
    <Modal title="添加成员" open={addOpen} onCancel={() => setAddOpen(false)} onOk={() => void addMembers()} destroyOnHidden><Form form={addForm} layout="vertical"><Form.Item name="userIds" label="选择用户" rules={[{ required: true, message: '请选择用户' }]}><Select mode="multiple" showSearch optionFilterProp="label" loading={usersLoading} options={users.filter(user => !members.some(member => member.userId === user.id)).map(user => ({ value: user.id, label: `${user.nickname}${user.deptName ? `（${user.deptName}）` : ''}` }))} /></Form.Item><Form.Item name="level" label="权限级别" rules={[{ required: true }]}><Select options={memberLevels.options} /></Form.Item></Form></Modal>
  </section>
}
