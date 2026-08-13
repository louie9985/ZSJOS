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
  BORDER_RADIUS_OPTIONS,
  DEFAULT_THEME,
  DENSITIES,
  FONT_SCALES,
  LAYOUT_MODES,
  STORAGE_KEYS,
  TAB_STYLE_OPTIONS,
  THEME_METAS,
  type BackgroundKey,
  type BorderRadiusPreset,
  type Density,
  type FontScale,
  type LayoutMode,
  type TabStyle,
  type ThemePreset,
} from '../../constants';

/**
 * CRM 全局主题：自建 React Context（不走 umi model，避免与 rootContainer 的
 * Provider 层级冲突）。状态持久化到 localStorage，刷新不丢。
 * 明暗由所选 preset 决定（见 constants.ts 的 THEME_METAS.dark）；
 * colorPrimary / compact 仅对两套”默认”主题生效。ConfigProvider 注入见 ThemeProvider.tsx。
 */

export type { BackgroundKey, BorderRadiusPreset, Density, FontScale, LayoutMode, TabStyle, ThemePreset };

export interface ThemeState {
  preset: ThemePreset;
  colorPrimary: string;
  /** @deprecated 由 density 取代，仅保留用于旧数据迁移 */
  compact: boolean;
  background: BackgroundKey;
  /** 玻璃不透明度 0–100，仅自定义背景时生效 */
  glassOpacity: number;
  density: Density;
  fontScale: FontScale;
  layoutMode: LayoutMode;
  borderRadius: BorderRadiusPreset;
  headerFixed: boolean;
  animation: boolean;
  watermark: boolean;
  tabs: boolean;
  tabStyle: TabStyle;
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
  setGlassOpacity: (opacity: number) => void;
  setDensity: (density: Density) => void;
  setFontScale: (scale: FontScale) => void;
  setLayoutMode: (mode: LayoutMode) => void;
  setBorderRadius: (preset: BorderRadiusPreset) => void;
  setHeaderFixed: (fixed: boolean) => void;
  setAnimation: (enabled: boolean) => void;
  setWatermark: (enabled: boolean) => void;
  setTabs: (enabled: boolean) => void;
  setTabStyle: (style: TabStyle) => void;
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
    // 迁移：density 面世前，紧凑度是布尔值且只对两套默认主题生效
    if (parsed.density === undefined && parsed.compact === true) {
      merged.density = 'compact';
    }
    if (!DENSITIES.includes(merged.density)) {
      merged.density = DEFAULT_THEME.density;
    }
    if (!FONT_SCALES.includes(merged.fontScale)) {
      merged.fontScale = DEFAULT_THEME.fontScale;
    }
    if (!LAYOUT_MODES.includes(merged.layoutMode)) {
      merged.layoutMode = DEFAULT_THEME.layoutMode;
    }
    if (typeof merged.glassOpacity !== 'number' || merged.glassOpacity < 0 || merged.glassOpacity > 100) {
      merged.glassOpacity = DEFAULT_THEME.glassOpacity;
    }
    if (!BORDER_RADIUS_OPTIONS.some((o) => o.value === merged.borderRadius)) {
      merged.borderRadius = DEFAULT_THEME.borderRadius;
    }
    if (typeof merged.headerFixed !== 'boolean') merged.headerFixed = DEFAULT_THEME.headerFixed;
    if (typeof merged.animation !== 'boolean') merged.animation = DEFAULT_THEME.animation;
    if (typeof merged.watermark !== 'boolean') merged.watermark = DEFAULT_THEME.watermark;
    if (typeof merged.tabs !== 'boolean') merged.tabs = DEFAULT_THEME.tabs;
    if (!TAB_STYLE_OPTIONS.some((o) => o.value === merged.tabStyle)) {
      merged.tabStyle = DEFAULT_THEME.tabStyle;
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
  const setGlassOpacity = useCallback(
    (glassOpacity: number) => setState((s) => ({ ...s, glassOpacity: Math.max(0, Math.min(100, glassOpacity)) })),
    [],
  );
  const setDensity = useCallback(
    (density: Density) => setState((s) => ({ ...s, density })),
    [],
  );
  const setFontScale = useCallback(
    (fontScale: FontScale) => setState((s) => ({ ...s, fontScale })),
    [],
  );
  const setLayoutMode = useCallback(
    (layoutMode: LayoutMode) => setState((s) => ({ ...s, layoutMode })),
    [],
  );
  const setBorderRadius = useCallback(
    (borderRadius: BorderRadiusPreset) => setState((s) => ({ ...s, borderRadius })),
    [],
  );
  const setHeaderFixed = useCallback(
    (headerFixed: boolean) => setState((s) => ({ ...s, headerFixed })),
    [],
  );
  const setAnimation = useCallback(
    (animation: boolean) => setState((s) => ({ ...s, animation })),
    [],
  );
  const setWatermark = useCallback(
    (watermark: boolean) => setState((s) => ({ ...s, watermark })),
    [],
  );
  const setTabs = useCallback(
    (tabs: boolean) => setState((s) => ({ ...s, tabs })),
    [],
  );
  const setTabStyle = useCallback(
    (tabStyle: TabStyle) => setState((s) => ({ ...s, tabStyle })),
    [],
  );
  const reset = useCallback(() => setState(DEFAULT_THEME), []);

  const meta = THEME_METAS.find((m) => m.key === state.preset);
  const isDark = meta?.dark ?? false;
  const customizable = meta?.customizable ?? false;
  const bgMeta = BACKGROUND_METAS.find((b) => b.key === state.background);
  // 按 preset 明暗过滤：浅色 preset 不得使用暗色渐变，反之亦然。
  // 不匹配时回落到「跟随主题」（value 为 undefined），避免深底深字。
  const backgroundValue =
    bgMeta && (bgMeta.dark === undefined || bgMeta.dark === isDark)
      ? bgMeta.value
      : undefined;

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
      setGlassOpacity,
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
      setGlassOpacity,
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
