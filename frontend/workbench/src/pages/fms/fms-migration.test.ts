import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { toAccountSetStartTime } from './FmsConfigAccountSetPage'
import { buildInitialBalanceViewRows, serializeInitialBalanceRows } from './FmsConfigInitialBalancePage'
import { canWriteVoucher } from './FmsVoucherCreatePage'
import type { FmsInitialBalance } from '../../services/fms/types'

function balance(overrides: Partial<FmsInitialBalance>): FmsInitialBalance {
  return {
    subjectId: 1,
    subjectCode: '1001',
    subjectName: '库存现金',
    type: 1,
    balanceDirection: 1,
    quantityAccounting: false,
    auxiliaryAccounting: false,
    auxiliaryConfigs: [],
    assistBalances: [],
    openingAmount: 0,
    openingQuantity: 0,
    yearDebitAmount: 0,
    yearDebitQuantity: 0,
    yearCreditAmount: 0,
    yearCreditQuantity: 0,
    yearOpeningAmount: 0,
    yearOpeningQuantity: 0,
    profitLossAmount: 0,
    profitLossQuantity: 0,
    ...overrides
  }
}

describe('FMS 迁移高风险逻辑', () => {
  it('账套启用期间始终提交所选月份的月初毫秒值', () => {
    expect(toAccountSetStartTime(dayjs('2026-08-25 17:30:00'))).toBe(dayjs('2026-08-01 00:00:00').valueOf())
  })

  it('初始余额只保存末级科目并保留辅助核算组合', () => {
    const rows = buildInitialBalanceViewRows([
      balance({ subjectId: 1, subjectCode: '1001', subjectName: '现金' }),
      balance({
        subjectId: 2,
        parentId: 1,
        subjectCode: '100101',
        subjectName: '人民币现金',
        auxiliaryAccounting: true,
        openingAmount: 88,
        assistBalances: [{
          assistCombinationId: 9,
          auxiliaries: [{ type: 1, typeId: 3, itemId: 7, name: '财务部' }],
          openingAmount: 88,
          openingQuantity: 0,
          yearDebitAmount: 0,
          yearDebitQuantity: 0,
          yearCreditAmount: 0,
          yearCreditQuantity: 0,
          yearOpeningAmount: 0,
          yearOpeningQuantity: 0,
          profitLossAmount: 0,
          profitLossQuantity: 0
        }]
      })
    ])

    expect(serializeInitialBalanceRows(rows)).toEqual([expect.objectContaining({
      subjectId: 2,
      assistBalances: [expect.objectContaining({ auxiliaryItemIds: [7], openingAmount: 88 })]
    })])
  })

  it('新增凭证要求 create 权限，已有凭证要求 update 权限', () => {
    expect(canWriteVoucher(undefined, true, ['fms:voucher:create'])).toBe(true)
    expect(canWriteVoucher(12, true, ['fms:voucher:create'])).toBe(false)
    expect(canWriteVoucher(12, true, ['fms:voucher:update'])).toBe(true)
    expect(canWriteVoucher(undefined, false, ['fms:voucher:create'])).toBe(false)
  })
})
