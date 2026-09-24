import { readFileSync, readdirSync } from 'node:fs'
import { resolve, relative } from 'node:path'
import { createRequire } from 'node:module'
import ts from 'typescript'
import { describe, expect, it } from 'vitest'

const root = resolve(process.cwd(), '../..')
const read = (file: string) => readFileSync(resolve(root, file), 'utf8')
interface Page { scene: string; pageKey: string; consumers: Partial<Record<'admin' | 'workbench', string>> }
interface WireExample { scene: string; field: Record<string, unknown>; filter: Record<string, unknown> }
const contract = JSON.parse(read('backend/yudao-module-zsjos/src/test/resources/advanced-filter-contract.json')) as {
  scenes: Record<string, unknown>; pages: Page[]; wireExamples: WireExample[]
  wireTypes: Record<string, string[]>; operatorsByType: Record<string, string[]>
}
const contracts = {
  admin: 'frontend/admin/src/api/zsjos/advancedFilter/index.ts',
  workbench: 'frontend/workbench/src/services/api.ts'
}
type Consumer = keyof typeof contracts
const declarations = (consumer: Consumer) => {
  const source = ts.createSourceFile(contracts[consumer], read(contracts[consumer]), ts.ScriptTarget.Latest, true)
  const names = new Set(['AdvancedFilterField', 'AdvancedFilterOption', 'AdvancedFilterScene', 'AdvancedFilterCondition', 'AdvancedFilterGroup'])
  return source.statements.filter(node => (ts.isTypeAliasDeclaration(node) || ts.isInterfaceDeclaration(node)) && names.has(node.name.text))
    .map(node => node.getText(source)).join('\n')
}

