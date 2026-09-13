<script setup lang="ts">
import { useTheme, THEMES } from '@/composables/useTheme'
import type { ThemeKey } from '@/stores/app'

defineOptions({ name: 'ThemeSwitch' })

const { currentTheme, switchTheme } = useTheme()

function onSelect(key: ThemeKey) {
  switchTheme(key)
}
</script>

<template>
  <div class="page-container my-subpage-page theme-page">
    <van-nav-bar class="my-subpage__nav" title="主题切换" left-arrow @click-left="$router.back()" />

    <div class="theme-list">
      <div
        v-for="t in THEMES"
        :key="t.key"
        class="theme-card my-subpage-card"
        :class="{ 'theme-card--active': currentTheme() === t.key }"
        @click="onSelect(t.key)"
      >
        <div
          class="theme-card__preview"
          :style="{ background: t.background, '--theme-preview-accent': t.accent }"
        >
          <span class="theme-card__preview-surface" />
          <span class="theme-card__preview-accent" />
        </div>
        <div class="theme-card__info">
          <span class="theme-card__marker" :style="{ background: t.accent }" />
          <span class="theme-card__label">{{ t.label }}</span>
        </div>
        <van-icon
          v-if="currentTheme() === t.key"
          name="success"
          color="var(--h5-primary)"
          size="20"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-list {
  padding: 16px;
}

.theme-card {
  background: var(--h5-content-surface);
  display: flex;
  align-items: center;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid var(--h5-glass-border);
  transition: border-color 0.2s;
}
.theme-card--active {
  border-color: var(--h5-primary);
}

.theme-card__preview {
  position: relative;
  width: 40px;
  height: 40px;
  border: 1px solid color-mix(in srgb, var(--h5-primary) 14%, #fff);
  border-radius: 10px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9), 0 4px 12px rgba(31, 35, 48, 0.06);
  flex-shrink: 0;
}

.theme-card__preview-surface {
  position: absolute;
  inset: 8px 7px;
  border: 1px solid rgba(60, 60, 67, 0.12);
  border-radius: 5px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.theme-card__preview-accent {
  position: absolute;
  right: 10px;
  bottom: 11px;
  left: 10px;
  height: 3px;
  border-radius: 999px;
  background: var(--theme-preview-accent);
}

.theme-card__info {
  flex: 1;
  margin-left: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.theme-card__marker {
  width: 10px;
  height: 10px;
  flex: 0 0 10px;
  border-radius: 50%;
}
.theme-card__label {
  font-size: 15px;
  color: var(--h5-text-primary);
}

</style>
