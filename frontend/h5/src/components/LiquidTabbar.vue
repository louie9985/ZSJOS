<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { gsap } from 'gsap'

export interface LiquidTabItem {
  path: string
  icon: string
  activeIcon: string
  label: string
}

const props = defineProps<{ items: LiquidTabItem[] }>()
const route = useRoute()
const shellRef = ref<HTMLElement | null>(null)
const targetRef = ref<SVGRectElement | null>(null)
const blobRef = ref<SVGEllipseElement | null>(null)
const neckRef = ref<SVGEllipseElement | null>(null)
let resizeObserver: ResizeObserver | undefined
let animationContext: gsap.Context | undefined
let currentX = 0
let currentWidth = 18
let positioned = false

const activeIndex = computed(() => {
  const index = props.items.findIndex((item) => item.path === route.path)
  return index >= 0 ? index : 0
})
const visualActiveIndex = ref(activeIndex.value)

function getTabbarRoot() {
  return shellRef.value?.querySelector<HTMLElement>('.van-tabbar')
}

function getGeometry() {
  const root = getTabbarRoot()
  const item = root?.querySelectorAll<HTMLElement>('.van-tabbar-item')[activeIndex.value]
  if (!root || !item) return null
  const rootRect = root.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  const x = ((itemRect.left - rootRect.left + itemRect.width / 2) / rootRect.width) * 100
  const width = (Math.max(48, Math.min(76, itemRect.width * 0.58)) / rootRect.width) * 100
  return { x, width }
}

function setStaticState(x: number, width: number) {
  const target = targetRef.value
  const blob = blobRef.value
  const neck = neckRef.value
  if (!target || !blob || !neck) return
  gsap.set(target, { attr: { x: x - width / 2, width }, scaleX: 1 })
  gsap.set(blob, { attr: { cx: x, rx: width * 0.36, ry: 23 }, opacity: 0 })
  gsap.set(neck, { attr: { cx: x, rx: width * 0.44 }, opacity: 0 })
  const tabContents = getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text')
  if (tabContents?.length) {
    gsap.set(tabContents, { scale: 1, transformOrigin: 'center bottom' })
  }
}

function animateTo(targetGeometry: { x: number; width: number }, animate: boolean) {
  const target = targetRef.value
  const blob = blobRef.value
  const neck = neckRef.value
  if (!target || !blob || !neck) return

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const shouldAnimate = animate && positioned && !reduceMotion
  const tabContents = getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text')
  gsap.killTweensOf([target, blob, neck, ...(tabContents ? Array.from(tabContents) : [])])
  if (tabContents?.length) {
    gsap.set(tabContents, { scale: 1, transformOrigin: 'center bottom' })
  }

  if (!shouldAnimate) {
    visualActiveIndex.value = activeIndex.value
    setStaticState(targetGeometry.x, targetGeometry.width)
    currentX = targetGeometry.x
    currentWidth = targetGeometry.width
    positioned = true
    return
  }

  gsap.set([blob, neck], { scaleX: 1, scaleY: 1 })

  const fromX = currentX
  const toX = targetGeometry.x
  const distance = toX - fromX
  const distanceAbs = Math.abs(distance)
  const bridgeRadius = Math.max(targetGeometry.width * 0.48, distanceAbs / 2 + targetGeometry.width * 0.42)
  const activeItem = getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item')[activeIndex.value]
  const activeContent = activeItem?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text')
  const timeline = gsap.timeline()

  timeline
    .to(activeContent || [], {
      scale: 1.12,
      duration: 0.12,
      stagger: 0.025,
      ease: 'power2.out'
    }, 0)
    .to(target, {
      scaleX: 1.13,
      duration: 0.1,
      ease: 'power2.out'
    })
    .set(blob, {
      attr: { cx: fromX, rx: targetGeometry.width * 0.38, ry: 22 },
      opacity: 1
    }, 0.06)
    .set(neck, {
      attr: { cx: (fromX + toX) / 2, rx: bridgeRadius, ry: 7 },
      opacity: 1
    }, 0.06)
    .to(blob, {
      attr: { cx: toX, rx: targetGeometry.width * 0.5 },
      duration: 0.34,
      ease: 'power2.inOut'
    }, 0.08)
    .to(neck, {
      attr: { cx: toX, rx: targetGeometry.width * 0.5 },
      duration: 0.28,
      ease: 'power2.inOut'
    }, 0.19)
    .call(() => {
      visualActiveIndex.value = activeIndex.value
    }, [], 0.27)
    .to(target, {
      attr: { x: targetGeometry.x - targetGeometry.width / 2, width: targetGeometry.width },
      scaleX: 1,
      duration: 0.27,
      ease: 'back.out(1.35)'
    }, 0.24)
    .to(activeContent || [], {
      scale: 1,
      duration: 0.28,
      stagger: 0.02,
      ease: 'back.out(1.4)'
    }, 0.29)
    .to(blob, {
      attr: { rx: targetGeometry.width * 0.36, ry: 22 },
      scaleY: 0.2,
      opacity: 0,
      duration: 0.13,
      ease: 'power2.in'
    }, 0.4)
    .to(neck, {
      attr: { rx: targetGeometry.width * 0.42 },
      scaleY: 0.15,
      opacity: 0,
      duration: 0.13,
      ease: 'power2.in'
    }, 0.4)

  currentX = toX
  currentWidth = targetGeometry.width
  positioned = true
}

