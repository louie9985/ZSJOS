import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import ts from 'typescript'
import { describe, expect, it } from 'vitest'

function files(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry => entry.isDirectory() ? files(join(dir, entry.name)) : [join(dir, entry.name)])
}
describe('all production business tables use the common entry', () => {
  it('prevents direct table imports and hand-written tables outside the implementation', () => {
    const failures: string[] = []
    const keys = new Set<string>()
    for (const path of [...files('src/pages'), ...files('src/components')].filter(path => path.endsWith('.tsx') && !path.includes('.test.') && !path.includes('/BusinessTable/'))) {
      const source = ts.createSourceFile(path, readFileSync(path, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
      const visit = (node: ts.Node) => {
        if (ts.isImportDeclaration(node) && node.importClause?.namedBindings && ts.isNamedImports(node.importClause.namedBindings)) {
          for (const element of node.importClause.namedBindings.elements) {
            if (!node.importClause.isTypeOnly && !element.isTypeOnly && ['Table', 'ProTable', 'EditableProTable'].includes(element.propertyName?.text || element.name.text)) failures.push(`${path}: direct table import`)
          }
        }
        if (ts.isJsxSelfClosingElement(node) || ts.isJsxOpeningElement(node)) {
          if (node.tagName.getText(source) === 'table') failures.push(`${path}: raw table`)
          if (node.tagName.getText(source) === 'BusinessTable') {
            const key = node.attributes.properties.find((p): p is ts.JsxAttribute => ts.isJsxAttribute(p) && p.name.getText(source) === 'tableKey')?.initializer
            if (!key || !ts.isStringLiteral(key) || keys.has(key.text)) failures.push(`${path}: missing/duplicate tableKey`)
            else keys.add(key.text)
          }
        }
        ts.forEachChild(node, visit)
      }
      visit(source)
    }
    expect(failures).toEqual([])
    expect(keys.size).toBeGreaterThan(40)
  }, 20000)
})
