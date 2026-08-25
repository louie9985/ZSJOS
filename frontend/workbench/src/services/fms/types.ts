/** FMS 账套用户权限级别 */
export const FmsAccountUserLevel = {
  OWNER: 1,
  READ: 2,
  WRITE: 3
} as const

export type FmsAccountUserLevelValue = (typeof FmsAccountUserLevel)[keyof typeof FmsAccountUserLevel]

/** FMS 账套 VO（列表项） */
export interface FmsAccountSetVO {
  id?: number
  companyCode: string
  companyName: string
  companyProfile?: string
  industry?: string
  location?: string
  legalRepresentative?: string
  legalRepresentativeIdNumber?: string
  businessLicenseNumber?: string
  organizationCode?: string
  remark?: string
  contactName?: string
  officeTelephone?: string
  mobile?: string
  faxNumber?: string
  qqNumber?: string
  email?: string
  otherContact?: string
  address?: string
  currencyId?: number
  startTime?: number
  standard?: number
  initialized?: boolean
  defaultStatus?: boolean
  founder?: boolean
  level?: number
  createTime?: string
}

// ========== Ledger ==========

/** 账簿查询参数 */
export interface FmsLedgerListParams {
  accountSetId: number
  startMonth: string
  endMonth: string
  subjectId?: number
  startSubjectId?: number
  endSubjectId?: number
  minLevel?: number
  maxLevel?: number
}

/** 辅助核算账簿查询参数 */
export interface FmsLedgerAuxiliaryListParams {
  accountSetId: number
  startMonth: string
  endMonth: string
  auxiliaryTypeId: number
  subjectId?: number
  auxiliaryItemId?: number
}

/** 总账行 */
export interface FmsLedgerGeneral {
  rowType: number
  subjectId: number
  subjectCode: string
  subjectName: string
  period: string
  digest: string
  debitAmount: number
  creditAmount: number
  balanceDirection: string
  balance: number
}

/** 明细账行 */
export interface FmsLedgerDetail {
  rowType: number
  entryId?: number
  entrySubjectId?: number
  subjectId: number
  subjectCode: string
  subjectName: string
  period: string
  accountDate: string
  voucherId?: number
  voucherNumber?: string
  digest: string
  debitAmount: number
  creditAmount: number
  balanceDirection: string
  balance: number
  debitQuantity: number
  creditQuantity: number
  balanceQuantity: number
  unitPrice?: number
  quantityUnit?: string
  columnAmounts?: Record<number, number>
}

/** 科目余额 */
export interface FmsSubjectBalance {
  nodeKey: string
  nodeType: number
  subjectId: number
  assistCombinationId?: number
  subjectCode: string
  subjectName: string
  level: number
  quantityAccounting: boolean
  quantityUnit?: string
  openingDebitAmount: number
  openingCreditAmount: number
  openingBalanceDirection: string
  openingQuantity: number
  openingUnitPrice: number
  periodDebitAmount: number
  periodCreditAmount: number
  periodDebitQuantity: number
  periodCreditQuantity: number
  yearDebitAmount: number
  yearCreditAmount: number
  yearDebitQuantity: number
  yearCreditQuantity: number
  endingDebitAmount: number
  endingCreditAmount: number
  endingBalanceDirection: string
  endingQuantity: number
  endingUnitPrice: number
  children: FmsSubjectBalance[]
}

/** 核算项目余额 */
export interface FmsLedgerAuxiliaryBalance {
  auxiliaryItemId: number
  code: string
  name: string
  openingDebitAmount: number
  openingCreditAmount: number
  periodDebitAmount: number
  periodCreditAmount: number
  yearDebitAmount: number
  yearCreditAmount: number
  endingDebitAmount: number
  endingCreditAmount: number
}

/** 多栏账科目 */
export interface FmsMultiColumnSubject {
  subjectId: number
  subjectCode: string
  subjectName: string
  balanceDirection: number
}

/** 多栏账 */
export interface FmsMultiColumn {
  columns: FmsMultiColumnSubject[]
  rows: FmsLedgerDetail[]
}

// ========== Closing ==========

/** 结账期间参数 */
export interface FmsClosingPeriodParams {
  accountSetId: number
  month: string
}

