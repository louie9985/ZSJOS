import type { DrawerProps } from 'antd'
import { DETAIL_DRAWER_WIDTH_STORAGE_KEY } from '../constants'
import ResizableDrawer, { normalizeDrawerWidth, readDrawerWidth } from './ResizableDrawer'

const DETAIL_DRAWER_MIN_WIDTH = 640
const DETAIL_DRAWER_DEFAULT_WIDTH = 'var(--crm-detail-drawer-width)'

export const normalizeDetailDrawerWidth = (width: number, viewportWidth: number) => normalizeDrawerWidth(width, viewportWidth, DETAIL_DRAWER_MIN_WIDTH)

export const readDetailDrawerWidth = (storage: Pick<Storage, 'getItem'>, viewportWidth: number) => readDrawerWidth(storage, DETAIL_DRAWER_WIDTH_STORAGE_KEY, viewportWidth, DETAIL_DRAWER_MIN_WIDTH)

type ResizableDetailDrawerProps = Omit<DrawerProps, 'defaultSize' | 'maxSize' | 'panelRef' | 'resizable' | 'size'> & {
  desktopResizable: boolean
}

export default function ResizableDetailDrawer({
  desktopResizable,
  open,
  rootClassName,
  width,
  ...props
}: ResizableDetailDrawerProps) {
  return <ResizableDrawer {...props} open={open} rootClassName={[rootClassName, desktopResizable && 'crm-resizable-detail-drawer'].filter(Boolean).join(' ')} width={width} desktopResizable={desktopResizable} storageKey={DETAIL_DRAWER_WIDTH_STORAGE_KEY} defaultSize={DETAIL_DRAWER_DEFAULT_WIDTH} minSize={DETAIL_DRAWER_MIN_WIDTH}/>
}
