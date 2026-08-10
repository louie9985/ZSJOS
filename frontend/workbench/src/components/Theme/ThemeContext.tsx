import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  BACKGROUND_METAS,
  DEFAULT_THEME,
  STORAGE_KEYS,
  THEME_METAS,
  type BackgroundKey,
  type ThemePreset,
} from '../../constants';

/**
 * CRM 全局主题：自建 React Context（不走 umi model，避免与 rootContainer 的
 * Provider 层级冲突）。状态持久化到 localStorage，刷新不丢。
 * 明暗由所选 preset 决定（见 constants.ts 的 THEME_METAS.dark）；
 * colorPrimary / compact 仅对两套“默认”主题生效。ConfigProvider 注入见 ThemeProvider.tsx。
 */

export type { BackgroundKey, ThemePreset };

export interface ThemeState {
  preset: ThemePreset;
  colorPrimary: string;
  compact: boolean;
  background: BackgroundKey;
}

export interface ThemeContextValue extends ThemeState {
  /** 当前 preset 是否为暗色系（由注册表元数据推导） */
  isDark: boolean;
  /** 当前 preset 是否支持自定义主题色/紧凑度 */
  customizable: boolean;
  /** 解析后的背景 CSS 值（'theme' 时为 undefined，跟随主题） */
  backgroundValue?: string;
  setPreset: (preset: ThemePreset) => void;
  setColorPrimary: (color: string) => void;
  setCompact: (compact: boolean) => void;
  setBackground: (bg: BackgroundKey) => void;
  reset: () => void;
}

function readStorage(): ThemeState {
  if (typeof window === 'undefined') return DEFAULT_THEME;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEYS.THEME);
    if (!raw) return DEFAULT_THEME;
    const parsed = JSON.parse(raw) as Partial<ThemeState>;
    const merged = { ...DEFAULT_THEME, ...parsed };
    // 校验 preset 合法（旧数据可能残留已废弃的 key）
    if (!THEME_METAS.some((m) => m.key === merged.preset)) {
      merged.preset = DEFAULT_THEME.preset;
    }
    return merged;
  } catch {
    return DEFAULT_THEME;
  }
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

/** 持有主题状态与持久化的 hook（供 ThemeProvider 内部使用） */
export function useThemeState(): ThemeContextValue {
  const [state, setState] = useState<ThemeState>(readStorage);

  useEffect(() => {
    try {
      window.localStorage.setItem(STORAGE_KEYS.THEME, JSON.stringify(state));
    } catch {
      // localStorage 不可用时静默降级（仅当前会话生效）
    }
  }, [state]);

  const setPreset = useCallback(
    (preset: ThemePreset) => setState((s) => ({ ...s, preset })),
    [],
  );
  const setColorPrimary = useCallback(
    (colorPrimary: string) => setState((s) => ({ ...s, colorPrimary })),
    [],
  );
  const setCompact = useCallback(
    (compact: boolean) => setState((s) => ({ ...s, compact })),
    [],
  );
  const setBackground = useCallback(
    (background: BackgroundKey) => setState((s) => ({ ...s, background })),
    [],
  );
  const reset = useCallback(() => setState(DEFAULT_THEME), []);

  const meta = THEME_METAS.find((m) => m.key === state.preset);
  const isDark = meta?.dark ?? false;
  const customizable = meta?.customizable ?? false;
  const backgroundValue = BACKGROUND_METAS.find(
    (b) => b.key === state.background,
  )?.value;

  return useMemo(
    () => ({
      ...state,
      isDark,
      customizable,
      backgroundValue,
      setPreset,
      setColorPrimary,
      setCompact,
      setBackground,
      reset,
    }),
    [
      state,
      isDark,
      customizable,
      backgroundValue,
      setPreset,
      setColorPrimary,
      setCompact,
      setBackground,
      reset,
    ],
  );
}

export const ThemeStateContext = ThemeContext;

/** 供任意组件（顶栏面板等）读写主题 */
export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme 必须在 ThemeProvider 内使用');
  }
  return ctx;
}
