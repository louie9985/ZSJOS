/**
 * Build a new browser URL for an image retry.
 *
 * Historical public-storage URLs can retain expired X-Amz query parameters.
 * Remove only those signature parameters and keep other application query
 * parameters intact; the object path remains the same.
 */
export function retryImageUrl(value: string, attempt: number): string {
  try {
    const isRelative = value.startsWith('/') && !value.startsWith('//')
    const url = new URL(value, 'http://image-retry.invalid')
    const signed = Array.from(url.searchParams.keys()).some(key => /^x-amz-/i.test(key))
    if (signed) {
      Array.from(url.searchParams.keys()).forEach(key => {
        if (/^x-amz-/i.test(key)) url.searchParams.delete(key)
      })
    }
    url.searchParams.set('__image_retry', String(Math.max(1, attempt)))
    if (isRelative) return `${url.pathname}${url.search}${url.hash}`
    return url.toString()
  } catch {
    return value
  }
}
