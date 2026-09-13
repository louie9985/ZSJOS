<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { gsap } from 'gsap'

export interface LiquidTabItem {
  path: string
  icon: string
  activeIcon: string
  label: string
}

const props = defineProps<{ items: LiquidTabItem[] }>()
const route = useRoute()
const router = useRouter()
const shellRef = ref<HTMLElement | null>(null)
const indicatorRef = ref<HTMLElement | null>(null)
let resizeObserver: ResizeObserver | undefined
let activeTimeline: gsap.core.Timeline | undefined
let positionRequestFrame: number | undefined
let positioned = false
let gestureState: 'idle' | 'pressed' | 'dragging' | 'settling' = 'idle'
let trackedPointerId: number | undefined
let pressedIndex = 0
let pointerStartX = 0
let pointerStartY = 0
let dragStartCenter = 0
let suppressClickUntil = 0
let skipNextRouteAnimation = false

const pressedScaleX = 1.28
const pressedScaleY = 1.34
const pressedContentScale = 1.08

const activeIndex = computed(() => {
  const index = props.items.findIndex((item) => item.path === route.path)
  return index >= 0 ? index : 0
})
const visualActiveIndex = ref(activeIndex.value)

function getTabbarRoot() {
  return shellRef.value?.querySelector<HTMLElement>('.van-tabbar')
}

function getTabItems() {
  return getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item')
}

function getGeometry(index: number) {
  const root = getTabbarRoot()
  const item = getTabItems()?.[index]
  if (!root || !item) return null
  const rootRect = root.getBoundingClientRect()
  const itemRect = item.getBoundingClientRect()
  const itemInset = itemRect.width * 0.06
  const x = itemRect.left - rootRect.left - root.clientLeft + itemInset
  const width = Math.max(0, itemRect.width - itemInset * 2)
  return { x, width, center: x + width / 2 }
}

function setStaticState(x: number, width: number) {
  const indicator = indicatorRef.value
  if (!indicator) return
  gsap.set(indicator, { x, width, scaleX: 1, scaleY: 1, transformOrigin: 'center center' })
  const tabContents = getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text')
  if (tabContents?.length) {
    gsap.set(tabContents, { scale: 1, transformOrigin: 'center bottom' })
  }
}

