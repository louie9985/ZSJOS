import { useState } from 'react'
import { Form, InputNumber, Modal, Radio, Space } from 'antd'
import type { FmsVoucher } from '../../services/fms/voucher'
import { buildVoucherPrintHtml, printHtml, type VoucherPrintSetting, DEFAULT_VOUCHER_PRINT_SETTING } from '../../services/fms/print'

interface Props {
  open: boolean
  vouchers: FmsVoucher[]
  companyName: string
  onClose: () => void
}

const STORAGE_KEY_PREFIX = 'fmsVoucherPrintSetting:'

function loadSetting(accountSetId?: number): VoucherPrintSetting {
  if (!accountSetId) return { ...DEFAULT_VOUCHER_PRINT_SETTING }
  try {
    const raw = localStorage.getItem(`${STORAGE_KEY_PREFIX}${accountSetId}`)
    if (raw) return { ...DEFAULT_VOUCHER_PRINT_SETTING, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { ...DEFAULT_VOUCHER_PRINT_SETTING }
}

function saveSetting(accountSetId: number, setting: VoucherPrintSetting) {
  try {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}${accountSetId}`, JSON.stringify(setting))
  } catch { /* ignore */ }
}

export default function FmsVoucherPrintSettings({ open, vouchers, companyName, onClose }: Props) {
  const accountSetId = vouchers[0]?.accountSetId
  const [setting, setSetting] = useState<VoucherPrintSetting>(() => loadSetting(accountSetId))

  const updateSetting = (patch: Partial<VoucherPrintSetting>) => {
    setSetting(prev => ({ ...prev, ...patch }))
  }

  const handleOk = () => {
    if (accountSetId) saveSetting(accountSetId, setting)
    const html = buildVoucherPrintHtml({ companyName, vouchers, setting })
    printHtml(html)
    onClose()
  }

  return (
    <Modal
      title="凭证打印设置"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      okText="打印"
      cancelText="取消"
      width={720}
      destroyOnClose
    >
      <Form layout="vertical" style={{ marginTop: 16 }}>
        <Form.Item label="纸张类型">
          <Radio.Group value={setting.paperType} onChange={e => updateSetting({ paperType: e.target.value })}>
            <Radio value="A4">A4</Radio>
            <Radio value="B5">B5</Radio>
            <Radio value="CUSTOM">自定义纸张</Radio>
          </Radio.Group>
          {setting.paperType === 'CUSTOM' && (
            <Space style={{ marginTop: 8 }}>
              <span>宽度</span>
              <InputNumber value={setting.width} min={1} controls={false} style={{ width: 72 }} onChange={v => updateSetting({ width: v ?? 250 })} />
              <span>mm</span>
              <span style={{ marginLeft: 16 }}>高度</span>
              <InputNumber value={setting.height} min={1} controls={false} style={{ width: 72 }} onChange={v => updateSetting({ height: v ?? 176 })} />
              <span>mm</span>
            </Space>
          )}
        </Form.Item>
        <Form.Item label="打印方向">
          <Radio.Group value={setting.orientation} onChange={e => updateSetting({ orientation: e.target.value })}>
            <Radio value="portrait">纵向</Radio>
            <Radio value="landscape">横向</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="附加边距">
          <Space>
            <span>左</span>
            <InputNumber value={setting.marginLeft} min={0} controls={false} style={{ width: 72 }} onChange={v => updateSetting({ marginLeft: v ?? 0 })} />
            <span>mm</span>
            <span style={{ marginLeft: 16 }}>上</span>
            <InputNumber value={setting.marginTop} min={0} controls={false} style={{ width: 72 }} onChange={v => updateSetting({ marginTop: v ?? 0 })} />
            <span>mm</span>
          </Space>
        </Form.Item>
        <Form.Item label="字体大小">
          <Space>
            <InputNumber value={setting.fontSize} min={12} max={24} controls={false} style={{ width: 72 }} onChange={v => updateSetting({ fontSize: v ?? 16 })} />
            <span>像素</span>
          </Space>
        </Form.Item>
      </Form>
    </Modal>
  )
}
