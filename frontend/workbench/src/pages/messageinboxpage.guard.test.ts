import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = readFileSync(new URL('./messageinboxpage.tsx', import.meta.url), 'utf8')

describe('message inbox category guard', () => {
  it('renders the six-category filter and keeps deep-linked messages visible', () => {
    expect(source).toContain('Segmented')
    expect(source).toContain('NOTIFY_MESSAGE_CATEGORY_ORDER')
    expect(source).toContain('category === \'all\' || notifyMessageCategoryOf(item) === category')
    expect(source).toContain('加载更多')
  })
})
