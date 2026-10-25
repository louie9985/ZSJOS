import type { SelectProps } from 'antd'

const placement = (points: string[], offset: number[]) => ({
  points, offset, dynamicInset: true, htmlRegion: 'visible' as const,
  // Flipping alone can still clip a wide popup beside a nearly full-width mobile control.
  overflow: { adjustX: true, adjustY: true, shiftX: true, shiftY: true },
})

/** Apply after local display props so every opted-in filter uses the same popup rules. */
export const filterSelectDisplayProps: Pick<SelectProps, 'popupMatchSelectWidth' | 'virtual' | 'classNames' | 'styles' | 'labelRender' | 'builtinPlacements'> = {
  popupMatchSelectWidth: false,
  // Wrapped labels have variable heights; fixed-height virtual rows would clip them.
  virtual: false,
  builtinPlacements: {
    bottomLeft: placement(['tl', 'bl'], [0, 4]),
    bottomRight: placement(['tr', 'br'], [0, 4]),
    topLeft: placement(['bl', 'tl'], [0, -4]),
    topRight: placement(['br', 'tr'], [0, -4]),
  },
  classNames: { root: 'workbench-filter-select', popup: { root: 'workbench-filter-popup' } },
  styles: { popup: { root: { width: 'min(480px, calc(100vw - 24px))' } } },
  labelRender: ({ label, value }) => {
    const text = label ?? value
    return <span className="workbench-filter-label" title={typeof text === 'string' || typeof text === 'number' ? String(text) : undefined}>{text}</span>
  },
}
