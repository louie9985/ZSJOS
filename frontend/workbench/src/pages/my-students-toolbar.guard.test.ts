import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('my students detail toolbar', () => {
  const page = readFileSync('src/pages/RegistrationPages.tsx', 'utf8')
  const studentDetail = readFileSync('src/components/StudentDetail.tsx', 'utf8')

  it('passes the planner operations toolbar into the overridden overview content', () => {
    const overviewLine = page
      .split('\n')
      .find(line => line.includes('overviewContent={<LeadDetailOverview'))
    expect(overviewLine).toBeDefined()
    expect(overviewLine).toContain('toolbar={<OverflowToolbar actions={studentToolbarActions} />}')
  })

  it('falls back to the StudentDetail toolbar when no overview content is supplied', () => {
    expect(studentDetail).toContain('toolbar={toolbar}')
  })
})
