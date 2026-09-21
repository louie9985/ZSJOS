import { useHref } from 'react-router-dom'
import type { ComponentProps } from 'react'
import { App, Typography } from 'antd'
import { APP_ROUTES } from '../constants'
import ViralAccountMaterialForm from './ViralAccountMaterialForm'

/** 内容拆解沿用账号拆解的布局和协议，并保持自己的字段标题配置。 */
export default function ViralContentMaterialForm(props: ComponentProps<typeof ViralAccountMaterialForm>) {
  const materialsHref = useHref(`${APP_ROUTES.MATERIAL_LIBRARY}?view=mine`)
  const { modal } = App.useApp()
  return <ViralAccountMaterialForm {...props} requireDraftContent titleFieldKey="work_title" coverLabel="封面图"
    onSaved={result => {
      props.onSaved(result)
      if (!result.submitted) modal.success({
        title: '草稿保存成功',
        content: <><Typography.Paragraph>可在「素材库 → 我的素材」中找到已保存的爆款内容拆解，打开后点击「继续编辑」，补充内容或提交审批。</Typography.Paragraph>
          <Typography.Link href={materialsHref}>前往我的素材</Typography.Link></>,
        okText: '知道了'
      })
    }}
    coverRequiredMessage="请上传封面图" sectionLabels={{ ACCOUNT_DETAIL: '作品详情' }} />
}