function tabContents() {
  return getTabbarRoot()?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text')
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function finishNavigation(targetIndex: number) {
  const path = props.items[targetIndex]?.path
  gestureState = 'idle'
  if (!path || path === route.path) return
  skipNextRouteAnimation = true
  void router.push(path).then((failure) => {
    if (!failure) return
    skipNextRouteAnimation = false
    visualActiveIndex.value = activeIndex.value
    schedulePosition(false)
  }).catch(() => {
    skipNextRouteAnimation = false
    visualActiveIndex.value = activeIndex.value
    schedulePosition(false)
  })
}

function settleTo(targetIndex: number, commitRoute: boolean, leadIn = 0) {
  const indicator = indicatorRef.value
  const targetGeometry = getGeometry(targetIndex)
  if (!indicator || !targetGeometry) return

  const reduceMotion = prefersReducedMotion()
  const shouldAnimate = positioned && !reduceMotion
  const allTabContents = tabContents()
  activeTimeline?.kill()
  activeTimeline = undefined
  gsap.killTweensOf([indicator, ...(allTabContents ? Array.from(allTabContents) : [])])

  if (!shouldAnimate) {
    visualActiveIndex.value = targetIndex
    setStaticState(targetGeometry.x, targetGeometry.width)
    positioned = true
    gestureState = 'idle'
    if (commitRoute) finishNavigation(targetIndex)
    return
  }

  const items = getTabItems()
  const sourceItem = items?.[visualActiveIndex.value]
  const destinationItem = items?.[targetIndex]
  const sourceContent = sourceItem?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text') || []
  const destinationContent = destinationItem?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text') || []
  const currentX = Number(gsap.getProperty(indicator, 'x')) || 0
  const distance = Math.abs(targetGeometry.x - currentX)
  const travelDuration = distance < 1 ? 0.08 : distance > targetGeometry.width * 1.5 ? 0.27 : 0.22
  const arrivalAt = leadIn + travelDuration
  const visualSwitchAt = leadIn + travelDuration * 0.62
  gestureState = 'settling'
  const timeline = gsap.timeline({
    onComplete: () => {
      visualActiveIndex.value = targetIndex
      setStaticState(targetGeometry.x, targetGeometry.width)
      activeTimeline = undefined
      gestureState = 'idle'
      if (commitRoute) finishNavigation(targetIndex)
    }
  })
  activeTimeline = timeline

  timeline
    .to(indicator, {
      scaleX: pressedScaleX,
      scaleY: pressedScaleY,
      duration: Math.max(leadIn, 0.01),
      ease: 'power2.out'
    }, 0)
    .to(sourceContent, {
      scale: 1,
      duration: 0.12,
      stagger: 0.02,
      ease: 'power2.inOut'
    }, 0)
    .to(indicator, {
      x: targetGeometry.x,
      width: targetGeometry.width,
      duration: travelDuration,
      ease: 'power3.inOut'
    }, leadIn)
    .call(() => {
      visualActiveIndex.value = targetIndex
    }, [], visualSwitchAt)
    .to(destinationContent, {
      scale: 1.06,
      duration: 0.08,
      stagger: 0.02,
      ease: 'power2.out'
    }, visualSwitchAt)
    .to(indicator, {
      scaleX: 0.96,
      scaleY: 0.96,
      duration: 0.07,
      ease: 'power1.in'
    }, arrivalAt)
    .to(indicator, {
      scaleX: 1,
      scaleY: 1,
      duration: 0.12,
      ease: 'back.out(1.6)'
    }, arrivalAt + 0.07)
    .to(destinationContent, {
      scale: 1,
      duration: 0.14,
      stagger: 0.02,
      ease: 'back.out(1.3)'
    }, visualSwitchAt + 0.08)

  positioned = true
}

function schedulePosition(animate: boolean) {
  void nextTick(() => {
    if (positionRequestFrame != null) cancelAnimationFrame(positionRequestFrame)
    positionRequestFrame = requestAnimationFrame(() => {
      positionRequestFrame = undefined
      const targetIndex = activeIndex.value
      const geometry = getGeometry(targetIndex)
      if (!geometry) return
      if (animate) {
        settleTo(targetIndex, false, 0.08)
      } else {
        activeTimeline?.kill()
        activeTimeline = undefined
        visualActiveIndex.value = targetIndex
        setStaticState(geometry.x, geometry.width)
        positioned = true
        gestureState = 'idle'
      }
    })
  })
}

function itemIndexFromEvent(event: PointerEvent) {
  const target = event.target instanceof Element ? event.target.closest<HTMLElement>('.van-tabbar-item') : null
  if (!target) return -1
  return Array.from(getTabItems() || []).indexOf(target)
}

function releaseTrackedPointer() {
  const pointerId = trackedPointerId
  trackedPointerId = undefined
  if (pointerId == null || !shellRef.value?.hasPointerCapture(pointerId)) return
  shellRef.value.releasePointerCapture(pointerId)
}

function cancelPointerGesture() {
  if (trackedPointerId == null) return
  suppressClickUntil = performance.now() + 100
  releaseTrackedPointer()
  settleTo(activeIndex.value, false)
}

function onPointerDown(event: PointerEvent) {
  if (!event.isPrimary || trackedPointerId != null || (event.pointerType === 'mouse' && event.button !== 0)) return
  const index = itemIndexFromEvent(event)
  const geometry = getGeometry(index)
  const indicator = indicatorRef.value
  if (index < 0 || !geometry || !indicator) return

  activeTimeline?.kill()
  activeTimeline = undefined
  const allTabContents = tabContents()
  gsap.killTweensOf([indicator, ...(allTabContents ? Array.from(allTabContents) : [])])
  trackedPointerId = event.pointerId
  pressedIndex = index
  pointerStartX = event.clientX
  pointerStartY = event.clientY
  dragStartCenter = geometry.center
  gestureState = 'pressed'
  shellRef.value?.setPointerCapture(event.pointerId)

  visualActiveIndex.value = index
  const selectedItem = getTabItems()?.[index]
  const selectedContent = selectedItem?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text') || []
  if (prefersReducedMotion()) {
    setStaticState(geometry.x, geometry.width)
    return
  }
  activeTimeline = gsap.timeline()
    .to(indicator, {
      x: geometry.x,
      width: geometry.width,
      scaleX: pressedScaleX,
      scaleY: pressedScaleY,
      duration: 0.12,
      ease: 'power3.out'
    }, 0)
    .to(selectedContent, {
      scale: pressedContentScale,
      duration: 0.12,
      stagger: 0.02,
      ease: 'power2.out'
    }, 0)
}

function onPointerMove(event: PointerEvent) {
  if (event.pointerId !== trackedPointerId || !indicatorRef.value) return
  const deltaX = event.clientX - pointerStartX
  const deltaY = event.clientY - pointerStartY
  const absX = Math.abs(deltaX)
  const absY = Math.abs(deltaY)

  if (gestureState === 'pressed') {
    if (absY > 8 && absY > absX) {
      cancelPointerGesture()
      return
    }
    if (absX < 6 || absX <= absY) return
    activeTimeline?.kill()
    activeTimeline = undefined
    const selectedItem = getTabItems()?.[visualActiveIndex.value]
    const selectedContent = selectedItem?.querySelectorAll<HTMLElement>('.van-tabbar-item__icon, .van-tabbar-item__text') || []
    gsap.to(selectedContent, { scale: 1, duration: 0.1, overwrite: true })
    gestureState = 'dragging'
  }
  if (gestureState !== 'dragging') return

  event.preventDefault()
  const geometries = props.items.map((_, index) => getGeometry(index)).filter((item): item is NonNullable<typeof item> => !!item)
  if (!geometries.length) return
  const minCenter = geometries[0].center
  const maxCenter = geometries[geometries.length - 1].center
  let desiredCenter = dragStartCenter + deltaX
  if (desiredCenter < minCenter) desiredCenter = minCenter + (desiredCenter - minCenter) * 0.2
  if (desiredCenter > maxCenter) desiredCenter = maxCenter + (desiredCenter - maxCenter) * 0.2

  const dragWidth = getGeometry(pressedIndex)?.width || geometries[0].width
  const reduceMotion = prefersReducedMotion()
  const indicatorState = {
    x: desiredCenter - dragWidth / 2,
    width: dragWidth,
    scaleX: reduceMotion ? 1 : pressedScaleX,
    scaleY: reduceMotion ? 1 : pressedScaleY
  }
  if (reduceMotion) {
    gsap.set(indicatorRef.value, indicatorState)
  } else {
    gsap.to(indicatorRef.value, {
      ...indicatorState,
      duration: 0.09,
      ease: 'power3.out',
      overwrite: true
    })
  }
  let nearestIndex = 0
  let nearestDistance = Number.POSITIVE_INFINITY
  geometries.forEach((geometry, index) => {
    const distance = Math.abs(geometry.center - desiredCenter)
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearestIndex = index
    }
  })
  visualActiveIndex.value = nearestIndex
}

