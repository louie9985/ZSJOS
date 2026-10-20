const execute = async ({ url, data }: { url: string; data?: unknown }, method: string) => {
  const response = await fetch(url, {
    method,
    body: data instanceof FormData ? data : data === undefined ? undefined : JSON.stringify(data),
    headers:
      data instanceof FormData || data === undefined ? {} : { 'Content-Type': 'application/json' }
  })
  const result = await response.json()
  if (result.code !== 0) throw Error(result.msg)
  return result.data
}
export default {
  get: (args: { url: string }) => execute(args, 'GET'),
  post: (args: { url: string; data: unknown }) => execute(args, 'POST'),
  upload: (args: { url: string; data: FormData }) => execute(args, 'POST'),
  download: async ({ url }: { url: string }) => (await fetch(url)).blob()
}
