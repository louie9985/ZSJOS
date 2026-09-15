import assert from 'node:assert/strict'
import test from 'node:test'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

function loadApi(name: string) {
  const calls: { method: string; url: string; params?: unknown; data?: unknown }[] = []
  const request = Object.fromEntries(
    ['get', 'post', 'put', 'delete'].map((method) => [
      method,
      async (options: object) => {
        calls.push({ method, ...options } as (typeof calls)[number])
        return { list: [], total: 0 }
      }
    ])
  )
  const source = readFileSync(
    new URL(`../src/api/zsjos/payment/${name}.ts`, import.meta.url),
    'utf8'
  )
  const js = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true }
  }).outputText
  const exports: Record<string, (...args: unknown[]) => Promise<unknown>> = {}
  runInNewContext(js, {
    exports,
    require: (id: string) => {
      assert.equal(id, '@/config/axios')
      return request
    }
  })
  return { api: exports, calls }
}

test('subject queries use registered endpoints and status updates use query parameters', async () => {
  const { api, calls } = loadApi('subject')
  await api.getPaymentSubjectPage({ pageNo: 2, pageSize: 10, status: 0 })
  await api.getPaymentSubjectSimpleList()
  await api.updatePaymentSubjectStatus(7, 0)
  assert.deepEqual(JSON.parse(JSON.stringify(calls)), [
    {
      method: 'get',
      url: '/zsjos/payment-subject/page',
      params: { pageNo: 2, pageSize: 10, status: 0 }
    },
    { method: 'get', url: '/zsjos/payment-subject/simple-list' },
    { method: 'put', url: '/zsjos/payment-subject/update-status', params: { id: 7, status: 0 } }
  ])
})

test('product filtering and batch save preserve paymentSubjectId', async () => {
  const { api, calls } = loadApi('productSubject')
  await api.getProductPaymentSubjectPage({ pageNo: 1, pageSize: 10, paymentSubjectId: 9 })
  await api.configProductPaymentSubject({ productIds: [1, 2], paymentSubjectId: 9 })
  assert.deepEqual(JSON.parse(JSON.stringify(calls)), [
    {
      method: 'get',
      url: '/zsjos/product-payment-subject/page',
      params: { pageNo: 1, pageSize: 10, paymentSubjectId: 9 }
    },
    {
      method: 'post',
      url: '/zsjos/product-payment-subject/batch-configure',
      data: { productIds: [1, 2], paymentSubjectId: 9 }
    }
  ])
})