/** 结账概况 */
export interface FmsClosingOverview {
  month: string
  closed: boolean
  voucherReviewRequired: boolean
  pendingVoucherCount: number
  voucherCount: number
  profitLossBalance: number
  balanceSheetDifference: number
  profitLossVoucherId?: number
  initialBalanceBalanced: boolean
  voucherNumberContinuous: boolean
  profitLossVoucherGenerated: boolean
  incomeStatementBalanced: boolean
  incomeStatementUnmappedSubjectCount: number
  balanceSheetProfitLossTransferred: boolean
  balanceSheetBalanced: boolean
  balanceSheetUnmappedSubjectCount: number
  canClose: boolean
}

export interface FmsClosingSubjectRule {
  subjectId?: number
  subjectCode?: string
  digest: string
  direction: number
  amountRatio: number
}

export interface FmsClosingSchemeSave {
  id?: number
  accountSetId: number
  name: string
  periodEnd: boolean
  subjectId?: number
  formulaRule: number
  timeType: number
  voucherWordId?: number
  subjects: FmsClosingSubjectRule[]
}

export interface FmsClosingScheme extends FmsClosingSchemeSave {
  id: number
  type: number
  digest?: string
  voucherType?: number
  priorYearAdjustmentSubjectId?: number
  adjustmentClosingSubjectId?: number
  otherClosingSubjectId?: number
  reverseBalance?: boolean
  closingDay?: number
  balance: number
  voucherIds: number[]
}

export interface FmsProfitLossSettings {
  accountSetId: number
  voucherWordId?: number
  digest: string
  voucherType: number
  priorYearAdjustmentSubjectId?: number
  adjustmentClosingSubjectId?: number
  otherClosingSubjectId?: number
  reverseBalance: boolean
  closingDay: number
}

export interface FmsSpecialClosingSettings {
  id: number
  accountSetId: number
  voucherWordId?: number
  subjects: FmsClosingSubjectRule[]
}

export interface FmsClosingTemplate {
  id?: number
  accountSetId: number
  presetCode?: string
  name: string
  category: number
  periodEnd: boolean
  subjectId?: number
  formulaRule?: number
  timeType?: number
  subjects: FmsClosingSubjectRule[]
  sort: number
  createTime?: string
}

// ========== Config: Currency ==========

/** 币别 */
export interface FmsCurrencyVO {
  id?: number
  accountSetId: number
  code: string
  name: string
  exchangeRate: number
  standard?: boolean
  createTime?: string
}

// ========== Config: Digest ==========

/** 常用摘要 */
export interface FmsDigestVO {
  id?: number
  accountSetId: number
  content: string
  createTime?: string
}

// ========== Config: Voucher Word ==========

/** 凭证字 */
export interface FmsVoucherWordVO {
  id?: number
  accountSetId: number
  name: string
  printTitle?: string
  defaultStatus?: boolean
  sort?: number
  createTime?: string
}

// ========== Config: Finance Indicator ==========

/** 财务指标 */
export interface FmsFinanceIndicatorVO {
  id?: number
  accountSetId: number
  name: string
  code: string
  type?: number
  formula?: string
  sort?: number
  status?: number
  createTime?: string
}

// ========== Config: Auxiliary Type ==========

/** 辅助核算类别 */
export interface FmsAuxiliaryTypeVO {
  id?: number
  accountSetId: number
  name: string
  type?: number
  systemPreset?: boolean
}

// ========== Config: Account Set (full) ==========

/** 账套完整信息（扩展列表字段） */
export interface FmsAccountSetFullVO extends FmsAccountSetVO {
}

/** 创建账套请求 */
export interface FmsAccountSetCreateReqVO {
  companyCode: string
  companyName: string
  companyProfile?: string
  industry?: string
  location?: string
  legalRepresentative?: string
  legalRepresentativeIdNumber?: string
  businessLicenseNumber?: string
  organizationCode?: string
  remark?: string
  contactName?: string
  officeTelephone?: string
  mobile?: string
  faxNumber?: string
  qqNumber?: string
  email?: string
  otherContact?: string
  address?: string
}

/** 初始化账套请求 */
export interface FmsAccountSetInitializeReqVO {
  accountSetId: number
  currencyCode: string
  startTime: number
  standard: number
  level: number
  subjectCodeRule: string
  ledgerBalanceMode: number
}

