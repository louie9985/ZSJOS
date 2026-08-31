import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('PC drawer resizing', () => {
  it('uses the shared resizable drawer for shared PC panels', () => {
    const sources = [
      'src/components/AdvancedFilter.tsx',
      'src/components/SettingsDrawer.tsx',
      'src/pages/LeadAssignmentPage.tsx',
      'src/pages/FeedbackPage.tsx',
      'src/pages/ConfigurationPages.tsx'
    ]
    for (const path of sources) expect(readFileSync(path, 'utf8')).toContain('ResizableDrawer')
  })

  it('leaves mobile-only drawers and modal form flows on their existing components', () => {
    expect(readFileSync('src/layouts/MobileNavDrawer.tsx', 'utf8')).not.toContain('ResizableDrawer')
    expect(readFileSync('src/components/ForcedFormProvider.tsx', 'utf8')).not.toContain('ResizableDrawer')
    expect(readFileSync('src/components/ResizableDrawer.tsx', 'utf8')).toContain("matchMedia('(max-width: 768px)')")
  })
})