function schedulePosition(animate: boolean) {
  void nextTick(() => requestAnimationFrame(() => {
    const geometry = getGeometry()
    if (geometry) animateTo(geometry, animate)
  }))
}

function handleResize() {
  schedulePosition(false)
}

watch(activeIndex, () => schedulePosition(true))
watch(() => props.items.map((item) => item.path).join('|'), () => {
  visualActiveIndex.value = activeIndex.value
  schedulePosition(false)
})

onMounted(() => {
  const root = getTabbarRoot()
  const scope = root || undefined
  animationContext = gsap.context(() => schedulePosition(false), scope)
  resizeObserver = new ResizeObserver(handleResize)
  if (root) resizeObserver.observe(root)
  window.addEventListener('resize', handleResize, { passive: true })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', handleResize)
  animationContext?.revert()
  gsap.killTweensOf([targetRef.value, blobRef.value, neckRef.value])
})
</script>

<template>
  <div ref="shellRef" class="liquid-tabbar-shell">
    <van-tabbar
      class="app-tabbar norem"
      route
      placeholder
      safe-area-inset-bottom
      active-color="var(--h5-text-primary)"
      inactive-color="var(--h5-text-secondary)"
    >
      <svg class="app-tabbar__gooey" aria-hidden="true" viewBox="0 0 100 66" preserveAspectRatio="none">
        <defs>
          <filter id="app-tabbar-gooey-filter" x="-30%" y="-80%" width="160%" height="260%">
            <feGaussianBlur in="SourceGraphic" stdDeviation="3.8" result="blur" />
            <feColorMatrix in="blur" mode="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 22 -9" result="gooey" />
            <feComposite in="SourceGraphic" in2="gooey" operator="atop" />
          </filter>
        </defs>
        <g class="app-tabbar__gooey-group" filter="url(#app-tabbar-gooey-filter)">
          <rect ref="targetRef" class="app-tabbar__gooey-target" x="-10" y="6" width="20" height="54" rx="27" />
          <ellipse ref="neckRef" class="app-tabbar__gooey-neck" cx="0" cy="33" rx="8" ry="7" />
          <ellipse ref="blobRef" class="app-tabbar__gooey-blob" cx="0" cy="33" rx="7" ry="22" />
        </g>
      </svg>
      <van-tabbar-item v-for="(item, index) in props.items" :key="item.path" :to="item.path">
        <template #icon>
          <span class="app-tabbar__icon-stack" :class="{ 'is-filled': visualActiveIndex === index }" aria-hidden="true">
            <van-icon :name="item.icon" class="app-tabbar__icon-state app-tabbar__icon-state--outline" />
            <van-icon :name="item.activeIcon" class="app-tabbar__icon-state app-tabbar__icon-state--filled" />
          </span>
        </template>
        {{ item.label }}
      </van-tabbar-item>
    </van-tabbar>
  </div>
</template>
