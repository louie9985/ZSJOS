/** Build a browser retry URL without expired object-storage signatures. */
export function retryImageUrl(value: string, attempt: number): string {
  try {
    const isRelative = value.startsWith('/') && !value.startsWith('//')
    const url = new URL(value, 'http://image-retry.invalid')
    const signed = Array.from(url.searchParams.keys()).some((key) => /^x-amz-/i.test(key))
    if (signed) {
      Array.from(url.searchParams.keys()).forEach((key) => {
        if (/^x-amz-/i.test(key)) url.searchParams.delete(key)
      })
    }
    url.searchParams.set('__image_retry', String(Math.max(1, attempt)))
    return isRelative ? `${url.pathname}${url.search}${url.hash}` : url.toString()
  } catch {
    return value
  }
}
