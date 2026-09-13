import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'
import { THEME_KEYS, resolveThemeKey, useAppStore } from '../src/stores/app.ts'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function createThemeStore(savedTheme) {
  const values = new Map()
  if (savedTheme !== undefined) values.set('h5-theme', savedTheme)

  const attributes = new Map()
  globalThis.localStorage = {
    getItem(key) {
      return values.get(key) ?? null
    },
    setItem(key, value) {
      values.set(key, value)
    }
  }
  globalThis.document = {
    documentElement: {
      setAttribute(name, value) {
        attributes.set(name, value)
      }
    }
  }

  setActivePinia(createPinia())
  return { store: useAppStore(), values, attributes }
}

test('主题键只保留三套彩色主题', () => {
  assert.deepEqual(THEME_KEYS, ['coral', 'lavender', 'sky'])
})

test('缺失、非法或历史经典主题记录回退到珊瑚粉', () => {
  for (const value of [null, '', 'unknown', 'neutral']) {
    assert.equal(resolveThemeKey(value), 'coral')
  }

  assert.equal(createThemeStore().store.theme, 'coral')
  assert.equal(createThemeStore('unknown').store.theme, 'coral')
  assert.equal(createThemeStore('neutral').store.theme, 'coral')
})

test('已有合法主题记录保持不变', () => {
  for (const theme of THEME_KEYS) {
    assert.equal(resolveThemeKey(theme), theme)
    assert.equal(createThemeStore(theme).store.theme, theme)
  }
})

test('三套主题均可应用并持久化', () => {
  const { store, values, attributes } = createThemeStore()

  for (const theme of THEME_KEYS) {
    store.setTheme(theme)
    assert.equal(store.theme, theme)
    assert.equal(values.get('h5-theme'), theme)
    assert.equal(attributes.get('data-theme'), theme)
  }
})

test('主题列表、主题页和加载入口均不再包含经典主题', () => {
  const themeListSource = readFileSync(path.join(projectRoot, 'src/composables/useTheme.ts'), 'utf8')
  const themePageSource = readFileSync(path.join(projectRoot, 'src/pages/profile/theme.vue'), 'utf8')
  const profileSource = readFileSync(path.join(projectRoot, 'src/pages/profile/index.vue'), 'utf8')
  const mainSource = readFileSync(path.join(projectRoot, 'src/main.ts'), 'utf8')
  const coralCss = readFileSync(path.join(projectRoot, 'src/styles/themes/coral.css'), 'utf8')

  assert.match(themeListSource, /\[\s*\{ key: 'coral', label: '珊瑚粉'/)
  assert.match(themeListSource, /\{ key: 'sky', label: '天空蓝', accent: '#4A90D9'/)
  assert.match(themePageSource, /v-for="t in THEMES"/)
  assert.match(themePageSource, /class="theme-card__marker" :style="\{ background: t\.accent \}"/)
  assert.match(themePageSource, /--theme-preview-accent/)
  assert.match(profileSource, /THEMES\.find\(t => t\.key === currentTheme\(\)\) \|\| THEMES\[0\]/)
  assert.doesNotMatch(themeListSource, /neutral|经典/)
  assert.doesNotMatch(themePageSource, /neutral|经典/)
  assert.doesNotMatch(mainSource, /neutral\.css/)
  assert.equal(existsSync(path.join(projectRoot, 'src/styles/themes/neutral.css')), false)
  assert.doesNotMatch(coralCss, /:root:not\(\[data-theme\]\)/)
})

test('三套主题继续保留主题材质所需的公共变量', () => {
  const baseCss = readFileSync(path.join(projectRoot, 'src/styles/base.css'), 'utf8')
  const vantCss = readFileSync(path.join(projectRoot, 'src/styles/vant-overrides.css'), 'utf8')

  assert.match(baseCss, /\.card \{[\s\S]*background: var\(--h5-content-surface-subtle\);/)
  assert.match(baseCss, /\.my-subpage-page > \.my-subpage__nav \{[\s\S]*background: var\(--h5-navigation-surface\);/)
  assert.match(vantCss, /\.app-tabbar > \.van-tabbar \{[\s\S]*background: var\(--h5-navigation-surface-subtle\);/)
  assert.doesNotMatch(vantCss, /neutral|经典/)
})

test('初始化时将历史主题值持久化为珊瑚粉', () => {
  const { store, values, attributes } = createThemeStore('neutral')

  store.initTheme()

  assert.equal(store.theme, 'coral')
  assert.equal(values.get('h5-theme'), 'coral')
  assert.equal(attributes.get('data-theme'), 'coral')
})
