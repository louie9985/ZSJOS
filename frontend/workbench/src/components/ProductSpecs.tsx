import { productSpecs, specText, type ProductSpec } from '../services/productSpecs'
import '../styles/components/product-specs.css'

export default function ProductSpecs({ product }: { product: { specs?: ProductSpec[] | null; attrValues?: Record<string, string>; selectedAttrValues?: string } }) {
  const specs = productSpecs(product)
  return specs.length ? <span className="product-specs">{specs.map(spec => <span key={spec.attrKey}>{specText(spec)}</span>)}</span> : null
}