// ========== Config: Account User ==========

/** 账套用户 */
export interface FmsAccountUserVO {
  userId: number
  nickname?: string
  deptName?: string
  mobile?: string
  email?: string
  status?: number
  defaultStatus: boolean
  founder: boolean
  level: number
}

/** 更新账套成员请求 */
export interface FmsAccountUserUpdateReqVO {
  accountSetId: number
  members: Array<{ userId: number; level: number }>
}

// ========== Report ==========

/** 财务报表查询参数 */
export interface FmsReportListParams {
  accountSetId: number
  startMonth: string
  endMonth: string
}

/** 财务报表未映射科目 */
export interface FmsReportUnmappedSubject {
  id: number
  code: string
  name: string
}

/** 财务报表项目 */
export interface FmsReportItem {
  id: number
  name: string
  rowNo: number
  level: number
  editable: boolean
  formula: string
  openingAmount: number
  closingAmount: number
  currentAmount: number
  yearAmount: number
}

/** 资产负债表行 */
export interface FmsBalanceSheetRow {
  rowId: number
  assetId?: number
  assetName?: string
  assetRowNo?: number
  assetClosingAmount?: number
  assetOpeningAmount?: number
  assetLevel?: number
  assetEditable?: boolean
  assetFormula?: string
  liabilityId?: number
  liabilityName?: string
  liabilityRowNo?: number
  liabilityClosingAmount?: number
  liabilityOpeningAmount?: number
  liabilityLevel?: number
  liabilityEditable?: boolean
  liabilityFormula?: string
}

/** 资产负债表检查结果 */
export interface FmsBalanceSheetCheck {
  balanced?: boolean
  initialBalanceBalanced?: boolean
  profitLossTransferred?: boolean
  openingDifferenceAmount?: number
  closingDifferenceAmount?: number
  unmappedSubjects: FmsReportUnmappedSubject[]
}

/** 利润表检查结果 */
export interface FmsIncomeStatementCheck {
  balanced?: boolean
  differenceAmount?: number
  unmappedSubjects: FmsReportUnmappedSubject[]
}

/** 现金流量表检查结果 */
export interface FmsCashFlowCheck {
  balanced?: boolean
  initialBalanceBalanced?: boolean
  profitLossTransferred?: boolean
  balanceSheetReady?: boolean
  openingDifferenceAmount?: number
  closingDifferenceAmount?: number
  unmappedSubjects: FmsReportUnmappedSubject[]
}

/** 现金流量辅助数据 */
export interface FmsCashFlowAdjustment {
  id: number
  name: string
  rowNo: number
  formula: string
  remark?: string
  editable: boolean
  currentAmount: number
  yearAmount: number
  level: number
}

/** 现金流量表修改项参数 */
export interface FmsCashFlowStatementUpdateItem {
  id: number
  currentAmount: number
  yearAmount: number
}

/** 现金流量辅助数据修改项参数 */
export interface FmsCashFlowAdjustmentUpdateItem {
  id: number
  currentAmount: number
  yearAmount: number
}

/** 报表公式修改项参数 */
export interface FmsReportFormulaItemUpdate {
  subjectId: number
  operator: '+' | '-'
  rules: number
}

/** 报表公式修改参数 */
export interface FmsReportFormulaUpdate {
  accountSetId: number
  id: number
  formulas: FmsReportFormulaItemUpdate[]
}

// ========== Subject ==========

/** FMS 科目信息（科目树节点） */
export interface FmsSubjectVO {
  id: number
  accountSetId: number
  code: string
  name: string
  parentId: number
  type?: number
  category?: number
  balanceDirection?: number
  auxiliaryTypeIds?: number[]
  auxiliaryTypeNames?: string[]
  currencyIds?: number[]
  quantityAccounting?: boolean
  quantityUnit?: string
  cash?: boolean
  migrateParentData?: boolean
  auxiliaryMappings?: Array<{ typeId: number; itemId?: number }>
  status?: number
  level?: number
  children?: FmsSubjectVO[]
}

export interface FmsSubjectUsage {
  childCount: number
  voucherEntryCount: number
  initialBalanceCount: number
  auxiliaryCombinationCount: number
  quantityDataCount: number
  used: boolean
}

// ========== Auxiliary Item ==========

