import { describe, expect, it } from 'vitest'
import { examBackgroundStyle } from './examCalendarColor'

describe('exam background colors', () => {
  it.each([undefined, null, '', 'red', '#fff', '#00000000', 'url(https://example.com)', '#123456;display:none'])('keeps theme defaults for invalid or absent %s', value => {
    expect(examBackgroundStyle(value)).toBeUndefined()
  })
  it.each([['#ffffff', '#000000'], ['#000000', '#ffffff'], ['#ffcc00', '#000000'], ['#001133', '#ffffff']])('contrasts text against %s', (background, foreground) => {
    expect(examBackgroundStyle(background)).toEqual({ backgroundColor: background, borderColor: background, color: foreground })
  })
})
