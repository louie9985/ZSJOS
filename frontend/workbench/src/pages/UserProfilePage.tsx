import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Avatar, Button, Card, Descriptions, Form, Input, Modal, Radio, Skeleton, Space, Switch, Tabs, Upload, message } from 'antd'
import { UploadOutlined, WechatOutlined } from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import EmployeeAvatar from '../components/EmployeeAvatar'
import DateTimeText from '../components/DateTimeText'
import { APP_ROUTES } from '../constants'
import { api, type SocialUser, type UserProfile } from '../services/api'

const WECOM_TYPE = 30
const errorText = (error: unknown) => error instanceof Error ? error.message : '请求失败，请稍后重试'

export const parseWecomCallback = (search: string) => {
  const params = new URLSearchParams(search)
  const type = Number(params.get('type'))
  const code = params.get('code')?.trim() || ''
  const state = params.get('state')?.trim() || ''
  return { type, code, state, hasValidSocialCallback: type === WECOM_TYPE && Boolean(code && state) }
}

export const buildWecomRedirectUri = (origin: string) =>
  `${origin}${APP_ROUTES.USER_PROFILE}?type=${WECOM_TYPE}`

export const isLatestAvatarUpload = (requestId: number, latestRequestId: number) =>
  requestId === latestRequestId

