import { SettingOutlined } from '@ant-design/icons'
import {
  Button,
  ColorPicker,
  Divider,
  Drawer,
  Flex,
  Segmented,
  Slider,
  Switch,
  Tooltip,
  Typography,
  theme,
} from 'antd'
import type { AggregationColor } from 'antd/es/color-picker/color'
import { useState } from 'react'
import {
  BACKGROUND_METAS,
  BORDER_RADIUS_OPTIONS,
  DENSITY_OPTIONS,
  FONT_SCALE_OPTIONS,
  GLASS_BLUR_MAX,
  GLASS_BLUR_MIN,
  LAYOUT_MODE_OPTIONS,
  PRESET_COLORS,
  TAB_STYLE_OPTIONS,
  THEME_METAS,
  type BorderRadiusPreset,
  type Density,
  type FontScale,
  type LayoutMode,
  type TabStyle,
} from '../constants'
import { useTheme } from './Theme/ThemeContext'
import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons'

const { Text } = Typography

/** 独立设置 Drawer，集中承载所有主题与布局设置 */
const SettingsDrawer: React.FC = () => {
  const [open, setOpen] = useState(false)
  const { token } = theme.useToken()
  const {
    preset,
    colorPrimary,
    compact,
    customizable,
    background,
    glassOpacity,
    glassBlur,
    density,
    fontScale,
    layoutMode,
    borderRadius,
    headerFixed,
    animation,
    watermark,
    tabs,
    tabStyle,
    isDark,
    setPreset,
    setColorPrimary,
    setCompact,
    setBackground,
    setGlassOpacity,
    setGlassBlur,
    setDensity,
    setFontScale,
    setLayoutMode,
    setBorderRadius,
    setHeaderFixed,
    setAnimation,
    setWatermark,
    setTabs,
    setTabStyle,
    reset,
  } = useTheme()

  return (
    <>
      <Tooltip title="系统设置">
        <Button
          type="text"
          icon={<SettingOutlined />}
          aria-label="系统设置"
          onClick={() => setOpen(true)}
        />
      </Tooltip>
      <Drawer
        title="系统设置"
        placement="right"
        size={320}
        open={open}
        onClose={() => setOpen(false)}
        styles={{ body: { paddingTop: 12 } }}
      >
        {/* ===== 导航布局 ===== */}
        <SectionTitle>导航布局</SectionTitle>
        <div className="settings-layout-grid">
          {LAYOUT_MODE_OPTIONS.map((item) => {
            const active = item.value === layoutMode
            return (
              <button
                key={item.value}
                type="button"
                className={`settings-layout-item${active ? ' active' : ''}`}
                onClick={() => setLayoutMode(item.value)}
                aria-label={item.label}
                aria-pressed={active}
                style={{
                  borderColor: active ? token.colorPrimary : token.colorBorderSecondary,
                }}
              >
                <LayoutIcon mode={item.value} active={active} primaryColor={token.colorPrimary} />
                <span className="settings-layout-label" style={{ color: active ? token.colorPrimary : token.colorTextSecondary }}>{item.label}</span>
              </button>
            )
          })}
        </div>

        <Divider style={{ margin: '20px 0 12px' }} />

        {/* ===== 主题预设 ===== */}
        <SectionTitle>预设主题</SectionTitle>
        <div className="settings-theme-grid">
          {THEME_METAS.map((m) => {
            const active = m.key === preset
            return (
              <Button
                key={m.key}
                size="small"
                onClick={() => setPreset(m.key)}
                style={{
                  height: 'auto',
                  padding: '6px 8px',
                  textAlign: 'left',
                  borderColor: active ? token.colorPrimary : token.colorBorder,
                  borderWidth: active ? 2 : 1,
                  background: active ? token.colorPrimaryBg : token.colorBgContainer,
                }}
              >
                <Flex align="center" gap={6}>
                  <span
                    style={{
                      width: 14,
                      height: 14,
                      flexShrink: 0,
                      borderRadius: 3,
                      background: m.swatch,
                      border: `1px solid ${token.colorBorderSecondary}`,
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {active && <CheckOutlined style={{ color: '#fff', fontSize: 9 }} />}
                  </span>
                  <span style={{ fontSize: 12, fontWeight: active ? 600 : 400 }}>{m.label}</span>
                </Flex>
              </Button>
            )
          })}
        </div>

        {/* ===== 主题色（仅默认主题可自定义） ===== */}
        {customizable && (
          <>
            <Divider style={{ margin: '16px 0 12px' }} />
            <SectionTitle>主题色</SectionTitle>
            <Flex gap={8} wrap style={{ marginTop: 6 }}>
              {PRESET_COLORS.map((c) => {
                const active = c.color.toLowerCase() === colorPrimary.toLowerCase()
                return (
                  <Tooltip key={c.key} title={c.label}>
                    <Button
                      type="text"
                      aria-label={c.label}
                      onClick={() => setColorPrimary(c.color)}
                      style={{
                        width: 26,
                        height: 26,
                        padding: 0,
                        background: c.color,
                        borderRadius: token.borderRadius,
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      {active && <CheckOutlined style={{ color: '#fff', fontSize: 12 }} />}
                    </Button>
                  </Tooltip>
                )
              })}
              <Tooltip title="自定义">
                <ColorPicker
                  value={colorPrimary}
                  onChangeComplete={(c: AggregationColor) => setColorPrimary(c.toHexString())}
                >
                  <Button
                    type="text"
                    aria-label="自定义主题色"
                    icon={<BgColorsOutlined />}
                    style={{
                      width: 26,
                      height: 26,
                      padding: 0,
                      borderRadius: token.borderRadius,
                      border: `1px dashed ${token.colorBorder}`,
                    }}
                  />
                </ColorPicker>
              </Tooltip>
            </Flex>
          </>
        )}

        {/* ===== 背景 ===== */}
        <Divider style={{ margin: '16px 0 12px' }} />
        <SectionTitle>背景</SectionTitle>
        <Flex gap={8} wrap style={{ marginTop: 6 }}>
          {BACKGROUND_METAS
            .filter((b) => b.key === 'theme' || b.dark === undefined || b.dark === isDark)
            .map((b) => {
              const active = b.key === background
              const isFollow = b.key === 'theme'
              return (
                <Tooltip key={b.key} title={b.label}>
                  <Button
                    type="text"
                    aria-label={b.label}
                    onClick={() => setBackground(b.key)}
                    style={{
                      width: 30,
                      height: 30,
                      padding: 0,
                      background: isFollow ? token.colorFillSecondary : b.preview,
                      borderRadius: token.borderRadius,
                      border: active
                        ? `2px solid ${token.colorPrimary}`
                        : `1px solid ${token.colorBorder}`,
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {active ? (
                      <CheckOutlined style={{ color: isFollow ? token.colorText : '#fff', fontSize: 12 }} />
                    ) : isFollow ? (
                      <span style={{ fontSize: 10, color: token.colorTextSecondary }}>自动</span>
                    ) : null}
                  </Button>
                </Tooltip>
              )
            })}
        </Flex>
        {background !== 'theme' && (
          <>
            <Text type="secondary" style={{ fontSize: 12, marginTop: 8, display: 'block' }}>不透明度</Text>
            <Slider
              min={20}
              max={95}
              value={glassOpacity}
              onChange={setGlassOpacity}
              tooltip={{ formatter: (v) => `${v}%` }}
              style={{ marginTop: 4 }}
            />
            <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>背景模糊</Text>
            <Slider
              min={GLASS_BLUR_MIN}
              max={GLASS_BLUR_MAX}
              value={glassBlur}
              onChange={setGlassBlur}
              marks={{ [GLASS_BLUR_MIN]: '关', [GLASS_BLUR_MAX]: '强' }}
              tooltip={{ formatter: (v) => (v ? `${v}px` : '关闭') }}
              style={{ marginTop: 4 }}
            />
          </>
        )}

        <Divider style={{ margin: '16px 0 12px' }} />

        {/* ===== 密度 & 字号 ===== */}
        <SectionTitle>界面密度</SectionTitle>
        <Segmented
          block
          style={{ marginTop: 6 }}
          value={density}
          onChange={(value) => setDensity(value as Density)}
          options={DENSITY_OPTIONS}
        />

        <SectionTitle style={{ marginTop: 16 }}>字号</SectionTitle>
        <Segmented
          block
          style={{ marginTop: 6 }}
          value={fontScale}
          onChange={(value) => setFontScale(value as FontScale)}
          options={FONT_SCALE_OPTIONS}
        />

        {/* ===== 圆角 ===== */}
        <SectionTitle style={{ marginTop: 16 }}>圆角</SectionTitle>
        <Segmented
          block
          style={{ marginTop: 6 }}
          value={borderRadius}
          onChange={(value) => setBorderRadius(value as BorderRadiusPreset)}
          options={BORDER_RADIUS_OPTIONS}
        />

        <Divider style={{ margin: '20px 0 12px' }} />

        {/* ===== 开关设置 ===== */}
        <SettingSwitch label="顶栏固定" description="关闭后顶栏随内容滚动" checked={headerFixed} onChange={setHeaderFixed} />
        <SettingSwitch label="页签模式" description="多页签切换，保留页面状态" checked={tabs} onChange={setTabs} />
        {tabs && (
          <>
            <SectionTitle style={{ marginBottom: 6 }}>页签样式</SectionTitle>
            <Segmented
              block
              value={tabStyle}
              onChange={(value) => setTabStyle(value as TabStyle)}
              options={TAB_STYLE_OPTIONS}
              style={{ marginBottom: 14 }}
            />
          </>
        )}
        <SettingSwitch label="过渡动画" description="关闭后减弱动效" checked={animation} onChange={setAnimation} />
        <SettingSwitch label="水印" description="全局显示当前用户水印" checked={watermark} onChange={setWatermark} />

        <Divider style={{ margin: '20px 0 12px' }} />
        <Button block onClick={reset}>恢复默认</Button>
      </Drawer>
    </>
  )
}

export default SettingsDrawer

/* ========== 内部子组件 ========== */

function SectionTitle({ children, style }: { children: React.ReactNode; style?: React.CSSProperties }) {
  return <Text type="secondary" style={{ fontSize: 12, ...style }}>{children}</Text>
}

function SettingSwitch({
  label,
  description,
  checked,
  onChange,
}: {
  label: string
  description: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <Flex align="center" justify="space-between" style={{ marginBottom: 14 }}>
      <div>
        <Text>{label}</Text>
        <br />
        <Text type="secondary" style={{ fontSize: 12 }}>{description}</Text>
      </div>
      <Switch checked={checked} onChange={onChange} />
    </Flex>
  )
}

/** 导航布局缩略图 SVG */
function LayoutIcon({ mode, active, primaryColor }: { mode: LayoutMode; active: boolean; primaryColor: string }) {
  const fill = active ? primaryColor : '#bfbfbf'
  const bg = active ? `${primaryColor}15` : '#f5f5f5'

  const common = { width: 48, height: 36, viewBox: '0 0 48 36' } as const

  switch (mode) {
    case 'side':
      return (
        <svg {...common}>
          <rect width="48" height="36" rx="3" fill={bg} />
          <rect x="1" y="1" width="10" height="34" rx="2" fill={fill} />
          <rect x="13" y="1" width="14" height="34" rx="2" fill={fill} opacity={0.4} />
        </svg>
      )
    case 'top':
      return (
        <svg {...common}>
          <rect width="48" height="36" rx="3" fill={bg} />
          <rect x="1" y="1" width="46" height="8" rx="2" fill={fill} />
          <rect x="1" y="11" width="14" height="24" rx="2" fill={fill} opacity={0.4} />
        </svg>
      )
    case 'top-only':
      return (
        <svg {...common}>
          <rect width="48" height="36" rx="3" fill={bg} />
          <rect x="1" y="1" width="46" height="8" rx="2" fill={fill} />
        </svg>
      )
    case 'single-sider':
      return (
        <svg {...common}>
          <rect width="48" height="36" rx="3" fill={bg} />
          <rect x="1" y="1" width="14" height="34" rx="2" fill={fill} />
        </svg>
      )
    case 'mini-float':
      return (
        <svg {...common}>
          <rect width="48" height="36" rx="3" fill={bg} />
          <rect x="1" y="1" width="7" height="34" rx="2" fill={fill} />
          <rect x="10" y="6" width="12" height="20" rx="2" fill={fill} opacity={0.3} />
        </svg>
      )
  }
}
