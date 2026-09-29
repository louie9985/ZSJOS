import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

/**
 * 发布登记与发布结果展示的契约守卫。
 *
 * 业务前提：作品通常是发布之后才回到系统登记，所以登记的是**真实发布时间**，
 * 允许早于当前时间。制作/提报阶段的「预计发布时间」才是未来时间语义，
 * 两者的校验不得互相传染。
 */
describe('content publish registration and result display', () => {
  const reviewPage = readFileSync('src/pages/ContentReviewBatchPage.tsx', 'utf8')
  const productionPage = readFileSync('src/pages/ContentProductionPage.tsx', 'utf8')
  const publishedWorks = readFileSync('src/components/AccountPublishedWorks.tsx', 'utf8')

  it('accepts a real publish time in the past', () => {
    // 只保留必填；不得再出现“必须晚于当前时间”一类未来时间校验。
    const field = reviewPage.split('name="publishedAt"')[1]?.split('/>')[0] ?? ''
    expect(field).toContain("message: '请选择发布时间'")
    expect(field).not.toContain('isAfter(dayjs()')
    expect(field).not.toContain('必须晚于当前时间')
    expect(field).not.toContain('不能早于')
    expect(reviewPage).toContain('可早于当前时间')
  })

  it('keeps the future-time rule on the planned publish time only', () => {
    // 预计发布时间属于制作阶段，仍要求未来时间；本次改动不得顺手放开。
    expect(productionPage).toContain('预计发布时间不能早于当前时间')
    expect(reviewPage).not.toContain('预计发布时间不能早于当前时间')
  })

  it('renders publish links through the shared resource link component', () => {
    expect(reviewPage).toContain('<ResourceLink href={item.publishedPlatformUrl}')
    // 卡片内的链接不能嵌在 <button> 里：无效结构且会吞掉卡片交互。
    expect(publishedWorks).toContain('account-work-link')
    expect(publishedWorks).toContain('<ResourceLink href={workUrl}')
    expect(publishedWorks).toContain('<ResourceLink href={safeWorkUrl(detail.publishedUrl)!}')
    expect(publishedWorks).not.toContain('target="_blank"')
    // 内容生产页的版本链接同样复用该组件，不再手写 <a>。
    expect(productionPage).toContain('<ResourceLink href={currentVersion.detailUrl}')
    expect(productionPage).toContain('<ResourceLink href={currentVersion.leadResourceUrl}')
    expect(productionPage).toContain('<ResourceLink href={currentVersion.referenceWorkUrl}')
  })

  it('shows the registered publish result on the content production page', () => {
    expect(productionPage).toContain('content-production-published')
    expect(productionPage).toContain('<ResourceLink href={selected.publishedUrl}')
  })
})