function onPointerUp(event: PointerEvent) {
  if (event.pointerId !== trackedPointerId) return
  const targetIndex = gestureState === 'dragging' ? visualActiveIndex.value : pressedIndex
  const leadIn = gestureState === 'dragging' ? 0 : 0.04
  suppressClickUntil = performance.now() + 100
  releaseTrackedPointer()
  settleTo(targetIndex, true, leadIn)
}

function onPointerCancel(event: PointerEvent) {
  if (event.pointerId === trackedPointerId) cancelPointerGesture()
}

function onItemClick(index: number) {
  if (performance.now() < suppressClickUntil) return
  settleTo(index, true, 0.08)
}

function onItemKeydown(event: KeyboardEvent, index: number) {
  if (event.repeat) return
  onItemClick(index)
}

function handleResize() {
  releaseTrackedPointer()
  schedulePosition(false)
}

watch(activeIndex, () => {
  if (skipNextRouteAnimation) {
    skipNextRouteAnimation = false
    schedulePosition(false)
    return
  }
  releaseTrackedPointer()
  schedulePosition(true)
})
watch(() => props.items.map((item) => item.path).join('|'), () => {
  releaseTrackedPointer()
  visualActiveIndex.value = activeIndex.value
  schedulePosition(false)
})

onMounted(() => {
  const root = getTabbarRoot()
  schedulePosition(false)
  resizeObserver = new ResizeObserver(handleResize)
  if (root) resizeObserver.observe(root)
  window.addEventListener('resize', handleResize, { passive: true })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', handleResize)
  releaseTrackedPointer()
  if (positionRequestFrame != null) cancelAnimationFrame(positionRequestFrame)
  activeTimeline?.kill()
  const allTabContents = tabContents()
  gsap.killTweensOf([indicatorRef.value, ...(allTabContents ? Array.from(allTabContents) : [])])
})
</script>

<template>
  <div
    ref="shellRef"
    class="liquid-tabbar-shell"
    @pointerdown="onPointerDown"
    @pointermove="onPointerMove"
    @pointerup="onPointerUp"
    @pointercancel="onPointerCancel"
    @lostpointercapture="onPointerCancel"
  >
    <van-tabbar
      :model-value="visualActiveIndex"
      class="app-tabbar norem"
      placeholder
      safe-area-inset-bottom
      active-color="var(--h5-text-primary)"
      inactive-color="var(--h5-text-secondary)"
    >
      <span ref="indicatorRef" class="app-tabbar__indicator" aria-hidden="true" />
      <van-tabbar-item
        v-for="(item, index) in props.items"
        :key="item.path"
        :name="index"
        :class="{ 'is-visually-active': visualActiveIndex === index }"
        @click="onItemClick(index)"
        @keydown.enter.space.prevent="onItemKeydown($event, index)"
      >
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
