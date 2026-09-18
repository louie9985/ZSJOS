import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Button, ConfigProvider, Space } from 'antd'
import PaymentQrCard from '../src/components/PaymentQrCardModal'
import type { PurchaseIntent } from '../src/services/api'

const fixture: PurchaseIntent = {
  id: 1, purchaseIntentNo: 'PI-DEMO-20260918', collectionMode: 'online_link', purchaseType: 'first',
  personId: 1, draft: {}, itemSnapshotJson: '[]', totalAmount: 1280, currency: 'CNY', version: 1,
  paymentLocked: true, paymentStatus: 'waiting', displayStatus: 'pending_payment',
  paymentUrl: `https://example.com/pay/DEMO-20260918?token=${'abc123'.repeat(32)}`,
  paymentExpiresAt: Date.now() + 3600000,
}
function Fixture() {
  const [intent, setIntent] = useState(fixture)
  return <ConfigProvider><h1>付款二维码验收（模拟数据，无业务请求）</h1><Space wrap>
    <PaymentQrCard intent={intent}/>
    <Button onClick={() => setIntent({ ...fixture, paymentExpiresAt: Date.now() + 5000 })}>五秒后过期</Button>
    <Button onClick={() => setIntent({ ...fixture, paymentStatus: 'paid' })}>已到账</Button>
    <Button onClick={() => setIntent({ ...fixture, paymentCancelPending: true })}>取消待确认</Button>
    <Button onClick={() => setIntent(fixture)}>恢复有效</Button>
  </Space></ConfigProvider>
}
if (import.meta.env.DEV) createRoot(document.getElementById('root')!).render(<Fixture />)