/** FMS 辅助核算项目（下拉选项） */
export interface FmsAuxiliaryItemOptionVO {
  id: number
  accountSetId: number
  auxiliaryTypeId: number
  code: string
  name: string
  status?: number
  remark?: string
  specification?: string
  unit?: string
}

// ========== Voucher Statistics ==========

/** 凭证汇总查询参数 */
export interface FmsVoucherStatisticsParams {
  accountSetId: number
  startMonth: string
  endMonth: string
  voucherWordId?: number
  minVoucherNumber?: number
  maxVoucherNumber?: number
  minLevel?: number
  maxLevel?: number
}

/** 凭证汇总信息 */
export interface FmsVoucherStatistics {
  subjectId: number
  subjectCode: string
  subjectName: string
  level: number
  debitAmount: number
  creditAmount: number
}

/** 财务参数 */
export interface FmsFinanceParameter {
  accountSetId: number
  standard?: number
  level: number
  subjectCodeRule: string
  ledgerBalanceMode: number
  voucherReviewRequired: boolean
}

/** 币种信息 */
export interface FmsCurrency {
  id: number
  code: string
  name: string
  symbol?: string
  exchangeRate?: number
}

/** 凭证模板分类 */
export interface FmsVoucherTemplateCategoryVO {
  id?: number
  accountSetId: number
  name: string
}

/** 凭证模板分录 */
export interface FmsVoucherTemplateEntry {
  digest: string
  subjectId: number
  quantity?: number
  unitPrice?: number
  debitAmount?: number
  creditAmount?: number
  auxiliaries: Array<{ type?: number; typeId: number; itemId: number; name?: string }>
}

/** 凭证模板 */
export interface FmsVoucherTemplateVO {
  id?: number
  accountSetId: number
  name: string
  categoryId: number
  categoryName?: string
  entries: FmsVoucherTemplateEntry[]
}

/** 初始余额金额 */
export interface FmsInitialBalanceAmounts {
  openingAmount: number
  openingQuantity: number
  yearDebitAmount: number
  yearDebitQuantity: number
  yearCreditAmount: number
  yearCreditQuantity: number
  yearOpeningAmount: number
  yearOpeningQuantity: number
  profitLossAmount: number
  profitLossQuantity: number
}

/** 初始余额辅助核算配置 */
export interface FmsInitialBalanceAuxiliaryConfig {
  auxiliaryTypeId: number
  type: number
  name: string
}

/** 初始余额辅助核算项目 */
export interface FmsInitialBalanceAuxiliaryItem {
  type: number
  typeId: number
  itemId: number
  name: string
}

/** 初始余额辅助核算余额 */
export interface FmsInitialBalanceAssist extends FmsInitialBalanceAmounts {
  assistCombinationId?: number
  auxiliaries: FmsInitialBalanceAuxiliaryItem[]
}

/** 初始余额 */
export interface FmsInitialBalance {
  id?: number
  subjectId: number
  subjectCode: string
  subjectName: string
  parentId?: number
  type: number
  balanceDirection: number
  quantityAccounting: boolean
  quantityUnit?: string
  auxiliaryAccounting: boolean
  auxiliaryConfigs: FmsInitialBalanceAuxiliaryConfig[]
  assistBalances: FmsInitialBalanceAssist[]
  openingAmount: number
  openingQuantity: number
  yearDebitAmount: number
  yearDebitQuantity: number
  yearCreditAmount: number
  yearCreditQuantity: number
  yearOpeningAmount: number
  yearOpeningQuantity: number
  profitLossAmount: number
  profitLossQuantity: number
}

/** 初始余额辅助核算更新 */
export interface FmsInitialBalanceAssistUpdate extends FmsInitialBalanceAmounts {
  auxiliaryItemIds: number[]
}

/** 初始余额更新 */
export interface FmsInitialBalanceUpdate extends FmsInitialBalanceAmounts {
  subjectId: number
  assistBalances: FmsInitialBalanceAssistUpdate[]
}

/** 试算平衡 */
export interface FmsTrialBalance {
  openingDebitAmount: number
  openingCreditAmount: number
  openingDifferenceAmount: number
  yearDebitAmount: number
  yearCreditAmount: number
  yearDifferenceAmount: number
  balanced: boolean
}
