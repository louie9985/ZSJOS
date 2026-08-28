import { useEffect, useState } from 'react'
import { Alert, Button, Card, Input, Typography } from 'antd'
import { resolveAuthPlatform } from '../constants'
import { api } from '../services/api'

export default function LoginPage({ onLogin, initialError = '' }: { onLogin: () => void; initialError?: string }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => setError(initialError), [initialError])

  const login = async () => {
    setLoading(true)
    setError('')
    try {
      const platform = resolveAuthPlatform()
      await api.login(username, password, platform)
      onLogin()
    } catch (loginError: any) {
      setError(loginError.response?.data?.msg || loginError.message || '登录失败')
    } finally {
      setLoading(false)
    }
  }

  return <div className="login-page"><Card className="login-card">
    <div className="login-mark">ZSJOS</div>
    <Typography.Title level={2}>员工工作台</Typography.Title>
    <Typography.Paragraph type="secondary">统一账号登录</Typography.Paragraph>
    {error && <Alert className="form-alert" type="error" showIcon message={error}/>}
    <Input placeholder="用户名" size="large" value={username} onChange={event => setUsername(event.target.value)} className="login-input"/>
    <Input.Password placeholder="密码" size="large" value={password} onChange={event => setPassword(event.target.value)} onPressEnter={login} className="login-input"/>
    <Button type="primary" block size="large" loading={loading} onClick={login}>登录</Button>
  </Card></div>
}