function diagnostics(consumer: Consumer, invalid = false) {
  const examples = structuredClone(contract.wireExamples)
  if (invalid) examples[0].field.valueType = 'arbitrary_sql'
  const content = declarations(consumer) + '\n' + examples.map((example, i) => `
    const scene${i}: AdvancedFilterScene = ${JSON.stringify(example.scene)};
    const field${i}: AdvancedFilterField = ${JSON.stringify(example.field)};
    const group${i}: AdvancedFilterGroup = ${JSON.stringify(example.filter)};
  `).join('\n')
  const file = resolve(root, `output/advanced-filter-${consumer}-virtual.ts`)
  const options: ts.CompilerOptions = { strict: true, noEmit: true, skipLibCheck: true, types: [], target: ts.ScriptTarget.ES2022 }
  const host = ts.createCompilerHost(options)
  const getSource = host.getSourceFile.bind(host)
  host.getSourceFile = (name, version, onError, shouldCreate) => resolve(name) === file
    ? ts.createSourceFile(file, content, ts.ScriptTarget.ES2022, true)
    : getSource(name, version, onError, shouldCreate)
  return ts.getPreEmitDiagnostics(ts.createProgram([file], options, host))
    .map(diagnostic => ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n'))
}

function files(directory: string, extension: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => entry.isDirectory()
    ? files(resolve(directory, entry.name), extension)
    : entry.name.endsWith(extension) && !entry.name.includes('.test.') ? [resolve(directory, entry.name)] : [])
}
interface Entry { file: string; scene: string; pageKey: string }
function workbenchEntries(): Entry[] {
  const entries: Entry[] = []
  for (const file of [...files(resolve(root, 'frontend/workbench/src/pages'), '.tsx'), ...files(resolve(root, 'frontend/workbench/src/components'), '.tsx')]) {
    const source = ts.createSourceFile(file, readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
    function visit(node: ts.Node) {
      if ((ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) && node.tagName.getText(source) === 'AdvancedFilterToolbar') {
        const attrs = node.attributes.properties.filter(ts.isJsxAttribute)
        const scene = attrs.find(attr => attr.name.getText(source) === 'scene')?.initializer
        const page = attrs.find(attr => attr.name.getText(source) === 'pageKey')?.initializer
        expect(scene && ts.isStringLiteral(scene), file + ': scene must have an audited binding').toBe(true)
        expect(page, file + ': pageKey is required').toBeDefined()
        if (!scene || !ts.isStringLiteral(scene) || !page) return
        if (ts.isStringLiteral(page)) entries.push({ file, scene: scene.text, pageKey: page.text })
        else {
          expect(['{`sales_order_approval:${center}`}', '{center ? `sales_order_approval:${center}` : undefined}']).toContain(page.getText(source))
          for (const center of ['registration', 'finance']) entries.push({ file, scene: scene.text, pageKey: `sales_order_approval:${center}` })
        }
      }
      ts.forEachChild(node, visit)
    }
    visit(source)
  }
  return entries
}

interface VueNode { type: number; tag?: string; name?: string; value?: { content: string }; props?: VueNode[]; children?: VueNode[] }
function adminEntries(): Entry[] {
  // Reuse Admin's own Vue parser; no additional test dependency or alternative template grammar.
  const requireAdmin = createRequire(resolve(root, 'frontend/admin/package.json'))
  const sfc = requireAdmin('vue/compiler-sfc') as { parse: (text: string) => { descriptor: { template?: { content: string } } } }
  const requireVue = createRequire(requireAdmin.resolve('vue/package.json'))
  const dom = requireVue('@vue/compiler-dom') as { parse: (text: string) => VueNode }
  const entries: Entry[] = []
  for (const file of files(resolve(root, 'frontend/admin/src/views/zsjos'), '.vue')) {
    const template = sfc.parse(readFileSync(file, 'utf8')).descriptor.template
    if (!template) continue
    function visit(node: VueNode) {
      if (node.type === 1 && ['ZsjosAdvancedFilter', 'WorkbenchListPage'].includes(node.tag ?? '')) {
        const props = node.props?.filter(prop => prop.type === 6) ?? []
        const scene = props.find(prop => ['scene', 'advanced-scene'].includes(prop.name ?? ''))?.value?.content
        const pageKey = props.find(prop => ['page-key', 'advanced-page-key'].includes(prop.name ?? ''))?.value?.content
        if (scene) { expect(pageKey, file).toBeTruthy(); entries.push({ file, scene, pageKey: pageKey! }) }
      }
      node.children?.forEach(visit)
    }
    visit(dom.parse(template.content))
  }
  return entries
}

describe('shared advanced-filter contract', () => {
  for (const consumer of ['admin', 'workbench'] as const) {
    it(`${consumer} accepts backend-verified wire examples using its actual declared types`, () => {
      expect(diagnostics(consumer)).toEqual([])
      expect(diagnostics(consumer, true).length).toBeGreaterThan(0)
      const source = ts.createSourceFile('contract.ts', declarations(consumer), ts.ScriptTarget.Latest, true)
      const scene = source.statements.find(node => ts.isTypeAliasDeclaration(node) && node.name.text === 'AdvancedFilterScene') as ts.TypeAliasDeclaration
      expect(ts.isUnionTypeNode(scene.type)).toBe(true)
      const values = (scene.type as ts.UnionTypeNode).types.map(type => (type as ts.LiteralTypeNode).literal.getText().replace(/["']/g, ''))
      expect(values.sort()).toEqual(Object.keys(contract.scenes).sort())
      const field = source.statements.find(node => (ts.isTypeAliasDeclaration(node) || ts.isInterfaceDeclaration(node)) && node.name.text === 'AdvancedFilterField') as ts.TypeAliasDeclaration | ts.InterfaceDeclaration
      const members = ts.isInterfaceDeclaration(field) ? field.members : (field.type as ts.TypeLiteralNode).members
      for (const [name, expected] of Object.entries(contract.wireTypes)) {
        const member = members.find(node => node.name?.getText(source) === name) as ts.PropertySignature
        expect(member, `${consumer}.${name}`).toBeDefined()
        expect(ts.isUnionTypeNode(member.type!)).toBe(true)
        expect((member.type as ts.UnionTypeNode).types.map(type => (type as ts.LiteralTypeNode).literal.getText(source).replace(/["']/g, '')).sort()).toEqual([...expected].sort())
      }
    })
    it(`${consumer} presents every contracted operator`, () => {
      const text = read(consumer === 'workbench' ? 'frontend/workbench/src/components/AdvancedFilter.tsx'
        : 'frontend/admin/src/views/zsjos/components/ZsjosAdvancedFilterGroup.vue')
      const source = ts.createSourceFile('labels.ts', text.includes('<script') ? text.split('<script setup lang="ts">')[1].split('</script>')[0] : text, ts.ScriptTarget.Latest, true)
      const labels: string[] = []
      function visit(node: ts.Node) {
        if (ts.isVariableDeclaration(node) && node.name.getText(source) === 'operatorLabels' && node.initializer && ts.isObjectLiteralExpression(node.initializer)) {
          labels.push(...node.initializer.properties.map(prop => prop.name!.getText(source).replace(/["']/g, '')))
        }
        ts.forEachChild(node, visit)
      }
      visit(source)
      expect(labels.sort()).toEqual([...new Set(Object.values(contract.operatorsByType).flat())].sort())
    })
    it(`${consumer} audits every concrete page binding against the same backend requirements`, () => {
      const actual = consumer === 'admin' ? adminEntries() : workbenchEntries()
      const folder = consumer === 'admin' ? 'frontend/admin/src/views/zsjos' : 'frontend/workbench/src/pages'
      const expected = contract.pages.filter(page => page.consumers[consumer]).map(page => ({
        file: resolve(root, folder, page.consumers[consumer]!), scene: page.scene, pageKey: page.pageKey
      }))
      const keys = (entries: Entry[]) => [...new Set(entries.map(entry => `${relative(root, entry.file).replaceAll('\\', '/')}|${entry.scene}|${entry.pageKey}`))].sort()
      expect(keys(actual)).toEqual(keys(expected))
    })
  }
})
