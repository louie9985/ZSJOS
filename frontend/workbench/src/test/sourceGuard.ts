import ts from 'typescript'

const tokenCache = new Map<string, string[]>()
const sourceFileCache = new Map<string, ts.SourceFile>()

const parseSource = (source: string) => {
  const cached = sourceFileCache.get(source)
  if (cached) return cached
  const sourceFile = ts.createSourceFile(
    'source-guard.tsx',
    source,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TSX,
  )
  sourceFileCache.set(source, sourceFile)
  return sourceFile
}

export function sourceTokens(source: string): string[] {
  const cached = tokenCache.get(source)
  if (cached) return cached
  const result: Array<{ kind: ts.SyntaxKind; value: string }> = []
  const sourceFile = parseSource(source)
  const visit = (node: ts.Node) => {
    const children = node.getChildren(sourceFile)
    if (children.length) {
      children.forEach(visit)
      return
    }
    if (node.kind === ts.SyntaxKind.EndOfFileToken) return
    const value = ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)
      ? node.text
      : node.getText(sourceFile)
    if (!value) return
    result.push({ kind: node.kind, value })
  }
  visit(sourceFile)
  const closingTokens = new Set([
    ts.SyntaxKind.CloseBraceToken,
    ts.SyntaxKind.CloseBracketToken,
    ts.SyntaxKind.CloseParenToken,
  ])
  const tokens = result
    .filter((token, index) => token.kind !== ts.SyntaxKind.SemicolonToken
      && !(token.kind === ts.SyntaxKind.CommaToken && closingTokens.has(result[index + 1]?.kind)))
    .map(token => `${ts.SyntaxKind[token.kind]}:${token.value}`)
  tokenCache.set(source, tokens)
  return tokens
}

export function sourceContainsTokens(source: string, expected: string): boolean {
  const sourceTokenList = sourceTokens(source)
  const expectedTokenList = sourceTokens(expected)
  if (expectedTokenList.length === 0 || expectedTokenList.length > sourceTokenList.length) return false

  return sourceTokenList.some((_, start) => expectedTokenList.every(
    (token, offset) => sourceTokenList[start + offset] === token,
  ))
}

const expressionPath = (node: ts.Expression): string | undefined => {
  if (ts.isIdentifier(node)) return node.text
  if (ts.isPropertyAccessExpression(node)) {
    const owner = expressionPath(node.expression)
    return owner ? `${owner}.${node.name.text}` : undefined
  }
  return undefined
}

export function sourceHasCall(source: string, callee: string): boolean {
  const sourceFile = parseSource(source)
  let found = false
  const visit = (node: ts.Node) => {
    if (found) return
    if (ts.isCallExpression(node) && expressionPath(node.expression) === callee) {
      found = true
      return
    }
    ts.forEachChild(node, visit)
  }
  visit(sourceFile)
  return found
}

export function expectSourceToContainTokens(source: string, expected: string): void {
  if (!sourceContainsTokens(source, expected)) {
    throw new Error(`Expected source to contain token sequence:\n${expected}`)
  }
}

export function expectSourceNotToContainTokens(source: string, expected: string): void {
  if (sourceContainsTokens(source, expected)) {
    throw new Error(`Expected source not to contain token sequence:\n${expected}`)
  }
}
