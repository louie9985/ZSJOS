import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons';
import {
  Button,
  ColorPicker,
  Divider,
  Flex,
  Popover,
  Switch,
  Tooltip,
  Typography,
  theme,
} from 'antd';
import type { AggregationColor } from 'antd/es/color-picker/color';
import { BACKGROUND_METAS, PRESET_COLORS, THEME_METAS } from '../../constants';
import { useTheme } from './ThemeContext';

const { Text } = Typography;

/** 顶栏主题切换：图标 + Popover 面板（13 套预设主题网格 + 默认主题的色/紧凑微调） */
const ThemeSwitcher: React.FC = () => {
  const { token } = theme.useToken();
  const {
    preset,
    colorPrimary,
    compact,
    customizable,
    background,
    setPreset,
    setColorPrimary,
    setCompact,
    setBackground,
    reset,
  } = useTheme();

  const content = (
    <div style={{ width: 320 }}>
      {/* 13 套预设主题网格 */}
      <Text type="secondary" style={{ fontSize: 12 }}>
        预设主题
      </Text>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(2, 1fr)',
          gap: 8,
          marginTop: 8,
        }}
      >
        {THEME_METAS.map((m) => {
          const active = m.key === preset;
          return (
            <Button
              key={m.key}
              onClick={() => setPreset(m.key)}
              style={{
                height: 'auto',
                padding: '8px 10px',
                textAlign: 'left',
                borderColor: active ? token.colorPrimary : token.colorBorder,
                borderWidth: active ? 2 : 1,
                background: active
                  ? token.colorPrimaryBg
                  : token.colorBgContainer,
              }}
            >
              <Flex align="center" gap={8}>
                {/* 代表色块（暗色主题加浅色描边以便在浅底可见） */}
                <span
                  style={{
                    width: 18,
                    height: 18,
                    flexShrink: 0,
                    borderRadius: 4,
                    background: m.swatch,
                    border: `1px solid ${token.colorBorderSecondary}`,
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  {active && (
                    <CheckOutlined style={{ color: '#fff', fontSize: 11 }} />
                  )}
                </span>
                <span
                  style={{
                    fontSize: 13,
                    fontWeight: active ? 600 : 400,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {m.label}
                </span>
              </Flex>
            </Button>
          );
        })}
      </div>

      {/* 主题色 + 紧凑度（仅“默认”主题可微调） */}
      {customizable && (
        <>
          <Divider style={{ margin: '16px 0' }} />
          <Text type="secondary" style={{ fontSize: 12 }}>
            主题色
          </Text>
          <Flex gap={8} wrap style={{ marginTop: 8 }}>
            {PRESET_COLORS.map((c) => {
              const active =
                c.color.toLowerCase() === colorPrimary.toLowerCase();
              return (
                <Tooltip key={c.key} title={c.label}>
                  <Button
                    type="text"
                    aria-label={c.label}
                    onClick={() => setColorPrimary(c.color)}
                    style={{
                      width: 28,
                      height: 28,
                      padding: 0,
                      background: c.color,
                      borderRadius: token.borderRadius,
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    {active && (
                      <CheckOutlined style={{ color: '#fff', fontSize: 14 }} />
                    )}
                  </Button>
                </Tooltip>
              );
            })}
            <Tooltip title="自定义">
              <ColorPicker
                value={colorPrimary}
                onChangeComplete={(c: AggregationColor) =>
                  setColorPrimary(c.toHexString())
                }
              >
                <Button
                  type="text"
                  aria-label="自定义主题色"
                  icon={<BgColorsOutlined />}
                  style={{
                    width: 28,
                    height: 28,
                    padding: 0,
                    borderRadius: token.borderRadius,
                    border: `1px dashed ${token.colorBorder}`,
                  }}
                />
              </ColorPicker>
            </Tooltip>
          </Flex>

          <Divider style={{ margin: '16px 0' }} />
          <Flex align="center" justify="space-between">
            <div>
              <Text>紧凑模式</Text>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>
                一屏显示更多内容
              </Text>
            </div>
            <Switch checked={compact} onChange={setCompact} />
          </Flex>
        </>
      )}

      {/* 背景（所有主题通用；玻璃主题下透出磨砂质感尤为明显） */}
      <Divider style={{ margin: '16px 0' }} />
      <Text type="secondary" style={{ fontSize: 12 }}>
        背景
      </Text>
      <Flex gap={8} wrap style={{ marginTop: 8 }}>
        {BACKGROUND_METAS.map((b) => {
          const active = b.key === background;
          const isFollow = b.key === 'theme';
          return (
            <Tooltip key={b.key} title={b.label}>
              <Button
                type="text"
                aria-label={b.label}
                onClick={() => setBackground(b.key)}
                style={{
                  width: 32,
                  height: 32,
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
                  <CheckOutlined
                    style={{
                      color: isFollow ? token.colorText : '#fff',
                      fontSize: 14,
                    }}
                  />
                ) : isFollow ? (
                  <span
                    style={{ fontSize: 11, color: token.colorTextSecondary }}
                  >
                    自动
                  </span>
                ) : null}
              </Button>
            </Tooltip>
          );
        })}
      </Flex>

      <Divider style={{ margin: '16px 0' }} />

      <Button block onClick={reset}>
        恢复默认
      </Button>
    </div>
  );

  return (
    <Popover
      content={content}
      trigger="click"
      placement="bottomRight"
      title="主题设置"
    >
      <Button type="text" icon={<BgColorsOutlined />} aria-label="主题设置" />
    </Popover>
  );
};

export default ThemeSwitcher;
