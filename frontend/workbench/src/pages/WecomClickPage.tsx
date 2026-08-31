import { useEffect, useState } from 'react'
import { Button, Card, Result, Spin } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { APP_ROUTES } from '../constants'
import { api, getAuthAccessToken } from '../services/api'

const errorText = (error: unknown) => error instanceof Error ? error.message : '企业微信消息链接解析失败'

export default function WecomClickPage({
  authPlatform,
  onNeedLogin
}: {
  authPlatform: 'PC' | 'MOBILE'
  onNeedLogin: (targetPath: string) => void
}) {
  const location = useLocation()
  const navigate = useNavigate()
  const [error, setError] = useState('')

  useEffect(() => {
    const ticket = new URLSearchParams(location.search).get('ticket')?.trim()
    if (!ticket) {
      setError('企业微信消息链接缺少票据')
      return
    }
    let cancelled = false
    api.resolveWecomClickTicket(ticket)
      .then(async target => {
        if (cancelled) return
        if (target.audience !== 'ADMIN') {
          setError('该企业微信消息不属于员工工作台')
          return
        }
        const targetPath = target.targetPath || target.fallbackPath || APP_ROUTES.ALL_MESSAGES
        if (!getAuthAccessToken(authPlatform)) {
          onNeedLogin(targetPath)
          return
        }
        navigate(targetPath, { replace: true })
      })
      .catch(cause => {
        if (!cancelled) setError(errorText(cause))
      })
    return () => { cancelled = true }
  }, [authPlatform, location.search, navigate, onNeedLogin])

  if (error) {
    return <section className="workspace-page wecom-click-page">
      <Card>
        <Result
          status="warning"
          title="企业微信消息链接不可用"
          subTitle={error}
          extra={<Button type="primary" onClick={() => navigate(APP_ROUTES.ALL_MESSAGES, { replace: true })}>查看消息中心</Button>}
        />
      </Card>
    </section>
  }
  return <section className="workspace-page wecom-click-page">
    <Card><Spin tip="正在打开企业微信消息..." /></Card>
  </section>
}
