<script setup lang="ts">
withDefaults(defineProps<{
  rank: number
  position?: 'left' | 'center' | 'right'
  tone?: 'medal' | 'theme'
}>(), {
  position: 'center',
  tone: 'medal'
})
</script>

<template>
  <span
    class="leaderboard-crown"
    :class="[`leaderboard-crown--${position}`, `leaderboard-crown--${tone}`]"
    :aria-label="`第 ${rank} 名`"
  >
    <b>{{ rank }}</b>
  </span>
</template>

<style scoped>
.leaderboard-crown {
  position: relative;
  display: flex;
  width: 30px;
  height: 22px;
  align-items: flex-end;
  justify-content: center;
  margin-bottom: 1px;
  color: #8a6500;
  font-size: 9px;
  font-weight: 800;
  line-height: 1;
}

.leaderboard-crown::before {
  position: absolute;
  inset: 0 0 3px;
  border-radius: 2px 2px 4px 4px;
  background: #ffe75c;
  clip-path: polygon(0 12%, 22% 48%, 33% 0, 50% 43%, 67% 0, 78% 48%, 100% 12%, 88% 100%, 12% 100%);
  content: '';
}

.leaderboard-crown b {
  position: relative;
  z-index: 1;
  margin-bottom: 4px;
  font-size: 9px;
}

.leaderboard-crown--left,
.leaderboard-crown--right {
  width: 25px;
  height: 19px;
  font-size: 8px;
}

.leaderboard-crown--left { color: #566475; }
.leaderboard-crown--left::before { background: #dce8f3; }
.leaderboard-crown--right { color: #906125; }
.leaderboard-crown--right::before { background: #ffd098; }

.leaderboard-crown--theme { color: var(--h5-primary-dark); }
.leaderboard-crown--theme::before {
  background: color-mix(in srgb, var(--h5-primary) 48%, var(--h5-card-bg));
}
</style>
