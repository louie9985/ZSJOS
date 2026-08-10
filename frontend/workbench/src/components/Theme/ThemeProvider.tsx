import type { ConfigProviderProps } from 'antd';
import { ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import type { ThemePreset } from '../../constants';
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

/**
 * 全局主题注入层：根据 preset 选择对应主题配置，用 ConfigProvider 包裹全站。
 * 两套“默认”主题（default-light/dark）支持运行时叠加 colorPrimary/compact；
 * 其余 11 套由各自 hook 提供完整配置。放在 rootContainer 内即全站生效。
 *
 * 注意：所有主题 hook 必须无条件调用（React Hooks 规则），再按 preset 选用。
 */
const ThemeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const value = useThemeState();
  const { preset, isDark, colorPrimary, compact } = value;

  // 无条件调用全部预设 hook
  const configs: Record<ThemePreset, ConfigProviderProps> = {
    'default-light': buildDefaultConfig(false, colorPrimary, compact),
    'default-dark': buildDefaultConfig(true, colorPrimary, compact),
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

  const finalConfig = configs[preset] ?? configs['default-light'];

  return (
    <ThemeStateContext.Provider value={value}>
      {/* data-crm-dark 供 CRM 布局做玻璃/暗色背景适配 */}
      <div
        data-crm-preset={preset}
        data-crm-dark={isDark ? 'true' : 'false'}
        style={{ display: 'contents' }}
      >
        <ConfigProvider {...finalConfig}>{children}</ConfigProvider>
      </div>
    </ThemeStateContext.Provider>
  );
};

export default ThemeProvider;
