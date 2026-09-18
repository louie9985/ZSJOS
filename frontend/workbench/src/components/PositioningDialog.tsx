import { Modal, type ModalProps } from 'antd'

export default function PositioningDialog({ styles, ...props }: ModalProps) {
  const resolvedStyles = typeof styles === 'function' ? styles({ props }) : styles
  return <Modal {...props} width={1180} style={{ top: 16, maxWidth: 'calc(100vw - 32px)', paddingBottom: 0, ...props.style }}
    styles={{ ...resolvedStyles, container: { maxHeight: 'calc(100dvh - 32px)', display: 'flex', flexDirection: 'column', ...resolvedStyles?.container },
      body: { minHeight: 0, overflowY: 'auto', overscrollBehavior: 'contain', ...resolvedStyles?.body },
      header: { flexShrink: 0, ...resolvedStyles?.header }, footer: { flexShrink: 0, ...resolvedStyles?.footer } }} />
}
