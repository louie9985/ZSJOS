import type { ConfigProviderProps } from 'antd';
import { theme } from 'antd';

/**
 * 内联主题：默认浅色 / 默认深色（非 hook，直接返回 ConfigProviderProps）。
 * default 系支持运行时叠加 colorPrimary / compact / 圆角，故由 ThemeProvider 动态组装，
 * 此处仅提供 Layout/Menu 的基础配置片段供复用。
 */
export function buildDefaultConfig(
  isDark: boolean,
  colorPrimary: string,
  compact: boolean,
): ConfigProviderProps {
  const algorithm = [
    isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    ...(compact ? [theme.compactAlgorithm] : []),
  ];

  const components = isDark
    ? {
        Layout: {
          bodyBg: '#050505',
          footerBg: '#050505',
          headerBg: '#111111',
          headerColor: 'rgba(255, 255, 255, 0.88)',
          siderBg: '#050505',
          triggerBg: '#111111',
          triggerColor: 'rgba(255, 255, 255, 0.88)',
        },
        Menu: {
          darkItemBg: 'transparent',
          darkItemColor: 'rgba(255, 255, 255, 0.68)',
          darkItemHoverBg: 'rgba(255, 255, 255, 0.08)',
          darkItemHoverColor: '#fff',
          darkItemSelectedBg: 'rgba(22, 119, 255, 0.28)',
          darkItemSelectedColor: '#fff',
          darkSubMenuItemBg: 'transparent',
          activeBarBorderWidth: 0,
          itemBg: 'transparent',
          subMenuItemBg: 'transparent',
        },
      }
    : {
        Layout: {
          bodyBg: '#f5f8ff',
          footerBg: '#f5f8ff',
          headerBg: '#ffffff',
          headerColor: 'rgba(0, 0, 0, 0.88)',
          siderBg: '#ffffff',
          triggerBg: '#f0f5ff',
          triggerColor: 'rgba(0, 0, 0, 0.88)',
        },
        Menu: {
          activeBarBorderWidth: 0,
          itemBg: 'transparent',
          subMenuItemBg: 'transparent',
        },
      };

  return {
    theme: {
      algorithm,
      token: {
        colorPrimary,
        borderRadius: 10,
        fontFamily: 'AlibabaSans, sans-serif',
      },
      components,
    },
  };
}