export default function UserProfilePage({ onUserChange }: { onUserChange: (user: { nickname: string; avatar?: string }) => void }) {
  const location = useLocation()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<UserProfile>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [social, setSocial] = useState<SocialUser>()
  const [socialLoading, setSocialLoading] = useState(false)
  const [binding, setBinding] = useState(false)
  const [wecomSaving, setWecomSaving] = useState(false)
  const avatarUploadRequest = useRef(0)
  const [form] = Form.useForm()
  const [passwordForm] = Form.useForm()

  const load = async () => {
    setLoading(true); setError('')
    try { const data = await api.userProfile(); setProfile(data); form.setFieldsValue(data) } catch (e) { setError(errorText(e)) } finally { setLoading(false) }
  }
  const loadSocial = async () => {
    setSocialLoading(true)
    try { setSocial((await api.boundSocialUsers()).find(item => item.type === WECOM_TYPE)) } catch (e) { message.error(errorText(e)) } finally { setSocialLoading(false) }
  }
  useEffect(() => { void load(); void loadSocial() }, [])
  useEffect(() => () => { avatarUploadRequest.current += 1 }, [])
  useEffect(() => {
    const callback = parseWecomCallback(location.search)
    if (!callback.code && !callback.state && callback.type !== WECOM_TYPE) return
    if (!callback.hasValidSocialCallback) { message.error('企业微信授权信息不完整，请重新绑定'); navigate(APP_ROUTES.USER_PROFILE, { replace: true }); return }
    setBinding(true)
    void api.bindSocialUser(callback.type, callback.code, callback.state)
      .then(() => { message.success('企业微信绑定成功'); void loadSocial() })
      .catch(e => message.error(errorText(e)))
      .finally(() => { setBinding(false); navigate(APP_ROUTES.USER_PROFILE, { replace: true }) })
  }, [location.search])

  const saveProfile = async (values: UserProfile) => {
    try { await api.updateUserProfile({ nickname: values.nickname, email: values.email, mobile: values.mobile, sex: values.sex, avatar: values.avatar }); const data = await api.userProfile(); setProfile(data); form.setFieldsValue(data); onUserChange(data); message.success('个人资料已保存') } catch (e) { message.error(errorText(e)) }
  }
  const upload = async (file: File) => {
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { message.error('仅支持 JPG、PNG、WebP 图片'); return false }
    const requestId = ++avatarUploadRequest.current
    try {
      const avatar = await api.uploadAvatar(file)
      if (!isLatestAvatarUpload(requestId, avatarUploadRequest.current)) return false
      form.setFieldValue('avatar', avatar)
      setProfile(current => current ? { ...current, avatar } : current)
      message.success('头像已上传，保存资料后生效')
    } catch (e) {
      if (isLatestAvatarUpload(requestId, avatarUploadRequest.current)) message.error(errorText(e))
    }
    return false
  }
  const bind = async () => {
    if (binding) return
    setBinding(true)
    try { const redirectUri = buildWecomRedirectUri(window.location.origin); window.location.href = await api.socialAuthRedirect(WECOM_TYPE, redirectUri) } catch (e) { setBinding(false); message.error(errorText(e)) }
  }
  const unbind = () => {
    if (!social || socialLoading) return
    Modal.confirm({ title: '解绑企业微信？', content: '解绑后将取消当前账号与企业微信的关联，并停止企业微信推送。', okText: '确认解绑', cancelText: '取消', onOk: async () => { await api.unbindSocialUser(WECOM_TYPE, social.openid); setSocial(undefined); setProfile(current => current ? { ...current, wecomEnabled: false } : current); message.success('企业微信已解绑') } })
  }
  const updateWecomEnabled = async (enabled: boolean) => {
    if (!social) {
      message.warning('请先绑定企业微信')
      return
    }
    setWecomSaving(true)
    try {
      await api.updateUserWecomEnabled(enabled)
      setProfile(current => current ? { ...current, wecomEnabled: enabled } : current)
      message.success(enabled ? '已开启企业微信推送' : '已关闭企业微信推送')
    } catch (e) {
      message.error(errorText(e))
    } finally {
      setWecomSaving(false)
    }
  }
  const infoItems = useMemo(() => [{ key: 'username', label: '用户名', children: profile?.username || '—' }, { key: 'dept', label: '部门', children: profile?.dept?.name || '—' }, { key: 'posts', label: '岗位', children: profile?.posts?.map(post => post.name).join('、') || '—' }, { key: 'createTime', label: '创建日期', children: <DateTimeText value={profile?.createTime} emptyText="—" precision="second"/> }], [profile])
  if (loading) return <section className="workspace-page user-profile-page"><Card><Skeleton active /></Card></section>
  if (error) return <section className="workspace-page user-profile-page"><Alert type="error" showIcon message="个人资料加载失败" description={error} action={<Button onClick={() => void load()}>重试</Button>} /></section>
  return <section className="workspace-page user-profile-page"><Card title="个人中心"><Tabs items={[
    { key: 'profile', label: '个人资料', children: <div className="user-profile-grid"><Card bordered><div className="profile-avatar-editor"><EmployeeAvatar avatar={profile?.avatar} name={profile?.nickname} size={96} /><Upload showUploadList={false} beforeUpload={upload}><Button icon={<UploadOutlined />}>更换头像</Button></Upload></div><Form form={form} layout="vertical" onFinish={saveProfile}><Form.Item name="avatar" hidden><Input /></Form.Item><Form.Item label="昵称" name="nickname" rules={[{ required: true, max: 30, message: '请输入 1-30 个字符的昵称' }]}><Input /></Form.Item><Form.Item label="手机号" name="mobile" rules={[{ pattern: /^1[3-9]\d{9}$/, message: '请输入有效的 11 位手机号' }]}><Input /></Form.Item><Form.Item label="邮箱" name="email" rules={[{ type: 'email', message: '请输入有效邮箱' }]}><Input /></Form.Item><Form.Item label="性别" name="sex"><Radio.Group options={[{ value: 0, label: '未知' }, { value: 1, label: '男' }, { value: 2, label: '女' }]} optionType="button" /></Form.Item><Button type="primary" htmlType="submit">保存资料</Button></Form></Card><Card title="账户信息" bordered><Descriptions column={1} items={infoItems} /></Card></div> },
    { key: 'password', label: '修改密码', children: <Card bordered><Form form={passwordForm} layout="vertical" onFinish={async values => { try { await api.updateUserPassword(values.oldPassword, values.newPassword); passwordForm.resetFields(); message.success('密码修改成功') } catch (e) { message.error(errorText(e)) } }}><Form.Item label="旧密码" name="oldPassword" rules={[{ required: true, min: 4, max: 100 }]}><Input.Password /></Form.Item><Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8, max: 20, message: '新密码为 8-20 位' }, { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: '新密码必须同时包含字母和数字' }]}><Input.Password /></Form.Item><Form.Item label="确认新密码" name="confirmPassword" dependencies={['newPassword']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator: (_, value) => !value || value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')) })]}><Input.Password /></Form.Item><Button type="primary" htmlType="submit">修改密码</Button></Form></Card> },
    { key: 'social', label: '第三方账号', children: <Card bordered loading={socialLoading}><Space direction="vertical" size={12}><Space><WechatOutlined style={{ color: '#07c160' }} /><strong>企业微信</strong>{social ? <><Avatar src={social.avatar} size="small" /><span>{social.nickname || social.openid}</span><Button danger onClick={unbind}>解绑</Button></> : <><span>未绑定</span><Button type="primary" loading={binding} onClick={bind}>绑定企业微信</Button></>}</Space><Space><span>接收企业微信推送</span><Switch disabled={!social} loading={wecomSaving} checked={Boolean(social && profile?.wecomEnabled)} onChange={updateWecomEnabled} /></Space></Space></Card> }
  ]} /></Card></section>
}
