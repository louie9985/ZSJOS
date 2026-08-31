/**
 * 针对 https://github.com/xaboy/form-create-designer 封装的工具类
 */
import { isRef } from 'vue'
import formCreate from '@form-create/element-ui'

/** 编码表单 Conf */
export const encodeConf = (designerRef: object) => {
  // @ts-ignore
  // 关联案例：https://gitee.com/yudaocode/yudao-ui-admin-vue3/pulls/834/
  return formCreate.toJson(designerRef.value.getOption())
}

/** 解码表单 Conf */
export const decodeConf = (conf: string) => {
  return formCreate.parseJson(conf)
}

/** 编码表单 Fields */
export const encodeFields = (designerRef: object) => {
  // @ts-ignore
  const rule = designerRef.value.getRule()
  const fields: string[] = []
  rule.forEach((item: any) => {
    fields.push(formCreate.toJson(item))
  })
  return fields
}

/** 解码表单 Fields */
export type ProcessInstanceRelationContext = {
  mode: 'create' | 'detail'
  processInstanceId?: string
}

const injectProcessInstanceRelationContext = (
  node: any,
  context?: ProcessInstanceRelationContext
) => {
  if (!node || typeof node !== 'object') return
  if (node.type === 'ProcessInstanceSelect') {
    node.props = {
      ...(node.props || {}),
      mode: context?.mode || 'create',
      processInstanceId: context?.processInstanceId,
      formField: node.field,
      multiple: true,
      limit: 20
    }
  }
  Object.values(node).forEach((value) => {
    if (Array.isArray(value))
      value.forEach((item) => injectProcessInstanceRelationContext(item, context))
    else if (value && typeof value === 'object')
      injectProcessInstanceRelationContext(value, context)
  })
}

export const decodeFields = (fields: string[], context?: ProcessInstanceRelationContext) => {
  const rule: object[] = []
  fields.forEach((item) => {
    const parsed = formCreate.parseJson(item)
    injectProcessInstanceRelationContext(parsed, context)
    rule.push(parsed)
  })
  return rule
}

/** 设置表单的 Conf 和 Fields，适用 FcDesigner 场景 */
export const setConfAndFields = (designerRef: object, conf: string, fields: string[]) => {
  // @ts-ignore
  designerRef.value.setOption(decodeConf(conf))
  // @ts-ignore
  designerRef.value.setRule(decodeFields(fields))
}

/** 设置表单的 Conf 和 Fields，适用 form-create 场景 */
export const setConfAndFields2 = (
  detailPreview: object,
  conf: string,
  fields: string[],
  value?: object,
  relationContext?: ProcessInstanceRelationContext
) => {
  if (isRef(detailPreview)) {
    // @ts-ignore
    detailPreview = detailPreview.value
  }

  // @ts-ignore
  detailPreview.option = decodeConf(conf)
  // @ts-ignore
  detailPreview.rule = decodeFields(fields, relationContext)

  if (value) {
    // @ts-ignore
    detailPreview.value = value
  }
}
