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
  <div class="page-container">
    <van-nav-bar title="主题切换" left-arrow @click-left="$router.back()" />

    <div class="theme-list">
      <div
        v-for="t in THEMES"
        :key="t.key"
        class="theme-card"
        :class="{ 'theme-card--active': currentTheme() === t.key }"
        @click="onSelect(t.key)"
      >
        <div class="theme-card__preview" :style="{ background: t.color }" />
        <div class="theme-card__info">
          <span class="theme-card__emoji">{{ t.emoji }}</span>
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
  display: flex;
  align-items: center;
  background: var(--h5-card-bg);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}
.theme-card--active {
  border-color: var(--h5-primary);
}

.theme-card__preview {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
}

.theme-card__info {
  flex: 1;
  margin-left: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.theme-card__emoji {
  font-size: 18px;
}
.theme-card__label {
  font-size: 15px;
  color: var(--h5-text-primary);
}
</style>
