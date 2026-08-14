import type { ConfigProviderProps } from 'antd';
import { ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
import { BORDER_RADIUS_VALUES, FONT_SCALE_SIZE, type Density, type FontScale, type ThemePreset } from '../../constants';
import { withGlassSurface } from './glassSurface';
import { buildDefaultConfig } from './presets';
import useBlossomTheme from './presets/blossomTheme';
import useBootstrapTheme from './presets/bootstrapTheme';
import useCartoonTheme from './presets/cartoonTheme';
import useGeekTheme from './presets/geekTheme';
import useGlassTheme from './presets/glassTheme';
import useIllustrationTheme from './presets/illustrationTheme';
import useLarkTheme from './presets/larkTheme';
import useMuiTheme from './presets/muiTheme';
import useSereneTheme from './presets/sereneTheme';
import useShadcnTheme from './presets/shadcnTheme';
import useV4Theme from './presets/v4Theme';
import { ThemeStateContext, useThemeState } from './ThemeContext';
import ThemeVars from './ThemeVars';

/**
 * 全局主题注入层：根据 preset 选择对应主题配置，用 ConfigProvider 包裹全站。
 * 两套“默认”主题（default-light/dark）支持运行时叠加 colorPrimary/compact；
 * 其余 11 套由各自 hook 提供完整配置。放在 rootContainer 内即全站生效。
 *
 * 注意：所有主题 hook 必须无条件调用（React Hooks 规则），再按 preset 选用。
 */
/**
 * 把密度、字号、圆角叠加到任意 preset 之上。
 *
 * algorithm 必须追加而非替换：11 套 preset 各自用 algorithm 定义明暗与配色，
 * 直接赋值会把它们的算法丢掉。字段可能是单个函数、数组或 undefined，三种都要兼容。
 */
export function withDensity(
  config: ConfigProviderProps,
  density: Density,
  fontScale: FontScale,
  borderRadiusLg?: number,
): ConfigProviderProps {
  const base = config.theme ?? {};
  const existing = base.algorithm;
  const algorithms = existing ? (Array.isArray(existing) ? [...existing] : [existing]) : [];
  if (density === 'compact') algorithms.push(antdTheme.compactAlgorithm);

  return {
    ...config,
    theme: {
      ...base,
      ...(algorithms.length ? { algorithm: algorithms } : {}),
      token: {
        ...base.token,
        fontSize: FONT_SCALE_SIZE[fontScale],
        ...(borderRadiusLg !== undefined ? { borderRadius: borderRadiusLg } : {}),
      },
    },
  };
}

const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const value = useThemeState();
  const { preset, isDark, colorPrimary, density, fontScale, backgroundValue, glassOpacity, borderRadius } = value;
  const hasBackground = Boolean(backgroundValue);
  const radiusValue = BORDER_RADIUS_VALUES[borderRadius].lg;

  // 无条件调用全部预设 hook
  const configs: Record<ThemePreset, ConfigProviderProps> = {
    // compact 传 false：紧凑度统一由 withDensity 叠加，否则会叠两次
    'default-light': buildDefaultConfig(false, colorPrimary, false, radiusValue),
    'default-dark': buildDefaultConfig(true, colorPrimary, false, radiusValue),
    mui: useMuiTheme(),
    shadcn: useShadcnTheme(),
    bootstrap: useBootstrapTheme(),
    cartoon: useCartoonTheme(),
    illustration: useIllustrationTheme(),
    glass: useGlassTheme(),
    geek: useGeekTheme(),
    lark: useLarkTheme(),
    blossom: useBlossomTheme(),
    v4: useV4Theme(),
    serene: useSereneTheme(),
  };

  const finalConfig = withGlassSurface(
    withDensity(
      configs[preset] ?? configs['default-light'],
      density,
      fontScale,
      radiusValue,
    ),
    hasBackground,
    glassOpacity,
  );

  return (
    <ThemeStateContext.Provider value={value}>
      {/* data-crm-dark 供 CRM 布局做玻璃/暗色背景适配 */}
      <div
        data-crm-preset={preset}
        data-crm-dark={isDark ? 'true' : 'false'}
        style={{ display: 'contents' }}
      >
        <ConfigProvider {...finalConfig} locale={zhCN}>
          {/* 须在 ConfigProvider 内：依赖 useToken 读取合并后的 preset token */}
          <ThemeVars />
          {children}
        </ConfigProvider>
      </div>
    </ThemeStateContext.Provider>
  );
};

export default ThemeProvider;
