import assert from 'node:assert/strict'
import test from 'node:test'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
import { reactive, ref, watch, nextTick } from 'vue'

const source = fs.readFileSync(
  new URL('../src/components/UploadFile/src/UploadFile.vue', import.meta.url),
  'utf8'
)
const script = source
  .match(/<script[^>]*>([\s\S]*?)<\/script>/)[1]
  .replace(/import[\s\S]*?from ['"][^'"]+['"]\s*\n/g, '')
const compiled = ts.transpileModule(
  script +
    '\nexported = { fileList, beforeUpload, handleFileSuccess, excelUploadError, handleRemove, uploadRef, handlePasteFiles };',
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }
).outputText

function setup(limit = 1) {
  const props = reactive({ modelValue: '', limit, fileType: ['png', 'pdf'], autoUpload: true })
  const errors = []
  const events = []
  const property = { def: () => ({}), isRequired: true }
  const context = {
    exported: undefined,
    ref,
    watch,
    onBeforeUnmount: () => {},
    defineOptions: () => {},
    defineProps: () => props,
    defineEmits: () => (name, value) => {
      events.push([name, value])
      if (name === 'update:modelValue') props.modelValue = value
    },
    propTypes: {
      oneOfType: () => property,
      array: property,
      number: property,
      bool: property,
      string: property
    },
    useMessage: () => ({ success() {}, error: (value) => errors.push(value) }),
    useUpload: () => ({}),
    isString: (value) => typeof value === 'string',
    getFileNameFromUrl: (url) => decodeURIComponent(url.split('/').pop()),
    genFileId: () => 999
  }
  vm.runInNewContext(compiled, context)
  return { ...context.exported, props, errors, events }
}

test('single-file limit accepts the first queued file and rejects unsupported extension', () => {
  const c = setup()
  const file = { uid: 1, name: 'image.PNG', status: 'ready', type: 'image/png' }
  c.fileList.value = [file]
  assert.equal(c.beforeUpload(file), true)
  assert.equal(c.beforeUpload({ ...file, name: 'image.png.exe' }), false)
})

test('success persists URL without losing original filename or uploader', async () => {
  const c = setup()
  const file = { uid: 1, name: '原始截图.png', status: 'success' }
  c.fileList.value = [file]
  c.handleFileSuccess({ code: 0, data: '/files/generated.png' }, file)
  await nextTick()
  assert.equal(c.props.modelValue, '/files/generated.png')
  assert.equal(c.fileList.value[0].name, '原始截图.png')
  assert.equal(c.fileList.value.length, 1)
})

test('one concurrent success survives another failure and retains pending file', async () => {
  const c = setup(3)
  c.props.modelValue = []
  await nextTick()
  const first = { uid: 1, name: 'same.png', status: 'success' }
  const pending = { uid: 2, name: 'same.png', status: 'uploading' }
  c.fileList.value = [first, pending]
  c.handleFileSuccess({ data: '/files/first.png' }, first)
  await nextTick()
  assert.equal(c.fileList.value.length, 2)
  assert.deepEqual([...c.props.modelValue], ['/files/first.png'])
  // Element Plus removes the failed file before invoking onError.
  c.fileList.value = c.fileList.value.filter((file) => file.uid !== 2)
  c.excelUploadError()
  assert.equal(c.fileList.value[0].url, '/files/first.png')
})

test('business failure or malformed response never writes an undefined URL', () => {
  for (const response of [
    { code: 401, msg: '登录失效' },
    { code: 0, data: null }
  ]) {
    const c = setup()
    const file = { uid: 1, name: 'image.png', status: 'success' }
    c.fileList.value = [file]
    c.handleFileSuccess(response, file)
    assert.equal(c.fileList.value.length, 0)
    assert.equal(c.props.modelValue, '')
    assert.equal(c.errors.length, 1)
  }
})

test('removing one same-name file preserves the other and external reset clears the list', async () => {
  const c = setup(3)
  c.props.modelValue = ['/files/a.png', '/files/b.png']
  await nextTick()
  c.fileList.value.splice(0, 1)
  c.handleRemove()
  await nextTick()
  assert.deepEqual([...c.props.modelValue], ['/files/b.png'])
  c.props.modelValue = []
  await nextTick()
  assert.equal(c.fileList.value.length, 0)
  c.props.modelValue = ['/files/b.png']
  await nextTick()
  assert.equal(c.fileList.value[0].name, 'b.png')
  assert.equal(c.fileList.value[0].status, 'success')
})

test('clipboard queue submits when auto-upload is enabled', () => {
  const c = setup()
  let started = 0,
    submitted = 0
  c.uploadRef.value = {
    handleStart() {
      started++
    },
    submit() {
      submitted++
    }
  }
  c.handlePasteFiles([{ name: 'clipboard.png' }])
  assert.equal(started, 1)
  assert.equal(submitted, 1)
})
