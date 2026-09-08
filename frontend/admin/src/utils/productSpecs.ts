export type ProductSpec = {
  attrKey: string
  attrName: string
  value: string
  label: string
  labelMissing?: boolean
}
export type ProductAttr = {
  attrKey?: string
  attrName: string
  values: Array<{ value: string; label: string }>
}
type ProductDisplay = {
  specs?: ProductSpec[] | null
  attrValues?: Record<string, string>
  selectedAttrValues?: string
}

/** Historical values must never be relabelled using today's catalog. */
export function productSpecs(product: ProductDisplay): ProductSpec[] {
  if (product.specs) return product.specs
  let raw = product.attrValues
  if (!raw && product.selectedAttrValues) {
    try {
      raw = JSON.parse(product.selectedAttrValues)
    } catch {
      return []
    }
  }
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return []
  return Object.entries(raw)
    .filter(([, value]) => typeof value === 'string')
    .map(([key, value]) => ({
      attrKey: key,
      attrName: key,
      value,
      label: value,
      labelMissing: true
    }))
}

export function catalogSpecs(
  values: Record<string, string> = {},
  attrs: ProductAttr[] = []
): ProductSpec[] {
  const remaining = new Map(Object.entries(values))
  const result = attrs.flatMap((attr) => {
    const value = attr.attrKey ? values[attr.attrKey] : undefined
    if (value == null) return []
    remaining.delete(attr.attrKey!)
    const option = attr.values.find((item) => item.value === value)
    return [
      {
        attrKey: attr.attrKey!,
        attrName: attr.attrName,
        value,
        label: option?.label || value,
        labelMissing: !option
      }
    ]
  })
  return [
    ...result,
    ...Array.from(remaining, ([key, value]) => ({
      attrKey: key,
      attrName: key,
      value,
      label: value,
      labelMissing: true
    }))
  ]
}

export function specText(spec: ProductSpec): string {
  return `${spec.attrName}：${spec.label}${spec.labelMissing ? '（历史标签缺失）' : ''}`
}

export function productSpecText(product: ProductDisplay): string {
  return productSpecs(product).map(specText).join(' · ')
}
