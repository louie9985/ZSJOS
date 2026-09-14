import type { ComponentProps } from 'react'
import ViralAccountMaterialForm from './ViralAccountMaterialForm'

/** 内容拆解沿用账号拆解的布局和协议，并保持自己的字段标题配置。 */
export default function ViralContentMaterialForm(props: ComponentProps<typeof ViralAccountMaterialForm>) {
  return <ViralAccountMaterialForm {...props} titleFieldKey="work_title" coverLabel="封面图"
    coverRequiredMessage="请上传封面图" sectionLabels={{ ACCOUNT_DETAIL: '作品详情' }} />
}
