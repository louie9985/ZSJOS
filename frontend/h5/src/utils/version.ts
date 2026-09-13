export interface ReleaseNote {
  version: string
  date: string
  summary: string
  notes: string[]
}

export interface VersionManifest {
  app: 'zsjos-partner-h5'
  version: string
  buildId: string
  publishedAt: string
  releases: ReleaseNote[]
}

export function parseVersionManifest(value: unknown): VersionManifest {
  const data = value as VersionManifest | null
  if (!data || data.app !== 'zsjos-partner-h5'
    || typeof data.version !== 'string' || !data.version.trim()
    || typeof data.buildId !== 'string' || !data.buildId.trim()
    || typeof data.publishedAt !== 'string' || !Number.isFinite(Date.parse(data.publishedAt))
    || !Array.isArray(data.releases)
    || !data.releases.every(release => release
      && typeof release.version === 'string' && typeof release.date === 'string'
      && typeof release.summary === 'string' && Array.isArray(release.notes)
      && release.notes.every(note => typeof note === 'string'))) {
    throw new Error('版本信息格式不正确，请稍后重试')
  }
  return data
}

// 构建不同也可能是部署回滚，不通过版本号大小推断兼容性。
export function hasDifferentBuild(current: VersionManifest, latest: VersionManifest | null) {
  return latest !== null && current.buildId !== latest.buildId
}

export function buildRefreshUrl(href: string, buildId: string) {
  const url = new URL(href)
  url.searchParams.set('_h5_build', buildId)
  return url.toString()
}
