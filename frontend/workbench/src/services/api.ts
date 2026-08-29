import axios, { type AxiosRequestConfig } from "axios";
import type { AxiosHeaderValue } from "axios";
import {
  APP_CONFIG,
  AUTH_CLIENT_IDS,
  STORAGE_KEYS,
  type AuthPlatform,
} from "../constants";
import {
  clearAuthStorage as clearPlatformAuthStorage,
  getAuthAccessToken,
  getAuthPlatform,
  getAuthStorageKeys,
  migrateLegacyAuthStorage,
} from "./authSession";

export { getAuthAccessToken, migrateLegacyAuthStorage } from "./authSession";
import {
  handleImpersonationInvalid,
  resolveImpersonationSessionHeader,
} from "./impersonation";
import { createIdempotencyKey } from "./idempotency";
import type { Timestamp } from "./time";

export type User = {
  id: number;
  nickname: string;
  avatar?: string;
  username?: string;
};
export type UserProfile = {
  id: number;
  username: string;
  nickname: string;
  email?: string;
  mobile?: string;
  sex: number;
  avatar?: string;
  createTime: Timestamp;
  dept?: { id: number; name: string };
  posts?: Array<{ id: number; name: string }>;
};
export type UserProfileUpdate = {
  nickname?: string;
  email?: string;
  mobile?: string;
  sex?: number;
  avatar?: string;
};
export type SocialUser = {
  id: number;
  type: number;
  openid: string;
  nickname?: string;
  avatar?: string;
};
export type MenuRenderMode = "native" | "admin_embed" | "admin_only";
export type RawMenu = {
  id: number;
  sourceMenuId?: number;
  layoutKey?: string;
  name: string;
  path?: string;
  icon?: string;
  component?: string;
  componentName?: string;
  workbenchRenderMode?: MenuRenderMode;
  visible?: boolean;
  keepAlive?: boolean;
  alwaysShow?: boolean;
  type?: number;
  sort?: number;
  parentId: number;
  children?: RawMenu[];
};
export type WorkbenchMenu = Omit<RawMenu, "children" | "path"> & {
  path: string;
  hidden: boolean;
  noCache: boolean;
  alwaysShow: boolean;
  children: WorkbenchMenu[];
};
export type WorkbenchLayoutMeta = {
  globalVersionId?: number;
  globalVersionNo?: number;
  appliedRoleLayouts: Array<{
    roleId: number;
    versionId?: number;
    versionNo?: number;
    priority?: number;
  }>;
  fallback: boolean;
  fallbackReason?: string;
};
export type PermissionInfo = {
  user: User;
  roles: string[];
  permissions: string[];
  menus: RawMenu[];
  workbenchMenus?: RawMenu[];
  workbenchLayoutMeta?: WorkbenchLayoutMeta;
  defaultAvatar?: string;
};
export type DictData = {
  label: string;
  value: string;
  dictType: string;
  colorType?: string;
  cssClass?: string;
};
export type EamAssetItem = {
  itemType: string;
  holdingId?: number;
  assetId?: number;
  assetCode?: string;
  stockBalanceId?: number;
  name: string;
  quantity: number;
  unit?: string;
  custodyMode?: number;
  status: number;
  signedAt?: Timestamp;
  returnAppliedAt?: Timestamp;
  returnResult?: number;
};
export type EamAssetTask = {
  id: number;
  type: number;
  status: number;
  processInstanceId?: string;
  demandId?: number;
  plannedLeaveTime?: Timestamp;
  remark?: string;
  createTime?: Timestamp;
};
export type EamAssetSummary = {
  employeeId: number;
  userId?: number;
  items: EamAssetItem[];
  tasks: EamAssetTask[];
  pendingSignCount: number;
  pendingReturnCount: number;
  offboardingUncleared: boolean;
};
export type EamDemandItem = {
  id?: number;
  name: string;
  categoryId: number;
  managementMode?: number;
  deliveryModeLabelSnapshot?: string;
  custodyModeLabelSnapshot?: string;
  quantity: number;
  unit?: string;
  extFields?: Record<string, unknown>;
  extFieldLabels?: Record<string, string>;
  extFieldDictTypes?: Record<string, string>;
  reservedQuantity?: number;
  purchasedQuantity?: number;
  fulfilledQuantity?: number;
  closedQuantity?: number;
};
export type EamDemand = {
  id?: number;
  no?: string;
  status?: number;
  processInstanceId?: string;
  reason?: string;
  createTime?: Timestamp;
  items: EamDemandItem[];
};
export type EamStockCandidate = {
  candidateType: "SERIALIZED" | "BATCH";
  assetId?: number;
  assetCode?: string;
  stockBalanceId?: number;
  name: string;
  categoryId: number;
  availableQuantity: number;
  unit?: string;
};
export type EamCategory = {
  id: number;
  parentId: number;
  name: string;
  code: string;
  unit: string;
  status: number;
  managementMode: number;
  effectiveDeliveryMode?: number;
  effectiveCustodyMode?: number;
};
export type EamCategoryField = {
  fieldKey: string;
  fieldName: string;
  fieldType: number;
  required: boolean;
  collectionVisible?: boolean;
  collectionRequired?: boolean;
  options?: string[];
  optionSource?: string;
  dictType?: string;
};
export type MediaAccountDetailSnapshot = {
  key: string;
  label: string;
  type: string;
  value: unknown;
  displayValue?: string;
  dictType?: string;
};
export type MediaAccountField = {
  key: string;
  label: string;
  type:
    | "text"
    | "textarea"
    | "number"
    | "date"
    | "select"
    | "multi_select"
    | "boolean";
  required: boolean;
  enabled: boolean;
  sort: number;
  dictType?: string;
  searchable: boolean;
};
export type MediaAccountFieldConfig = {
  id: number;
  versionNo: number;
  version: number;
  fields: MediaAccountField[];
};
export type MediaAccount = {
  id: number;
  accountNo: string;
  nickname: string;
  platformValue: string;
  platformLabelSnapshot: string;
  platformAccountId?: string;
  leadDirection?: string;
  studentPersonId?: number;
  directorUserId?: number;
  detailConfigVersionId?: number;
  detailValues?: Record<string, unknown>;
  detailSnapshots?: MediaAccountDetailSnapshot[];
  accountGradeValue?: string;
  accountGradeLabelSnapshot?: string;
  healthStatusValue?: string;
  healthStatusLabelSnapshot?: string;
  riskLevelValue?: string;
  riskLevelLabelSnapshot?: string;
  healthJson?: string;
  rescueStatus?: string;
  rebindProcessInstanceId?: string;
  sStage?: string;
  sStageLabelSnapshot?: string;
  currentStatusValue?: string;
  currentStatusLabelSnapshot?: string;
  status?: string;
  version: number;
  availableActions: string[];
};
export type MediaAccountMaintenanceProblem = {
  value: string;
  labelSnapshot: string;
};
export type MediaAccountMaintenance = {
  currentStatusValue?: string;
  currentStatusLabelSnapshot?: string;
  stageValue?: string;
  stageLabelSnapshot?: string;
  primaryProblems: MediaAccountMaintenanceProblem[];
  executionMeasureValue?: string;
  executionMeasureLabelSnapshot?: string;
  adjustmentDirection?: string;
  startDate?: string;
  endDate?: string;
};
export type MediaAccountMaintenanceRevision = MediaAccountMaintenance & {
  id: number;
  revisionNo: number;
  changedFields: string[];
  operatedByUserId: number;
  operatedByUserName?: string;
  operatedAt: Timestamp;
};
export type MediaAccountCalendarItem = {
  id: number;
  accountNo: string;
  nickname?: string;
  platformLabelSnapshot?: string;
  studentPersonId?: number;
  studentName?: string;
  directorUserId?: number;
  directorUserName?: string;
  operatorUserId?: number;
  operatorUserName?: string;
  currentStatusValue?: string;
  currentStatusLabelSnapshot?: string;
  stageValue?: string;
  stageLabelSnapshot?: string;
  startDate: string;
  endDate: string;
};
export type MediaAccountCalendarResult = {
  list: MediaAccountCalendarItem[];
  total: number;
  unscheduledCount: number;
};
export type MediaAccountCalendarCandidates = {
  directors: SimpleUser[];
  operators: SimpleUser[];
};
export type MediaContent = {
  id: number;
  contentNo: string;
  accountId: number;
  title: string;
  status: string;
  version: number;
  availableActions: string[];
};
export type MediaReview = {
  id: number;
  reviewNo: string;
  reviewType: string;
  subjectType: string;
  subjectId: number;
  reviewerUserId?: number;
  rejectReason?: string;
  status: string;
  version: number;
  availableActions: string[];
};
export type ProductionTicketDispatchContext = {
  accountId: number;
  accountNo?: string;
  accountName?: string;
  platformLabel?: string;
  studentName?: string;
  accountFields?: MediaAccountDetailSnapshot[];
  positioningSubmissionId?: number;
  positioning?: PositioningTicketSnapshot;
  operatorRemark?: string;
};
export type PositioningTicketSnapshot = {
  submissionNo?: number;
  submittedAt?: Timestamp;
  fields?: StudentContactFormField[];
  values?: Record<string, unknown>;
  dict?: Record<string, unknown>;
  layer1?: Record<string, unknown>;
  layer2?: Record<string, unknown>;
  formula?: Record<string, unknown>;
  feasibility?: Record<string, unknown>;
  contentForm?: Record<string, unknown>;
  compliance?: Record<string, unknown>;
  professionalRisk?: boolean;
};
export type ProductionTicket = {
  id: number;
  ticketNo: string;
  accountId: number;
  ownerOperatorUserId?: number;
  reviewerUserId?: number;
  assigneeFilmingEditorUserId?: number;
  positioningSubmissionId?: number;
  dispatchContext?: ProductionTicketDispatchContext;
  status: string;
  version: number;
  revisionCount?: number;
  expectedDeliveredAt?: Timestamp;
  deadlineAt?: Timestamp;
  availableActions: string[];
};
export type ProductionTicketCreateContext = ProductionTicketDispatchContext & {
  sceneCode: string;
  templateName: string;
  allowedAssignmentTypes: string[];
  targetDeptIds?: number[];
  fields?: import('./workOrderApi').WorkOrderField[];
  canCreate: boolean;
  unavailableReason?: string;
  assigneeCandidates: AssignmentUser[];
};
export type PositioningCard = {
  id: number;
  cardNo: string;
  accountId: number;
  studentPersonId?: number;
  serviceRelationId?: number;
  directorUserId?: number;
  operatorUserId?: number;
  templateId?: number;
  templateVersionId?: number;
  fieldsSnapshot?: StudentContactFormField[];
  valuesSnapshot?: Record<string, unknown>;
  dictSnapshot?: Record<string, unknown>;
  trialEndDate?: string;
  status: string;
  professionalRisk?: boolean;
  versionNo?: number;
  version: number;
  availableActions: string[];
};
export type PositioningCardDraftRequest = {
  accountId: number;
  studentPersonId?: number;
  serviceRelationId?: number;
  templateId?: number;
  trialEndDate?: string;
  values?: Record<string, unknown>;
  version?: number;
  professionalRisk?: boolean;
  layer1Json?: string;
  layer2Json?: string;
  formulaJson?: string;
  feasibilityJson?: string;
  contentFormJson?: string;
  complianceJson?: string;
};
export type PositioningCardDraftResult = { id: number; version: number };
export type PositioningLinkResult = { sharePath: string; expiresAt: Timestamp };
export type PositioningCardImportSource = {
  submissionId: number;
  cardId: number;
  cardNo: string;
  accountId: number;
  accountLabel: string;
  submissionNo: number;
  status: string;
  submittedAt?: Timestamp;
  sameAccount: boolean;
};
export type PositioningCardImportResult = PositioningCardDraftResult &
  DirectorTemplateSnapshot & {
    trialEndDate?: string;
    professionalRisk: boolean;
    skippedFieldKeys: string[];
  };
export type SalesUser = {
  id: number;
  nickname: string;
  maskedMobile?: string;
  deptName?: string;
  avatar?: string;
};
export type AssignmentUser = SalesUser & { deptId?: number; status: number };
export type AssignmentRelation = AssignmentUser & {
  salesUsers: AssignmentUser[];
  validSalesCount: number;
  invalidSalesCount: number;
  updateTime?: Timestamp;
};
export type AssignmentLog = {
  id: number;
  sourceUsers: string;
  targetUsers: string;
  actionType: "append" | "replace" | "remove";
  operatorName: string;
  createTime: Timestamp;
};
export type PageResult<T> = { list: T[]; total: number };
export type CursorPageResult<T> = {
  list: T[];
  nextCursor?: string;
  hasMore: boolean;
};
export type RegistrationAttachment = {
  id: number;
  infraFileId: number;
  fileUrl: string;
  originalName: string;
  contentType?: string;
  fileSize: number;
  uploadedByUserId: number;
  uploadedByUserName?: string;
  uploadedAt?: Timestamp;
};
export type RegistrationRoute = {
  id: number;
  optionKey: string;
  departmentId: number;
  departmentName: string;
  assigneeType: "study_planner" | "content_director";
  assigneeTypeLabel: string;
  selected: boolean;
  assigneeUserId?: number;
  assigneeUserName?: string;
  sort: number;
};
export type RegistrationCase = {
  id: number;
  orderId: number;
  orderNo?: string;
  orderStatus?: string;
  orderStatusLabel?: string;
  studentName?: string;
  studentMobile?: string;
  leadNo?: string;
  status: string;
  statusLabel?: string;
  studyPlannerUserId?: number;
  studyPlannerUserName?: string;
  registrationApprovedAt?: Timestamp;
  completedAt?: Timestamp;
  cancelledAt?: Timestamp;
  cancelReason?: string;
  version: number;
  completable?: boolean;
  completionBlockCode?: string;
  completionBlockReason?: string;
  items?: Array<{
    id: number;
    itemKey: string;
    itemType: string;
    title: string;
    sort: number;
    checked?: boolean;
    checkedByUserName?: string;
    checkedAt?: Timestamp;
    attachmentRequired?: boolean;
    attachments?: RegistrationAttachment[];
  }>;
  routes?: RegistrationRoute[];
};
export type StudyPlanner = { id: number; nickname: string };
export type RegistrationRouteOption = {
  id: number;
  optionKey: string;
  departmentId: number;
  departmentName: string;
  assigneeType: "study_planner" | "content_director";
  assigneeTypeLabel: string;
  sort: number;
  enabled: boolean;
  systemRequired: boolean;
};
export type RegistrationChecklistConfig = {
  templateId: number;
  templateVersion: number;
  published?: {
    id: number;
    versionNo: number;
    status: string;
    items: RegistrationChecklistItem[];
    routeOptions: RegistrationRouteOption[];
  };
  draft?: {
    id: number;
    versionNo: number;
    status: string;
    items: RegistrationChecklistItem[];
    routeOptions: RegistrationRouteOption[];
  };
};
export type RegistrationChecklistItem = {
  id: number;
  itemKey: string;
  itemType: "checkbox" | "attachment" | "study_planner";
  title: string;
  sort: number;
  enabled: boolean;
  systemRequired: boolean;
  attachmentRequired?: boolean;
};
export type StudentTaskStage = {
  key: string;
  label: string;
  status: "done" | "current" | "pending";
  detail: string;
};
export type MyStudent = {
  personId: number;
  personNo?: string;
  leadId?: number;
  leadNo?: string;
  name?: string;
  mobile?: string;
  wechatId?: string;
  activatedAt?: Timestamp;
  services: Array<{
    serviceRelationId: number;
    leadId?: number;
    leadNo?: string;
    orderId?: number;
    orderNo?: string;
    courseName?: string;
    skuName?: string;
    categoryPath?: string[];
    attributeValues?: string[];
    productSnapshot?: string;
    status: string;
    activatedAt?: Timestamp;
    acceptanceStatus?: string;
    acceptedAt?: Timestamp;
    version?: number;
    owner?: boolean;
    ownerUserId?: number;
    ownerUserName?: string;
    contentDirectorUserId?: number;
    contentDirectorUserName?: string;
    careerPlannerUserId?: number;
    careerPlannerUserName?: string;
    operatorUserId?: number;
    operatorUserName?: string;
    directorStage?: string;
    directorInterviewAt?: Timestamp;
  }>;
};
export type MediaStudentDetail = {
  student: MyStudent;
  accounts: Array<{
    id: number;
    accountNo: string;
    nickname?: string;
    platformLabel?: string;
    stage?: string;
    stageLabelSnapshot?: string;
    currentStatusValue?: string;
    currentStatusLabelSnapshot?: string;
    primaryProblems: MediaAccountMaintenanceProblem[];
    executionMeasureValue?: string;
    executionMeasureLabelSnapshot?: string;
    adjustmentDirection?: string;
    maintenanceStartDate?: string;
    maintenanceEndDate?: string;
    runStatus?: string;
    version: number;
    lastActivityAt?: Timestamp;
    availableActions: string[];
    detailSnapshots: MediaAccountDetailSnapshot[];
    taskLine: StudentTaskStage[];
  }>;
  positioningCards: Array<{
    id: number;
    accountId: number;
    submissionId: number;
    cardNo: string;
    status: string;
    latestRound: boolean;
    effective: boolean;
    current: boolean;
    versionNo?: number;
    submissionNo?: number;
    professionalRisk?: boolean;
    studentDecisionComment?: string;
    submittedAt?: Timestamp;
    version: number;
    lastActivityAt?: Timestamp;
    availableActions: string[];
  }>;
  positioningDrafts: Array<{
    id: number;
    accountId: number;
    cardNo: string;
    status: string;
    versionNo?: number;
    professionalRisk?: boolean;
    version: number;
    lastActivityAt?: Timestamp;
    availableActions: string[];
  }>;
  contents: Array<{
    id: number;
    accountId: number;
    contentNo: string;
    title?: string;
    status: string;
    currentVersionNo?: number;
    publishedAt?: Timestamp;
    version: number;
    lastActivityAt?: Timestamp;
    availableActions: string[];
  }>;
  productionTickets: Array<{
    id: number;
    accountId: number;
    ticketNo: string;
    status: string;
    deadlineAt?: Timestamp;
    revisionCount?: number;
    lastActivityAt?: Timestamp;
  }>;
  operationTimeline: Array<{
    key: string;
    type: string;
    title: string;
    detail?: string;
    operatorName?: string;
    occurredAt: Timestamp;
  }>;
  studentTaskLine: StudentTaskStage[];
  taskLine: StudentTaskStage[];
  pendingStats: {
    accountCount: number;
    positioningCount: number;
    contentCount: number;
    productionCount: number;
  };
};
export type MediaStudentTalkRecord = {
  id: number;
  accountId?: number;
  operatorUserId: number;
  operatorUserName?: string;
  content: string;
  attachmentFileIds: number[];
  occurredAt: Timestamp;
};
export type StudentContactChecklistItem = {
  key: string;
  title: string;
  type: string;
  enabled?: boolean;
  attachmentRequired?: boolean;
  sort?: number;
};
export type StudentContactConfig = {
  published?: {
    id: number;
    versionNo: number;
    version: number;
    firstContactTimeoutMinutes: number;
    studyPlanTimeoutMinutes: number;
    checklist: StudentContactChecklistItem[];
    quickNotes: string[];
    collaboratorTabs: Record<string, string[]>;
  };
  draft?: {
    id: number;
    versionNo: number;
    version: number;
    firstContactTimeoutMinutes: number;
    studyPlanTimeoutMinutes: number;
    checklist: StudentContactChecklistItem[];
    quickNotes: string[];
    collaboratorTabs: Record<string, string[]>;
  };
};
export type StudentContactFormField = {
  key: string;
  title: string;
  type:
    | "text"
    | "textarea"
    | "number"
    | "date"
    | "datetime"
    | "dict"
    | "select"
    | "multi_select"
    | "radio"
    | "checkbox_group"
    | "checkbox"
    | "attachment"
    | "region";
  required: boolean;
  enabled: boolean;
  systemField: boolean;
  sort: number;
  description?: string;
  dictType?: string;
  multiple?: boolean;
  minSelections?: number;
  maxSelections?: number;
  minValue?: number;
  maxValue?: number;
  maxLength?: number;
  group?: string;
};
export type DirectorTemplateSnapshot = {
  templateId: number;
  templateVersionId: number;
  templateVersionNo: number;
  fields: StudentContactFormField[];
  values: Record<string, unknown>;
  dictSnapshots: Record<string, unknown>;
};
export type DirectorTemplateVersion = {
  id: number;
  templateId: number;
  versionNo: number;
  status: "draft" | "published" | "archived";
  fields: StudentContactFormField[];
  publishedByUserId?: number;
  publishedAt?: Timestamp;
  version: number;
};
export type DirectorTemplate = {
  id: number;
  scene: "director_interview" | "positioning_card";
  templateCode: string;
  name: string;
  defaultTemplate: boolean;
  status: string;
  version: number;
  published?: DirectorTemplateVersion;
  draft?: DirectorTemplateVersion;
  versions: DirectorTemplateVersion[];
};
export type DirectorConfig = {
  id: number;
  interviewAppointmentHours: number;
  positioningDueHours: number;
  trialDays: number;
  version: number;
};
export type DirectorStageForm = {
  state: "empty" | "draft" | "submitted";
  configId?: number;
  configVersion?: number;
  templateId?: number;
  templateVersionId?: number;
  templateVersionNo?: number;
  fields: StudentContactFormField[];
  values: Record<string, unknown>;
  dictSnapshots: Record<string, unknown>;
  version: number;
  savedAt?: Timestamp;
  savedByUserId?: number;
  submittedAt?: Timestamp;
  interviewAt?: Timestamp;
};
export type StudentContactAction =
  | "ACCEPT"
  | "FIRST_CONTACT"
  | "STUDY_PLAN"
  | "FOLLOW_UP"
  | "EDIT_BASIC_INFO"
  | "ASSIGN_CONTENT_DIRECTOR"
  | "ASSIGN_CAREER_PLANNER"
  | "UPDATE_EXAM_DATE"
  | "EXAM_NOTICE_DONE"
  | "POST_EXAM_DONE"
  | "COMPLETE_STAGE"
  | "END_SERVICE"
  | "DIRECTOR_PRECHECK"
  | "DIRECTOR_INTERVIEW"
  | "DIRECTOR_OPERATOR_ASSIGN";
export type StudentDeliveryStage = {
  code: string;
  label: string;
  status: string;
  current?: boolean;
  available?: boolean;
};
export type StudentContactContext = {
  serviceRelationId: number;
  acceptanceStatus?: string;
  acceptedAt?: string;
  version: number;
  firstContactChecklist: StudentContactChecklistItem[];
  quickNotes: string[];
  firstContactTimeoutMinutes?: number;
  studyPlanTimeoutMinutes?: number;
  visibleTabs: string[];
  availableActions: StudentContactAction[];
  currentTask?: {
    id: number;
    type: string;
    status: string;
    dueAt?: string;
    overdue?: boolean;
  };
  ownerUserId?: number;
  ownerUserName?: string;
  contentDirectorUserId?: number;
  contentDirectorUserName?: string;
  careerPlannerUserId?: number;
  careerPlannerUserName?: string;
  operatorUserId?: number;
  operatorUserName?: string;
  directorStage?: string;
  directorInterviewAt?: Timestamp;
  defaultDirectorInterviewAt?: Timestamp;
  directorInterviewAppointmentHours?: number;
  directorTrialDays?: number;
  deliveryStage?: string;
  deliveryStageLabel?: string;
  deliveryStages?: StudentDeliveryStage[];
  examDate?: string;
  formFields?: StudentContactFormField[];
  directorForms?: { precheck: DirectorStageForm; interview: DirectorStageForm };
  operatorAssignmentConflict?: boolean;
};
export type StudentContactRecord = {
  id: number;
  contactType: string;
  successful: boolean;
  unsuccessfulReasonValue?: string;
  unsuccessfulReasonLabel?: string;
  remark: string;
  attachmentFileIds: number[];
  completedChecklistKeys: string[];
  nextContactAt: string;
  operatorUserId: number;
  operatorUserName?: string;
  submittedAt: string;
  deliveryStage?: string;
  deliveryData?: string;
};
export type StudentContactExtension = {
  id: number;
  serviceRelationId: number;
  taskId: number;
  status: string;
  originalDueAt: string;
  requestedDueAt: string;
  reasonValue: string;
  reasonLabel?: string;
  description: string;
  attachmentFileIds: number[];
  applicantUserId: number;
  reviewerUserId: number;
  processInstanceId?: string;
  decisionReason?: string;
  submittedAt: string;
  resolvedAt?: string;
  version: number;
};

export type AdvancedFilterCondition = {
  fieldKey: string;
  operator: string;
  value?: unknown;
  valueFrom?: unknown;
  valueTo?: unknown;
};
export type AdvancedFilterGroup = {
  logic: "AND" | "OR";
  conditions: AdvancedFilterCondition[];
  groups: AdvancedFilterGroup[];
};
export type AdvancedFilterScene =
  | "lead"
  | "order"
  | "lead_appeal"
  | "duplicate_review"
  | "registration"
  | "student"
  | "subordinate_sales";
export type AdvancedFilterField = {
  fieldKey: string;
  group: string;
  label: string;
  valueType: "text" | "select" | "number" | "date";
  operators: string[];
  optionSource?: string;
  options: Array<{ value: string | number; label: string }>;
  optionsLoading?: boolean;
  optionsError?: boolean;
  retryOptions?: () => void;
};
export type AdvancedFilterCatalog = {
  fields: AdvancedFilterField[];
  relativeDateOptions: Array<{ value: string; label: string }>;
};
export type AreaNode = {
  id: number;
  name: string;
  selectionCode: string;
  leafSelectable: boolean;
  children?: AreaNode[];
};
export type LeadCategoryNode = {
  id: number;
  name: string;
  children: LeadCategoryNode[];
};
export type LeadCatalogItem = {
  categoryId: number;
  categoryName: string;
  categoryPath: Array<{ id: number; name: string }>;
  level1CategoryId?: number;
  level1CategoryName?: string;
  level2CategoryId?: number;
  level2CategoryName?: string;
  spuRef: string;
  spuName: string;
  attrs: Array<{
    attrKey: string;
    attrName: string;
    required: boolean;
    values: Array<{ value: string; label: string }>;
  }>;
};
export type LeadCatalogSku = {
  spuRef: string;
  skuRef: string;
  skuName: string;
  attrValues: Record<string, string>;
  price: number;
};
export type LeadCatalog = {
  categoryTree: LeadCategoryNode[];
  spus: LeadCatalogItem[];
  skus: LeadCatalogSku[];
};
export type LeadAttachment = {
  infraFileId: number;
  fileUrl: string;
  originalName: string;
  contentType: string;
  fileSize: number;
};
export type LeadCreateRequest = {
  name: string;
  mobile?: string;
  wechatId?: string;
  provinceCode: string;
  cityCode: string;
  intendedProducts: Array<{
    spuRef?: string;
    skuRef?: string;
    spuUnknown: boolean;
    skuUnknown: boolean;
    primary: boolean;
  }>;
  sourceChannel: string;
  leadCategory: string;
  remark?: string;
  attachments: Array<{ infraFileId: number }>;
  dispatchMode: "auto" | "specified";
  specifiedSalesUserId?: number;
  newMediaProviderUserId?: number;
  idempotencyKey: string;
};
export type LeadCreateResult = {
  leadId?: number;
  leadNo?: string;
  reviewId?: number;
  outcome:
    | "created"
    | "activated"
    | "review_pending"
    | "duplicate_rejected"
    | "duplicate_auto_closed";
  assignmentStatus?: string;
  pendingAssigneeUserId?: number;
  existingLeadStatus?: string;
  existingQualificationStatus?: string;
  existingOperationalStatus?: string;
};
export type LeadDuplicateReview = {
  id: number;
  status: "pending" | "completed";
  submitterUserId?: number;
  submissionSnapshot: string;
  matchRules: string;
  candidateSnapshot: string;
  resultType?: string;
  reviewOpinion?: string;
  selectedSalesUserId?: number;
  reviewerUserId?: number;
  reviewedAt?: Timestamp;
  createTime: Timestamp;
};
export type LeadDuplicateReviewDecision = {
  resultType:
    "new_person" | "reuse_person" | "reactivate_lead" | "notify_owner";
  matchedPersonId?: number;
  matchedLeadId?: number;
  selectedSalesUserId?: number;
  opinion: string;
  attachments: Array<{ infraFileId: number }>;
  idempotencyKey: string;
};
export type PendingLead = {
  id: number;
  leadNo: string;
  dispatchMode: "auto" | "specified";
  maskedName: string;
  maskedMobile?: string;
  maskedWechatId?: string;
  provinceName: string;
  cityName: string;
  intendedProducts: string[];
  primaryIntendedProduct?: string;
  sourceChannel: string;
  sourceChannelLabel?: string;
  leadCategory: string;
  leadCategoryLabel?: string;
  remark?: string;
  attachmentUrls: string[];
  submittedAt: Timestamp;
  expiresAt?: Timestamp;
  remainingSeconds?: number;
  rejectable: boolean;
  deferrable: boolean;
  assignmentHistoryId?: number;
};
export type SalesDispatchStatus = {
  eligible: boolean;
  presence: "online" | "offline";
  mode: "accepting" | "paused";
  effectiveStatus: "online" | "busy" | "offline";
};
export type ManagedLeadProduct = {
  id: number;
  spuRef?: string;
  spuName?: string;
  skuRef?: string;
  skuName?: string;
  selectedAttrValues?: string;
  price?: number;
  categoryName?: string;
  primary: boolean;
};
export type ManagedLeadAttachment = {
  id: number;
  fileUrl: string;
  originalName: string;
  contentType: string;
  fileSize: number;
};
export type ManagedLead = {
  id: number;
  leadNo: string;
  personId: number;
  submittedName: string;
  submittedMobile?: string;
  submittedWechatId?: string;
  sourceType: string;
  sourceLabel?: string;
  sourceUserId?: number;
  sourceUserName?: string;
  sourceChannel?: string;
  partnerOwnerNameSnapshot?: string;
  providerOwnerType?: "system_user" | "partner";
  providerOwnerId?: number;
  providerOwnerNameSnapshot?: string;
  contributionUserIdSnapshot?: number;
  contributionUserNameSnapshot?: string;
  contributionSupervisorUserIdSnapshot?: number;
  contributionSupervisorNameSnapshot?: string;
  contributionDeptIdSnapshot?: number;
  contributionDeptNameSnapshot?: string;
  countedAt?: Timestamp;
  provinceCode?: string;
  provinceName?: string;
  cityCode?: string;
  cityName?: string;
  leadCategory?: string;
  leadCategoryLabelSnapshot?: string;
  remark?: string;
  status: string;
  assignmentStatus: string;
  handlingStage: string;
  qualificationStatus: "pending" | "valid" | "invalid";
  followUpStatus?:
    "first_follow_pending" | "following" | "deal_pending_approval" | "won";
  operationalStatus: "active" | "suspended";
  dispatchMode?: string;
  ownerUserId?: number;
  ownerUserName?: string;
  pendingAssigneeUserId?: number;
  pendingAssigneeUserName?: string;
  pendingExpiresAt?: Timestamp;
  assignmentAttemptCount?: number;
  publicPoolAt?: Timestamp;
  submittedAt: Timestamp;
  nextFollowUpAt?: Timestamp;
  currentAssignmentFirstFollowUpAt?: Timestamp;
  currentAssignmentFirstFollowUpDeadlineAt?: Timestamp;
  qualificationStartedAt?: Timestamp;
  qualificationDeadlineAt?: Timestamp;
  suspendedAt?: Timestamp;
  qualifiedByUserId?: number;
  qualifiedByUserName?: string;
  qualifiedAt?: Timestamp;
  validDescription?: string;
  convertedAt?: Timestamp;
  salesOrderSubmittedAt?: Timestamp;
  invalidReason?: string;
  invalidReasonLabelSnapshot?: string;
  invalidDescription?: string;
  invalidEvidence?: LeadAppealEvidence[];
  recycleSourceOwnerUserId?: number;
  recycleSourceOwnerUserName?: string;
  appealDeadlineAt?: Timestamp;
  closedAt?: Timestamp;
  closeReason?: string;
  createTime: Timestamp;
  updateTime: Timestamp;
  lastActivityAt?: Timestamp;
  relationTypes: Array<"submitter" | "owner" | "student_service_owner">;
  overviewVisible?: boolean;
  visibleTabs?: LeadDetailTab[];
  identityMaskMode?: "counterparty_masked" | "full";
  primaryProduct?: ManagedLeadProduct;
  intendedProducts?: ManagedLeadProduct[];
  attachments?: ManagedLeadAttachment[];
  opportunity?: {
    id: number;
    status: string;
    nextFollowUpAt?: Timestamp;
    wonAt?: Timestamp;
  };
  activeSalesOrderId?: number;
  activeSalesOrderStatus?: "pending_approval" | "revision_required";
  availableActions?: Array<{
    code:
      | "EDIT_BASIC_INFO"
      | "ADD_FOLLOW_UP"
      | "JUDGE_VALID"
      | "JUDGE_INVALID"
      | "ENTER_DEAL"
      | "ENTER_REPURCHASE"
      | "REVISE_DEAL"
      | "SUBMITTER_SUPPLEMENT"
      | "SUBMITTER_URGE"
      | "SUBMITTER_COMPLAINT"
      | "QUALIFICATION_RESTORE"
      | "QUALIFICATION_TRANSFER"
      | "QUALIFICATION_RECYCLE"
      | "QUALIFICATION_RELEASE"
      | "SUPERVISOR_RESTORE"
      | "SUPERVISOR_TRANSFER"
      | "SUPERVISOR_RECYCLE"
      | "SUPERVISOR_RELEASE_CLAIM_POOL"
      | "SUPERVISOR_RELEASE_PUBLIC_SEA";
    enabled: boolean;
  }>;
};
export type LeadComplaintEvidence = {
  infraFileId: number;
  fileUrl: string;
  originalName?: string;
  contentType?: string;
  fileSize?: number;
};
export type LeadComplaint = {
  id: number;
  leadId: number;
  leadNo: string;
  complainantUserId: number;
  salesUserId: number;
  reason: string;
  complainantUserName?: string;
  salesUserName?: string;
  evidence?: LeadComplaintEvidence[];
  evidenceRefs?: string;
  status: "pending" | "handled";
  result?: "founded" | "unfounded";
  handlerUserId?: number;
  handlerUserName?: string;
  handlerOpinion?: string;
  handlerEvidenceRefs?: string;
  handlerEvidence?: LeadComplaintEvidence[];
  handledAt?: Timestamp;
  createTime: Timestamp;
};
export type LeadQualificationException = {
  id: number;
  leadNo: string;
  submittedName: string;
  submittedMobile?: string;
  status: string;
  assignmentStatus: string;
  handlingStage: string;
  ownerUserId?: number;
  ownerUserName?: string;
  recycleSourceOwnerUserId?: number;
  recycleSourceOwnerUserName?: string;
  qualificationDeadlineAt?: Timestamp;
  suspendedAt?: Timestamp;
};
export type LeadInboxFilterOption = { key: string; label: string };
export type LeadInboxFilterSection = {
  key: string;
  label: string;
  options: LeadInboxFilterOption[];
};
export type LeadInboxFilterGroup = {
  key: string;
  label: string;
  sections: LeadInboxFilterSection[];
};
export type LeadInboxFilterProfile = { groups: LeadInboxFilterGroup[] };
export type LeadSimpleStatus =
  | "first_follow_pending"
  | "qualification_pending"
  | "following"
  | "deal_pending_approval"
  | "won"
  | "invalid"
  | "closed"
  | "suspended";
export type ManagedLeadPageParams = {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  status?: string;
  inboxGroup?: string;
  inboxStage?: string;
  relationScope?: "all" | "submitted" | "owned";
  simpleStatus?: LeadSimpleStatus;
  advancedFilter?: AdvancedFilterGroup;
};
export type LeadDetailTab =
  | "overview"
  | "follow-ups"
  | "orders"
  | "appeals"
  | "complaints"
  | "flow-history";
export type LeadFlowAttachment = {
  infraFileId?: number;
  originalName?: string;
  contentType?: string;
  previewUrl?: string;
  previewable: boolean;
  available: boolean;
};
export type LeadFlowHistory = {
  id: string;
  occurredAt: Timestamp;
  businessObject: string;
  flowNode: string;
  source: string;
  operator?: string;
  fromOwner?: string;
  toOwner?: string;
  leadStatusBefore?: string;
  leadStatusAfter?: string;
  assignmentStatusBefore?: string;
  assignmentStatusAfter?: string;
  reason?: string;
  remark?: string;
  attachments: LeadFlowAttachment[];
};
export type LeadFollowUpImage = {
  infraFileId: number;
  originalName: string;
  contentType: string;
  fileSize: number;
  sort: number;
  url?: string;
};
export type LeadFollowUp = {
  id: number;
  leadId: number;
  assignmentHistoryId?: number;
  opportunityId?: number;
  recordScope: "lead" | "opportunity";
  operatorUserId: number;
  operatorName?: string;
  occurredAt: Timestamp;
  firstInAssignment: boolean;
  method: string;
  methodLabel: string;
  result: string;
  resultLabel: string;
  categoryBefore?: string;
  categoryBeforeLabel?: string;
  categoryAfter?: string;
  categoryAfterLabel?: string;
  remark?: string;
  nextFollowUpAt?: Timestamp;
  images: LeadFollowUpImage[];
};
export type LeadFollowUpCreateRequest = {
  method: string;
  result: string;
  leadCategory?: string;
  remark?: string;
  nextFollowUpAt?: Timestamp;
  images: Array<{ infraFileId: number }>;
  idempotencyKey: string;
};
export type LeadBasicInfoUpdateRequest = {
  name: string;
  mobile?: string;
  wechatId?: string;
  provinceCode: string;
  cityCode: string;
  leadCategory?: string;
  intendedProducts: LeadCreateRequest["intendedProducts"];
  reason: string;
};
export type LeadAppealEvidence = {
  infraFileId: number;
  fileUrl?: string;
  originalName: string;
  contentType: string;
  fileSize: number;
  sort?: number;
};
export type LeadAppeal = {
  id: number;
  leadId: number;
  leadNo: string;
  leadName: string;
  roundNo: number;
  reviewStage: "sales_manager" | "quality" | "chairman";
  status:
    | "sales_manager_reviewing"
    | "quality_reviewing"
    | "chairman_reviewing"
    | "overturned"
    | "upheld"
    | "withdrawn";
  applicantUserId: number;
  applicantUserName?: string;
  reason: string;
  evidence: LeadAppealEvidence[];
  invalidReasonSnapshot?: string;
  invalidDescriptionSnapshot?: string;
  invalidEvidenceSnapshot: LeadAppealEvidence[];
  processInstanceId?: string;
  taskId?: string;
  reviewerUserId?: number;
  reviewerUserName?: string;
  decisionReason?: string;
  decisionEvidence: LeadAppealEvidence[];
  submittedAt: Timestamp;
  decidedAt?: Timestamp;
  canSubmitNextRound: boolean;
};
export type SalesOrderVoucher = LeadAttachment;
export type SalesOrderSubmitRequest = {
  purchaseIntentId?: number;
  buyerName?: string;
  studentName: string;
  studentNature: string;
  studentMobile?: string;
  studentWechatId?: string;
  provinceCode: string;
  provinceName: string;
  cityCode: string;
  cityName: string;
  agreedExamTime?: string;
  classType?: string;
  servicePeriod: string;
  studentSource: string;
  customerPaidAt: Timestamp;
  feeMode: string;
  paymentMethod: string;
  remark?: string;
  studentSpecialRequirements?: string;
  materialDeliveryContact?: string;
  items: Array<{ spuRef: string; skuRef: string; actualAmount: number }>;
  paymentVouchers: Array<{ infraFileId: number }>;
  idempotencyKey: string;
};
export type CollectionMode = "online_link" | "offline_paid";
export type PurchaseIntentDraftRequest = {
  id?: number;
  version?: number;
  collectionMode: CollectionMode;
  purchaseType:
    | "lead_first_purchase"
    | "lead_repurchase"
    | "student_repurchase"
    | "external_repurchase";
  leadId?: number;
  personId?: number;
  sourceKey: string;
  draft: Record<string, unknown>;
  items: Array<{
    spuRef: string;
    skuRef: string;
    skuName?: string;
    actualAmount: number;
  }>;
  totalAmount: number;
  idempotencyKey: string;
};
export type PurchaseIntent = {
  id: number;
  purchaseIntentNo: string;
  collectionMode: CollectionMode;
  purchaseType: string;
  leadId?: number;
  personId: number;
  draft: Record<string, unknown>;
  itemSnapshotJson: string;
  totalAmount: number;
  currency: string;
  version: number;
  paymentLocked: boolean;
  paymentIntentId?: number;
  paymentIntentNo?: string;
  paymentUrl?: string;
  paymentStatus?: "created" | "waiting" | "paid" | "expired" | "closed";
  paymentExpiresAt?: Timestamp;
  displayStatus:
    | "order_draft"
    | "pending_payment"
    | "paid_pending_submission"
    | "invalid";
};

export type PaymentRefund = {
  id: number;
  refundNo: string;
  paymentTransactionId: number;
  orderId?: number;
  refundAmount: number;
  currency: string;
  reason: string;
  approvalMode: string;
  status: string;
  refundReqsn?: string;
  originalReqsn?: string;
  originalTrxId?: string;
  acceptedAt?: Timestamp;
  refundedAt?: Timestamp;
  lastQueriedAt?: Timestamp;
  lastErrorMessage?: string;
};
export type SalesOrder = {
  id: number;
  orderNo: string;
  leadId?: number;
  opportunityId?: number;
  personId: number;
  orderType: "first_purchase" | "repurchase";
  supersedesOrderId?: number;
  supersededByOrderId?: number;
  status:
    | "pending_approval"
    | "revision_required"
    | "effective"
    | "superseded"
    | "terminated";
  submitterUserId: number;
  formalSalesUserId?: number;
  buyerName: string;
  studentName: string;
  studentNature: string;
  studentMobile?: string;
  studentWechatId?: string;
  provinceCode: string;
  provinceName: string;
  cityCode: string;
  cityName: string;
  agreedExamTime?: string;
  classType?: string;
  servicePeriod: string;
  servicePeriodLabelSnapshot?: string;
  studentSource: string;
  studentSourceLabelSnapshot?: string;
  studentNatureLabelSnapshot?: string;
  totalAmount: number;
  customerPaidAt: Timestamp;
  feeMode: string;
  feeModeLabelSnapshot?: string;
  paymentMethod: string;
  paymentMethodLabelSnapshot?: string;
  remark?: string;
  studentSpecialRequirements?: string;
  materialDeliveryContact?: string;
  items: Array<{
    id: number;
    productRef: string;
    skuRef: string;
    productName: string;
    skuName: string;
    categoryPath: string[];
    attrValues: Record<string, string>;
    actualAmount: number;
  }>;
  paymentVouchers: SalesOrderVoucher[];
  approvalRoundNo: number;
  approvalRoundStatus: string;
  processInstanceId?: string;
  taskId?: string;
  taskDefinitionKey?: "registrationReview" | "financeReview";
  taskStatus?: number;
  taskReason?: string;
  taskCreateTime?: Timestamp;
  taskEndTime?: Timestamp;
  decisionReason?: string;
  canRevise?: boolean;
  canTerminate?: boolean;
  version: number;
  currentApprovalRoundId: number;
  approvalRoundVersion: number;
  repurchaseReason?: string;
  terminationReason?: string;
  canRequestSupervisorConfirmation?: boolean;
  submittedAt: Timestamp;
  effectiveAt?: Timestamp;
  leadProfile?: {
    leadNo: string;
    submittedName: string;
    submittedMobile?: string;
    submittedWechatId?: string;
    sourceType?: string;
    sourceLabel?: string;
    sourceUserName?: string;
    sourceChannel?: string;
    sourceChannelLabelSnapshot?: string;
    provinceName?: string;
    cityName?: string;
    leadCategory?: string;
    leadCategoryLabelSnapshot?: string;
    dispatchMode?: string;
    ownerUserName?: string;
  };
  registrationApproval?: SalesOrderApprovalStatus;
  financeApproval?: SalesOrderApprovalStatus;
  registrationSupervisorConfirmation?: SalesOrderSupervisorConfirmation;
  financeSupervisorConfirmation?: SalesOrderSupervisorConfirmation;
  supervisorApproval?: SalesOrderSupervisorApproval;
};
export type SalesOrderSupervisorConfirmation = {
  id: number;
  status: "pending" | "confirmed" | "rejected" | "cancelled";
  requesterUserId: number;
  requesterUserName?: string;
  requestReason: string;
  decisionReason?: string;
  requestedAt?: Timestamp;
  decidedAt?: Timestamp;
};
export type SalesOrderApprovalStatus = {
  status: "pending" | "approved" | "rejected" | "cancelled";
  reviewerUserId?: number;
  reviewerUserName?: string;
  createTime?: Timestamp;
  endTime?: Timestamp;
};
export type SalesOrderListItem = Pick<
  SalesOrder,
  | "id"
  | "orderNo"
  | "leadId"
  | "status"
  | "studentName"
  | "studentMobile"
  | "totalAmount"
  | "approvalRoundNo"
  | "submittedAt"
  | "effectiveAt"
> & {
  personId?: number;
  orderType?: SalesOrder["orderType"];
  taskId?: string;
  taskDefinitionKey?: "registrationReview" | "financeReview";
  taskStatus?: number;
  taskReason?: string;
  taskCreateTime?: Timestamp;
  taskEndTime?: Timestamp;
  supervisorConfirmationId?: number;
  supervisorConfirmationStatus?: string;
  supervisorRequesterName?: string;
};
export type SalesOrderSupervisorInboxItem = {
  id: number;
  orderId: number;
  orderNo: string;
  studentName: string;
  approvalRoundId: number;
  taskDefinitionKey: "registrationReview" | "financeReview";
  taskId: string;
  requesterUserId: number;
  requesterUserName?: string;
  supervisorUserId: number;
  requestReason: string;
  decisionReason?: string;
  status: "pending" | "confirmed" | "rejected" | "cancelled";
  requestedAt?: Timestamp;
  decidedAt?: Timestamp;
  version: number;
  orderVersion: number;
  roundVersion: number;
};
export type SalesOrderStatusCounts = {
  total: number;
  pendingApproval: number;
  revisionRequired: number;
  effective: number;
  superseded: number;
};
export type SalesOrderApprovalFilterOption = {
  key: string;
  label: string;
  count: number;
};
export type SalesOrderApprovalFilterSection = {
  key: string;
  label: string;
  options: SalesOrderApprovalFilterOption[];
};
export type SalesOrderApprovalFilterGroup = {
  key: string;
  label: string;
  count: number;
  sections: SalesOrderApprovalFilterSection[];
};
export type SalesOrderApprovalCenter = {
  key: "registration" | "finance";
  label: string;
};
export type SalesOrderApprovalFilterProfile = {
  groups: SalesOrderApprovalFilterGroup[];
  centers: SalesOrderApprovalCenter[];
};
export type SalesOrderApprovalTaskTarget = {
  workType: "approval" | "supervisor";
  orderId: number;
  taskId: string;
  taskDefinitionKey: "registrationReview" | "financeReview";
  center: "registration" | "finance";
  confirmationId?: number;
  status: string;
};
export type ForcedFormField = {
  key: string
  type: 'text' | 'textarea' | 'radio' | 'checkbox' | 'multi-select' | 'attachment'
  label: string
  required?: boolean
  dictType?: string
  maxLength?: number
  maxCount?: number
  maxSizeMb?: number
  allowedExtensions?: string[]
  options?: Array<{ label: string; value: string }>
}
export type ForcedForm = {
  id: number
  formId?: number
  batchId?: number
  versionId?: number
  recipientId?: number
  name: string
  description?: string
  fieldsJson?: string
  status: string
  version?: number
  sentAt?: Timestamp
  recipientCount?: number
  completedCount?: number
  pendingCount?: number
  lastSentAt?: Timestamp
  currentVersionId?: number
}
export type ForcedFormRuntime = {
  formId: number
  versionId: number
  version: number
  name: string
  description?: string
  recipientId: number
  batchId: number
  fields: ForcedFormField[]
}
export type ForcedFormAttachmentUploadResult = {
  formId: number
  versionId: number
  fieldKey: string
  infraFileId: number
  uploadToken: string
  fileName: string
  fileSize: number
  contentType?: string
}
export type ForcedFormStatus = {
  pendingCount: number
  firstPendingFormId?: number
  firstPendingFormName?: string
}
export type BpmBusinessTaskTarget =
  | {
      supported: true;
      route: string;
      query: Record<string, string | number | boolean>;
      bizType: "sales_order" | "lead_appeal";
      message?: string;
    }
  | {
      supported: false;
      route?: string;
      query: Record<string, string | number | boolean>;
      bizType: "unsupported";
      message: string;
    };
export type BusinessTaskBucket = "unscheduled" | "overdue" | "today" | "future";
export type BusinessTaskSummary = Record<BusinessTaskBucket, number>;
export type BusinessTask = {
  id: number;
  taskType: string;
  bizType: string;
  bizId: number;
  title: string;
  summary?: string;
  status: "pending" | "completed" | "cancelled";
  dueAt?: Timestamp;
  remindAt?: Timestamp;
  completedAt?: Timestamp;
  cancelledAt?: Timestamp;
  createTime: Timestamp;
  overdue: boolean;
  actionCode?:
    | "OPEN_LEAD_ASSIGNMENT"
    | "OPEN_LEAD_FOLLOW_UP"
    | "OPEN_LEAD_SUBMITTER_SUPPLEMENT"
    | "OPEN_WORK_TASK"
    | "CONFIRM_WORK_TASK"
    | "SUMMARIZE_WORK_PLAN"
    | "OPEN_SALES_ORDER_REVISION"
    | "COMPLETE_BIRTHDAY_CARE"
    | "OPEN_STUDENT_FIRST_CONTACT"
    | "OPEN_STUDENT_STUDY_PLAN"
    | "OPEN_STUDENT_CONTACT"
    | "OPEN_STUDENT_CONTACT_ASSISTANCE";
  serviceRelationId?: number;
  targetTab?: string;
  targetRecordId?: number;
  actionable: boolean;
};
export type BpmTask = {
  id: string;
  name: string;
  createTime: Timestamp;
  endTime?: Timestamp;
  durationInMillis?: number;
  status: number;
  reason?: string;
  assigneeUser?: { id: number; nickname: string };
  ownerUser?: { id: number; nickname: string };
  processInstanceId: string;
  processDefinitionKey?: string;
  taskDefinitionKey?: string;
  parentTaskId?: string;
  formName?: string;
  reasonRequire?: boolean;
  processInstance?: {
    id: string;
    name: string;
    createTime: Timestamp;
    processDefinitionId?: string;
    summary?: Array<{ key: string; value: string }>;
    startUser?: { id: number; nickname: string };
  };
};
export type SalesOrderSupervisorApproval = SalesOrderSupervisorConfirmation & {
  taskDefinitionKey: "registrationReview" | "financeReview";
  center: "registration" | "finance";
  supervisorUserId: number;
  supervisorUserName?: string;
};
export type ExportTask = {
  id: number;
  taskNo: string;
  exportType: "lead" | "order" | "finance_order" | "cashback" | "withdrawal";
  status:
    | "queued"
    | "prechecking"
    | "generating"
    | "ready"
    | "failed"
    | "cancelled"
    | "expired";
  attemptCount: number;
  resultFileName?: string;
  resultFileSize?: number;
  readyAt?: Timestamp;
  expiresAt?: Timestamp;
  failureCode?: string;
  failureMessage?: string;
  createTime: Timestamp;
};
export type SimpleUser = {
  id: number;
  nickname: string;
  username?: string;
  status?: number;
  avatar?: string;
  deptId?: number;
  deptName?: string;
};
export type SimpleDept = { id: number; name: string; parentId?: number };
export type WorkPlanAttachmentUpload = {
  infraFileId: number;
  originalName: string;
  contentType?: string;
  fileSize?: number;
};
export type WorkPlanChange = {
  id: number;
  subjectType: string;
  subjectId: number;
  changeType: string;
  beforeSnapshot?: string;
  afterSnapshot?: string;
  reason: string;
  operatorUserId: number;
  changedAt: Timestamp;
};
export type WorkReport = {
  id: number;
  revisionNo: number;
  completionSummary: string;
  submitterUserId: number;
  submittedAt: Timestamp;
  confirmationDecision?: "auto_confirmed" | "confirmed" | "returned";
  confirmationComment?: string;
  confirmedByUserId?: number;
  confirmedAt?: Timestamp;
  infraFileIds: number[];
  reportFields?: Record<string, unknown>;
};
export type WorkTask = {
  id: number;
  planId?: number;
  parentTaskId?: number;
  title: string;
  description?: string;
  deliverableRequirement?: string;
  assigneeUserId: number;
  assigneeDeptId?: number;
  assignerUserId: number;
  dueAt?: Timestamp;
  remindAt?: Timestamp;
  confirmationRequired: boolean;
  confirmerUserId?: number;
  status:
    "draft" | "pending" | "awaiting_confirmation" | "completed" | "cancelled";
  reportedAt?: Timestamp;
  completedAt?: Timestamp;
  cancelledAt?: Timestamp;
  cancelReason?: string;
  version: number;
  blockedByChildren: boolean;
  completedChildCount: number;
  totalChildCount: number;
  taskFields?: Record<string, unknown>;
  reports: WorkReport[];
  availableActions: Array<
    "assign" | "complete" | "review" | "cancel" | "decompose"
  >;
};
export type WorkPlan = {
  id: number;
  title: string;
  periodType: "day" | "month" | "week" | "quarter" | "year" | "custom";
  startDate: string;
  endDate: string;
  planTypeId: number;
  templateId: number;
  templateVersionId: number;
  ownerUserId: number;
  ownerDeptId?: number;
  objective?: string;
  keyRequirements?: string;
  status: "draft" | "active" | "completed" | "cancelled";
  summaryReady: boolean;
  creatorUserId: number;
  publishedAt?: Timestamp;
  completedAt?: Timestamp;
  cancelledAt?: Timestamp;
  cancelReason?: string;
  version: number;
  availableActions: Array<"update" | "publish" | "assign" | "close" | "cancel">;
  fieldDefinitions: WorkPlanTemplateField[];
  planFields?: Record<string, unknown>;
  tasks: WorkTask[];
  summary?: WorkPlanSummary;
  changes: WorkPlanChange[];
};
export type WorkTaskInput = {
  id?: number;
  parentTaskId?: number;
  title: string;
  description?: string;
  deliverableRequirement?: string;
  assigneeUserId: number;
  dueAt?: string;
  remindAt?: string;
  confirmationRequired: boolean;
  confirmerUserId?: number;
  taskFields?: Record<string, unknown>;
  version?: number;
  reason?: string;
};
export type WorkPlanInput = {
  title: string;
  periodType: WorkPlan["periodType"];
  startDate: string;
  endDate: string;
  templateVersionId: number;
  ownerUserId: number;
  objective?: string;
  keyRequirements?: string;
  planFields?: Record<string, unknown>;
  supplementalFields?: WorkPlanTemplateField[];
  version?: number;
  reason?: string;
  tasks?: WorkTaskInput[];
};
export type WorkPlanType = {
  id: number;
  code: string;
  name: string;
  description?: string;
  status: number;
  sort: number;
};
export type WorkPlanTemplateField = {
  id?: number;
  fieldKey?: string;
  label: string;
  section: "plan" | "task" | "report" | "summary";
  fieldType: string;
  required?: boolean;
  unit?: string;
  placeholder?: string;
  filterable?: boolean;
  exportable?: boolean;
  optionsJson?: string;
  defaultValueJson?: string;
  sort?: number;
};
export type WorkPlanTemplateTask = {
  title: string;
  description?: string;
  deliverableRequirement?: string;
  dueOffsetDays?: number;
  dueOffsetBasis?: string;
  confirmationRequired?: boolean;
  sort?: number;
};
export type WorkPlanTemplate = {
  id: number;
  typeId: number;
  code: string;
  name: string;
  description?: string;
  status: string;
  currentVersionNo: number;
  versionId?: number;
  versionStatus?: string;
  periodMode?: WorkPlan["periodType"];
  fields?: WorkPlanTemplateField[];
  applicableDeptIds?: number[];
  includeChildDepartments?: boolean;
  presetItems?: WorkPlanTemplateTask[];
};
export type LeadAssignmentRule = {
  id: number;
  code: string;
  name: string;
  strategyType: "global_round_robin";
  acceptTimeoutSeconds: number;
  maxAttempts: number;
  dailyClaimLimit: number;
  status: number;
};
export type LeadFollowUpRule = {
  id: number;
  code: string;
  name: string;
  firstFollowUpTimeoutMinutes: number;
  qualificationTimeoutMinutes: number;
  agingPoolTimeoutDays: number;
  noProgressWarningDays: number;
  noProgressGraceDays: number;
  notificationPopupDurationMinutes: number;
  duplicateAutoResolutionEnabled: boolean;
  status: number;
  version: number;
};
export type LeadFilterAudience = "submitter" | "owner" | "reviewer";
export type LeadFilterCondition = { field: string; values: string[] };
export type LeadFilterOptionConfig = {
  key: string;
  label: string;
  sort: number;
  enabled: boolean;
  conditions: LeadFilterCondition[];
};
export type LeadFilterGroupConfig = {
  key: string;
  label: string;
  sort: number;
  enabled: boolean;
  sectionLabel?: string;
  conditions: LeadFilterCondition[];
  options: LeadFilterOptionConfig[];
};
export type LeadFilterAdmin = {
  audience: LeadFilterAudience;
  audienceLabel: string;
  draftGroups: LeadFilterGroupConfig[];
  publishedGroups: LeadFilterGroupConfig[];
  publishedVersion: number;
  publishedAt?: Timestamp;
  updateTime?: Timestamp;
};
export type LeadFilterVersion = {
  versionNo: number;
  publishedBy: number;
  publishedAt: Timestamp;
};
export type ProductCategory = {
  id: number;
  parentId: number;
  level: number;
  name: string;
  status: number;
  sort: number;
  children?: ProductCategory[];
};
export type ProductCategorySaveRequest = {
  id?: number;
  parentId?: number;
  name: string;
  status: number;
  sort: number;
  remark?: string;
};
export type ProductConfig = {
  id: number;
  productRef: string;
  name: string;
  subtitle?: string;
  categoryId: number;
  categoryName?: string;
  status: number;
  sort: number;
  updateTime?: Timestamp;
};
export type ProductSaveRequest = {
  id?: number;
  categoryId: number;
  name: string;
  subtitle?: string;
  description?: string;
  targetAudience?: string;
  studyDuration?: string;
  studyMode?: string;
  coverImage?: string;
  status: number;
  sort: number;
  remark?: string;
};
export type ProductSku = {
  id: number;
  spuId: number;
  skuRef: string;
  skuName: string;
  attrValues: Record<string, string>;
  price: number;
  status: number;
  sort: number;
  remark?: string;
  updateTime?: Timestamp;
};
export type ProductSkuSaveRequest = {
  id?: number;
  spuId: number;
  skuName: string;
  attrValues: Record<string, string>;
  price: number;
  status: number;
  sort: number;
  remark?: string;
};
export type ProductAttribute = {
  attrKey?: string;
  attrName: string;
  required: boolean;
  sort: number;
  values: Array<{ value: string; label: string; sort: number }>;
};
export type WorkPlanTemplateSaveRequest = {
  typeId: number;
  code?: string;
  name: string;
  description?: string;
  periodMode: NonNullable<WorkPlanTemplate["periodMode"]>;
  fields: WorkPlanTemplateField[];
  applicableDeptIds: number[];
  includeChildDepartments: boolean;
  presetItems: WorkPlanTemplateTask[];
};
export type WorkPlanSummary = {
  id: number;
  summary: string;
  submitterUserId: number;
  submittedAt: Timestamp;
  infraFileIds: number[];
  summaryFields?: Record<string, unknown>;
};
export type SubordinateCategoryCount = {
  value: string;
  label: string;
  count: number;
  configured: boolean;
};
export type SubordinateSales = {
  userId: number;
  name: string;
  avatar?: string;
  username: string;
  mobile?: string;
  accountStatus: number;
  presence: "online" | "offline";
  accepting: boolean;
  eligible: boolean;
  canReceiveNewLeads: boolean;
  newcomerPoolStatus: "not_available";
  todayPendingCount: number;
  todayFollowUpStatus: "completed" | "incomplete";
  firstFollowTimeoutCount: number;
  suspendedLeadCount: number;
  categoryCounts: SubordinateCategoryCount[];
  validLeadCount: number;
  convertedLeadCount: number;
  effectiveOrderCount: number;
  effectiveOrderAmount: number;
};
export type LeadAgingPoolStatus =
  "waiting_assignment" | "assigned" | "deal_pending";
export type LeadAgingPoolItem = {
  cycleId: number;
  leadId: number;
  leadNo: string;
  cycleNo: number;
  status: LeadAgingPoolStatus;
  originalOwnerUserId: number;
  originalOwnerUserName?: string;
  collaboratorUserId?: number;
  collaboratorUserName?: string;
  frozenDeptId: number;
  frozenDeptName?: string;
  submittedName: string;
  submittedMobile?: string;
  submittedWechatId?: string;
  leadCategory?: string;
  sourceChannel?: string;
  ownershipStartedAt: Timestamp;
  dueAt: Timestamp;
  enteredAt: Timestamp;
  assignedAt?: Timestamp;
  lastFollowUpAt?: Timestamp;
  nextFollowUpAt?: Timestamp;
  activeSalesOrderId?: number;
  activeSalesOrderStatus?: "pending_approval" | "revision_required";
  availableActions: Array<
    | "ASSIGN"
    | "EXIT"
    | "REQUEST_TRANSFER"
    | "ADD_FOLLOW_UP"
    | "ENTER_DEAL"
    | "REVISE_DEAL"
  >;
};
export type SubordinateTask = {
  id: number;
  taskType: string;
  leadId: number;
  leadNo: string;
  leadName?: string;
  dueAt?: Timestamp;
  overdue: boolean;
};
export type SubordinateBatchItem = {
  leadId: number;
  leadNo?: string;
  success: boolean;
  code: string;
  message: string;
};
export type SubordinateBatchResult = {
  successCount: number;
  failureCount: number;
  items: SubordinateBatchItem[];
};
export type SubordinatePauseAllResult = {
  totalCount: number;
  changedCount: number;
  alreadyPausedCount: number;
};
export type NotifyMessage = {
  id: number;
  templateNickname: string;
  templateTitle?: string;
  templateSummary?: string;
  templateContent: string;
  templateType: number;
  readStatus: boolean;
  readTime?: Timestamp;
  createTime: Timestamp;
  notifyRuleId?: number;
  sceneCode?: string;
  actionType?: "none" | "message_detail" | "business_detail";
  bizType?: string;
  bizId?: number;
  sourceEventKey?: string;
};

export type AnnouncementAttachment = {
  infraFileId: number;
  fileName: string;
  mimeType?: string;
  fileSize: number;
  sort: number;
  downloadUrl?: string;
};
export type Announcement = {
  id: number;
  title: string;
  type: number;
  content?: string;
  publishTime: Timestamp;
  highlightUntil?: Timestamp;
  highlighted?: boolean;
  read: boolean;
  readTime?: Timestamp;
  attachments: AnnouncementAttachment[];
};
export type AnnouncementUnreadSummary = {
  unreadCount: number;
  latest?: Announcement;
};

export type NotifyMessagePageParams = {
  pageNo: number;
  pageSize: number;
  readStatus?: boolean;
};

export const http = axios.create({
  baseURL: APP_CONFIG.API_BASE_URL,
  timeout: 30000,
});
type AuthAxiosRequestConfig = AxiosRequestConfig & {
  _retry?: boolean;
  _zsjosAuthPlatform?: AuthPlatform;
  _zsjosImpersonationSessionId?: number;
};
type RefreshResult =
  | { status: "refreshed"; accessToken: string }
  | { status: "failed"; expectedRefreshToken: string | null }
  | { status: "stale" };
const refreshing: Partial<Record<AuthPlatform, Promise<RefreshResult>>> = {};

export class AuthenticationError extends Error {
  readonly code = 401;
  constructor(message = "账号未登录") {
    super(message);
    this.name = "AuthenticationError";
  }
}

export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export const FORCED_FORM_REQUIRED_CODE = 1_900_004_006;

export const clearAuthStorage = (
  platform: AuthPlatform,
) => {
  delete refreshing[platform];
  clearPlatformAuthStorage(platform);
};

export const resolveRequestAuthPlatform = (
  requestPlatform: AuthPlatform | undefined,
  currentPlatform: AuthPlatform,
) => requestPlatform ?? currentPlatform;

export const applyAuthorizationHeader = (
  headers: { delete: (name: string) => void; Authorization?: AxiosHeaderValue },
  accessToken: string | null,
) => {
  headers.delete("Authorization");
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
};
export type SubordinatePartner = {
  id: number;
  partnerNo: string;
  name: string;
  mobile?: string;
  status: "enabled" | "disabled" | "converted";
  assignedEmployeeName?: string;
  assignedAt?: Timestamp;
  assignmentEffective: boolean;
};

type SharedTenantCacheItem = { c: number; e: number; v: string };

const positiveTenantId = (value: unknown) => {
  const tenantId = Number(value);
  return Number.isInteger(tenantId) && tenantId > 0
    ? String(tenantId)
    : undefined;
};

export const readSharedTenantId = (
  storage: Pick<Storage, "getItem"> = localStorage,
) => {
  const raw = storage.getItem(STORAGE_KEYS.TENANT_ID);
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (typeof parsed === "object" && parsed !== null && "v" in parsed) {
      const cacheItem = parsed as SharedTenantCacheItem;
      if (Number.isFinite(cacheItem.e) && cacheItem.e <= Date.now())
        return undefined;
      return positiveTenantId(JSON.parse(cacheItem.v));
    }
    return positiveTenantId(parsed);
  } catch {
    return positiveTenantId(raw);
  }
};

export const writeSharedTenantId = (
  tenantId: string,
  storage: Pick<Storage, "setItem"> = localStorage,
) => {
  const normalized = positiveTenantId(tenantId);
  if (!normalized) throw new AuthenticationError("租户信息无效，请重新登录");
  const cacheItem: SharedTenantCacheItem = {
    c: Date.now(),
    e: 253402300799999,
    v: JSON.stringify(Number(normalized)),
  };
  storage.setItem(STORAGE_KEYS.TENANT_ID, JSON.stringify(cacheItem));
  return normalized;
};

const authenticatedTenantId = (platform: AuthPlatform) => {
  const tenantId = readSharedTenantId();
  if (tenantId) return tenantId;
  const keys = getAuthStorageKeys(platform);
  expireAuthentication(platform, localStorage.getItem(keys.refreshToken));
  throw new AuthenticationError("租户信息缺失，请重新登录");
};

export const AUTH_EXPIRED_EVENT = "zsjos-auth-expired";
const authExpiredDispatched: Record<AuthPlatform, boolean> = {
  PC: false,
  MOBILE: false,
};
export const isCurrentRefreshSession = (
  expectedRefreshToken: string | null,
  currentRefreshToken: string | null,
) => expectedRefreshToken === currentRefreshToken;
const expireAuthentication = (
  platform: AuthPlatform,
  expectedRefreshToken: string | null,
) => {
  const keys = getAuthStorageKeys(platform);
  if (
    !isCurrentRefreshSession(
      expectedRefreshToken,
      localStorage.getItem(keys.refreshToken),
    )
  )
    return;
  clearAuthStorage(platform);
  if (authExpiredDispatched[platform]) return;
  authExpiredDispatched[platform] = true;
  window.dispatchEvent(
    new CustomEvent(AUTH_EXPIRED_EVENT, { detail: { platform } }),
  );
};

http.interceptors.request.use((config) => {
  const request = config as typeof config & {
    _zsjosAuthPlatform?: AuthPlatform;
    _zsjosImpersonationSessionId?: number;
  };
  const platform = resolveRequestAuthPlatform(
    request._zsjosAuthPlatform,
    getAuthPlatform(),
  );
  request._zsjosAuthPlatform = platform;
  const keys = getAuthStorageKeys(platform);
  const expectedOrigin = new URL(
    config.baseURL || APP_CONFIG.API_BASE_URL,
    window.location.origin,
  ).origin;
  if (
    /^[a-z][a-z\d+.-]*:\/\//i.test(config.url || "") &&
    new URL(config.url!).origin !== expectedOrigin
  ) {
    config.headers.delete("tenant-id");
    config.headers.delete("Authorization");
    config.headers.delete("X-ZSJOS-Impersonation-Session");
    config.headers.delete("X-ZSJOS-Workbench-Platform");
    return config;
  }
  const token = localStorage.getItem(keys.accessToken);
  applyAuthorizationHeader(config.headers, token);
  config.headers["tenant-id"] = token
    ? authenticatedTenantId(platform)
    : readSharedTenantId() || APP_CONFIG.DEFAULT_TENANT_ID;
  config.headers["X-ZSJOS-Workbench-Platform"] = platform;
  const impersonation =
    platform === "PC"
      ? localStorage.getItem(STORAGE_KEYS.IMPERSONATION)
      : null;
  const impersonationId = resolveImpersonationSessionHeader(
    config.url,
    impersonation,
    expectedOrigin,
  );
  delete request._zsjosImpersonationSessionId;
  config.headers.delete("X-ZSJOS-Impersonation-Session");
  if (impersonationId != null) {
    config.headers["X-ZSJOS-Impersonation-Session"] = impersonationId;
    request._zsjosImpersonationSessionId = impersonationId;
  }
  return config;
});

export type NotifyMessageCursorParams = {
  cursor?: string;
  limit?: number;
  readStatus?: boolean;
};

const isAuthEndpoint = (url?: string) =>
  [
    "/system/auth/login",
    "/system/auth/logout",
    "/system/auth/refresh-token",
  ].some((path) => url?.includes(path));

const retryAfterRefresh = async (
  config: AxiosRequestConfig,
  originalError: unknown,
) => {
  const request = config as AxiosRequestConfig & {
    _retry?: boolean;
    _zsjosAuthPlatform?: AuthPlatform;
  };
  if (request._retry || isAuthEndpoint(request.url))
    return Promise.reject(originalError);
  request._retry = true;
  const platform = request._zsjosAuthPlatform ?? getAuthPlatform();
  const keys = getAuthStorageKeys(platform);
  // Admin iframe 可能已经完成刷新；先复用共享存储中的新 token，避免两个窗口同时刷新。
  const sharedAccessToken = localStorage.getItem(keys.accessToken);
  const sentAuthorization = String(
    request.headers?.Authorization || request.headers?.authorization || "",
  );
  if (
    sharedAccessToken &&
    sentAuthorization !== `Bearer ${sharedAccessToken}`
  ) {
    request.headers = {
      ...request.headers,
      Authorization: `Bearer ${sharedAccessToken}`,
    };
    request._zsjosAuthPlatform = platform;
    return http(request);
  }
  if (!refreshing[platform]) {
    const task = refreshToken(platform);
    refreshing[platform] = task;
    void task.finally(() => {
      if (refreshing[platform] === task) delete refreshing[platform];
    });
  }
  const result = await refreshing[platform]!;
  if (result.status === "stale") return Promise.reject(originalError);
  if (result.status === "failed") {
    expireAuthentication(platform, result.expectedRefreshToken);
    return Promise.reject(new AuthenticationError());
  }
  request.headers = {
    ...request.headers,
    Authorization: `Bearer ${result.accessToken}`,
  };
  request._zsjosAuthPlatform = platform;
  return http(request);
};

const clearRejectedImpersonation = (
  code: unknown,
  config?: AxiosRequestConfig & { _zsjosImpersonationSessionId?: number },
) => {
  if (typeof code === "number")
    handleImpersonationInvalid(code, config?._zsjosImpersonationSessionId);
};

http.interceptors.response.use(
  async (response) => {
    clearRejectedImpersonation(response.data?.code, response.config);
    if (response.data?.code === 401)
      return retryAfterRefresh(
        response.config,
        new AuthenticationError(response.data.msg),
      );
    return response;
  },
  async (error) => {
    const original = error.config as
      (AxiosRequestConfig & { _retry?: boolean }) | undefined;
    clearRejectedImpersonation(error.response?.data?.code, original);
    if (error.response?.status !== 401 || !original)
      return Promise.reject(error);
    return retryAfterRefresh(original, error);
  },
);

let dictDataRequest: Promise<DictData[]> | undefined;

export const unwrap = <T>(response: { data: any }): T => {
  const payload = response.data;
  if (payload && typeof payload.code === "number") {
    if (payload.code === 401) throw new AuthenticationError(payload.msg);
    if (payload.code !== 0) {
      if (payload.code === FORCED_FORM_REQUIRED_CODE) {
        window.dispatchEvent(
          new CustomEvent("zsjos-forced-form-required", { detail: payload.data }),
        );
      }
      throw new ApiError(
        payload.code,
        payload.msg || `请求失败（${payload.code}）`,
      );
    }
    return payload.data as T;
  }
  return payload as T;
};

async function refreshToken(platform: AuthPlatform): Promise<RefreshResult> {
  const keys = getAuthStorageKeys(platform);
  const refresh = localStorage.getItem(keys.refreshToken);
  if (!refresh) return { status: "failed", expectedRefreshToken: null };
  try {
    const expectedClientId = AUTH_CLIENT_IDS[platform];
    const clientId = localStorage.getItem(keys.clientId) || expectedClientId;
    const clientIdParam = `&clientId=${encodeURIComponent(clientId)}`;
    const tenantId = authenticatedTenantId(platform);
    const response = await axios.post(
      `${APP_CONFIG.API_BASE_URL}/system/auth/refresh-token?refreshToken=${encodeURIComponent(refresh)}${clientIdParam}`,
      undefined,
      { headers: { "tenant-id": tenantId }, timeout: 30000 },
    );
    const result = unwrap<{
      accessToken: string;
      refreshToken: string;
      clientId?: string;
    }>(response);
    if (result.clientId && result.clientId !== expectedClientId)
      throw new AuthenticationError("登录端类型不匹配，请重新登录");
    if (
      !isCurrentRefreshSession(
        refresh,
        localStorage.getItem(keys.refreshToken),
      )
    ) {
      return { status: "stale" };
    }
    localStorage.setItem(keys.accessToken, result.accessToken);
    localStorage.setItem(keys.refreshToken, result.refreshToken);
    localStorage.setItem(keys.clientId, expectedClientId);
    return { status: "refreshed", accessToken: result.accessToken };
  } catch {
    return isCurrentRefreshSession(
      refresh,
      localStorage.getItem(keys.refreshToken),
    )
      ? { status: "failed", expectedRefreshToken: refresh }
      : { status: "stale" };
  }
}

const isUrl = (path: string) => /^https?:\/\//i.test(path);

// Keep this aligned with yudao-ui's pathResolve: child paths are relative to their parent.
export const resolveMenuPath = (parentPath: string, path?: string) => {
  if (path && isUrl(path)) return path;
  if (!path) return parentPath;
  const childPath = path.startsWith("/") ? path : `/${path}`;
  return `${parentPath}${childPath}`.replace(/\/{2,}/g, "/");
};

export function buildMenuTree(
  rawMenus: RawMenu[],
  parentPath = "/",
  pathsAreAbsolute = false,
): WorkbenchMenu[] {
  return rawMenus.map((menu) => {
    const path =
      pathsAreAbsolute && menu.path
        ? menu.path
        : resolveMenuPath(parentPath, menu.path);
    const children = buildMenuTree(menu.children || [], path, pathsAreAbsolute);
    return {
      ...menu,
      path,
      hidden: !menu.visible,
      noCache: !menu.keepAlive,
      alwaysShow: children.length > 0 && (menu.alwaysShow ?? true),
      children,
    };
  });
}

export const api = {
  login: async (
    username: string,
    password: string,
    platform: AuthPlatform = "PC",
  ) => {
    writeSharedTenantId(readSharedTenantId() || APP_CONFIG.DEFAULT_TENANT_ID);
    const result = unwrap<{
      accessToken: string;
      refreshToken: string;
      expiresTime: string;
      clientId?: string;
    }>(await http.post("/system/auth/login", { username, password, platform }));
    const expectedClientId = AUTH_CLIENT_IDS[platform];
    if (result.clientId && result.clientId !== expectedClientId) {
      clearAuthStorage(platform);
      throw new AuthenticationError("登录端类型不匹配，请重新登录");
    }
    const keys = getAuthStorageKeys(platform);
    localStorage.setItem(keys.accessToken, result.accessToken);
    localStorage.setItem(keys.refreshToken, result.refreshToken);
    localStorage.setItem(keys.expiresTime, result.expiresTime);
    localStorage.setItem(keys.clientId, expectedClientId);
    delete refreshing[platform];
    authExpiredDispatched[platform] = false;
    return result;
  },
  logout: async (platform: AuthPlatform = getAuthPlatform()) => {
    try {
      await http.post("/system/auth/logout", undefined, {
        _zsjosAuthPlatform: platform,
      } as AuthAxiosRequestConfig);
    } finally {
      clearAuthStorage(platform);
    }
  },
  permissionInfo: async () =>
    unwrap<PermissionInfo>(await http.get("/system/auth/get-permission-info")),
  userProfile: async () =>
    unwrap<UserProfile>(await http.get("/system/user/profile/get")),
  updateUserProfile: async (data: UserProfileUpdate) =>
    unwrap<boolean>(await http.put("/system/user/profile/update", data)),
  updateUserPassword: async (oldPassword: string, newPassword: string) =>
    unwrap<boolean>(
      await http.put("/system/user/profile/update-password", {
        oldPassword,
        newPassword,
      }),
    ),
  uploadAvatar: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    data.append("directory", "employee/avatar");
    return unwrap<string>(await http.post("/infra/file/avatar/upload", data));
  },
  boundSocialUsers: async () =>
    unwrap<SocialUser[]>(await http.get("/system/social-user/get-bind-list")),
  socialAuthRedirect: async (type: number, redirectUri: string) =>
    unwrap<string>(
      await http.get("/system/auth/social-auth-redirect", {
        params: { type, redirectUri },
      }),
    ),
  bindSocialUser: async (type: number, code: string, state: string) =>
    unwrap<boolean>(
      await http.post("/system/social-user/bind", { type, code, state }),
    ),
  unbindSocialUser: async (type: number, openid: string) =>
    unwrap<boolean>(
      await http.delete("/system/social-user/unbind", {
        data: { type, openid },
      }),
    ),
  dictDataByType: async (dictType: string) => {
    const request =
      dictDataRequest ??
      (dictDataRequest = http
        .get("/system/dict-data/simple-list")
        .then((response) => unwrap<DictData[]>(response))
        .catch((error: unknown) => {
          dictDataRequest = undefined;
          throw error;
        }));
    return request.then((dictData) =>
      dictData.filter((item) => item.dictType === dictType),
    );
  },
  eam: {
    myAssets: async () =>
      unwrap<EamAssetSummary>(await http.get("/eam/workbench/my-assets")),
    myDemands: async () =>
      unwrap<EamDemand[]>(await http.get("/eam/workbench/my-demands")),
    categories: async () =>
      unwrap<EamCategory[]>(await http.get("/eam/workbench/categories")),
    categoryFields: async (categoryId: number) =>
      unwrap<EamCategoryField[]>(
        await http.get("/eam/workbench/category-fields", {
          params: { categoryId },
        }),
      ),
    previewStockCandidates: async (data: EamDemandItem) =>
      unwrap<EamStockCandidate[]>(
        await http.post("/eam/workbench/stock-candidates", data),
      ),
    createDemand: async (data: { reason?: string; items: EamDemandItem[] }) =>
      unwrap<number>(await http.post("/eam/workbench/demand", data)),
    sign: async (holdingId: number) =>
      unwrap<boolean>(
        await http.put(`/eam/workbench/holding/${holdingId}/sign`),
      ),
    applyReturn: async (holdingId: number, remark?: string) =>
      unwrap<boolean>(
        await http.put(
          `/eam/workbench/holding/${holdingId}/return`,
          undefined,
          { params: { remark } },
        ),
      ),
    repair: async (data: { assetId: number; faultDesc: string }) =>
      unwrap<number>(await http.post("/eam/workbench/repair", data)),
  },
  areaTree: async () => unwrap<AreaNode[]>(await http.get("/system/area/tree")),
  leadCatalog: async () =>
    unwrap<LeadCatalog>(await http.get("/zsjos/lead/product/catalog")),
  uploadLeadAttachment: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<LeadAttachment>(
      await http.post("/zsjos/lead/attachment/upload", data),
    );
  },
  createLead: async (data: LeadCreateRequest) =>
    unwrap<LeadCreateResult>(await http.post("/zsjos/lead/create", data)),
  createSelfSourcedLead: async (data: LeadCreateRequest) =>
    unwrap<LeadCreateResult>(
      await http.post("/zsjos/lead/self-sourced/create", data),
    ),
  newMediaProviders: async () =>
    unwrap<SalesUser[]>(
      await http.get("/zsjos/lead/self-sourced/new-media-providers"),
    ),
  mediaAccount: {
    create: async (data: {
      studentPersonId: number;
      platformValue: string;
      platformLabelSnapshot: string;
      detailValues: Record<string, unknown>;
    }) => unwrap<number>(await http.post("/zsjos/media-account/create", data)),
    publishedFieldConfig: async () =>
      unwrap<MediaAccountFieldConfig>(
        await http.get("/zsjos/media-account-field-config/published"),
      ),
    get: async (id: number) =>
      unwrap<MediaAccount>(
        await http.get("/zsjos/media-account/get", { params: { id } }),
      ),
    page: async (params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
      sStage?: string;
    }) =>
      unwrap<PageResult<MediaAccount>>(
        await http.get("/zsjos/media-account/page", { params }),
      ),
    studentCandidates: async (keyword?: string) =>
      unwrap<Array<{ personId: number; name?: string }>>(
        await http.get("/zsjos/media-account/student-candidates", {
          params: { keyword },
        }),
      ),
    bindStudent: async (id: number, studentPersonId: number, reason?: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/media-account/${id}/bind-student`, null, {
          params: { studentPersonId, reason },
        }),
      ),
    unbindStudent: async (id: number, reason?: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/media-account/${id}/unbind-student`, null, {
          params: { reason },
        }),
      ),
    update: async (
      id: number,
      data: Partial<MediaAccount> & { version: number; nickname: string },
    ) => unwrap<boolean>(await http.put(`/zsjos/media-account/${id}`, data)),
    maintain: async (
      id: number,
      data: {
        version: number;
        currentStatusValue?: string;
        stageValue?: string;
        primaryProblemValues?: string[];
        executionMeasureValue?: string;
        adjustmentDirection?: string;
        startDate?: string;
        endDate?: string;
      },
    ) =>
      unwrap<number>(
        await http.put(`/zsjos/media-account/${id}/maintenance`, data),
      ),
    maintenanceHistory: async (
      id: number,
      params: { pageNo: number; pageSize: number },
    ) =>
      unwrap<PageResult<MediaAccountMaintenanceRevision>>(
        await http.get(`/zsjos/media-account/${id}/maintenance-history`, {
          params,
        }),
      ),
    calendar: async (params: {
      pageNo: number;
      pageSize: number;
      rangeStart: string;
      rangeEnd: string;
      keyword?: string;
      currentStatusValue?: string;
      stageValue?: string;
      directorUserId?: number;
      operatorUserId?: number;
    }) =>
      unwrap<MediaAccountCalendarResult>(
        await http.get("/zsjos/media-account/calendar", { params }),
      ),
    calendarAll: async (params: {
      rangeStart: string;
      rangeEnd: string;
      keyword?: string;
      currentStatusValue?: string;
      stageValue?: string;
      directorUserId?: number;
      operatorUserId?: number;
    }) =>
      unwrap<MediaAccountCalendarResult>(
        await http.get("/zsjos/media-account/calendar/all", { params }),
      ),
    calendarCandidates: async () =>
      unwrap<MediaAccountCalendarCandidates>(
        await http.get("/zsjos/media-account/calendar/candidates"),
      ),
    diagnose: async (
      id: number,
      data: {
        weekNo: string;
        statStart: string;
        statEnd: string;
        basicJson: string;
        productionFunnelJson: string;
        platformDataJson: string;
        contentPerfJson: string;
        leadFunnelJson: string;
        rootCauseJson: string;
        nextWeekPlanJson: string;
        suggestedGrade?: string;
        configVersionId: number;
      },
    ) =>
      unwrap<number>(
        await http.post(`/zsjos/media-account/${id}/diagnoses`, data),
      ),
    publishedDiagnosisConfig: async () =>
      unwrap<number>(
        await http.get("/zsjos/media-account/diagnosis-config/published"),
      ),
    rescue: async (id: number, version: number, status: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/media-account/${id}/rescue`, null, {
          params: { version, status },
        }),
      ),
    requestRebind: async (
      id: number,
      targetStudentId: number,
      version: number,
    ) =>
      unwrap<string>(
        await http.post(`/zsjos/media-account/${id}/request-rebind`, null, {
          params: { targetStudentId, version },
        }),
      ),
  },
  mediaContent: {
    create: async (data: {
      accountId: number;
      title: string;
      topic?: string;
      contentClassValue: string;
      contentClassLabelSnapshot: string;
    }) => unwrap<number>(await http.post("/zsjos/content/create", data)),
    get: async (id: number) =>
      unwrap<MediaContent>(
        await http.get("/zsjos/content/get", { params: { id } }),
      ),
    page: async (params: {
      pageNo: number;
      pageSize: number;
      status?: string;
      keyword?: string;
    }) =>
      unwrap<PageResult<MediaContent>>(
        await http.get("/zsjos/content/page", { params }),
      ),
    completeTopic: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/complete-topic`, null, {
          params: { version },
        }),
      ),
    submitProduction: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/submit-production`, null, {
          params: { version },
        }),
      ),
    submitAcceptance: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/submit-acceptance`, null, {
          params: { version },
        }),
      ),
    approveAcceptance: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/approve-acceptance`, null, {
          params: { version },
        }),
      ),
    rejectAcceptance: async (id: number, version: number, reason: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/reject-acceptance`, null, {
          params: { version, reason },
        }),
      ),
    startRevision: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/start-revision`, null, {
          params: { version },
        }),
      ),
    resubmitProduction: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/content/${id}/resubmit-production`, null, {
          params: { version },
        }),
      ),
    versions: async (contentId: number) =>
      unwrap<unknown[]>(
        await http.get("/zsjos/content/version/list", {
          params: { contentId },
        }),
      ),
  },
  productionTicket: {
    createContext: async (accountId: number, sceneCode: string) =>
      unwrap<ProductionTicketCreateContext>(
        await http.get("/zsjos/production-ticket/create-context", {
          params: { accountId, sceneCode },
        }),
      ),
    create: async (data: { sceneCode: string; accountId: number; assigneeUserId?: number; targetDeptId?: number; operatorRemark?: string; values?: Record<string, unknown>; attachmentIds?: number[] }) =>
      unwrap<number>(
        await http.post("/zsjos/production-ticket/create", {
          ...data,
          idempotencyKey: createIdempotencyKey(),
        }),
      ),
    get: async (id: number) =>
      unwrap<ProductionTicket>(
        await http.get("/zsjos/production-ticket/get", { params: { id } }),
      ),
    page: async (params: {
      pageNo: number;
      pageSize: number;
      status?: string;
      keyword?: string;
    }) =>
      unwrap<PageResult<ProductionTicket>>(
        await http.get("/zsjos/production-ticket/page", { params }),
      ),
    pendingAssignments: async () =>
      unwrap<ProductionTicket[]>(
        await http.get("/zsjos/production-ticket/assignment/my-pending"),
      ),
    poolPage: async (params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
    }) =>
      unwrap<PageResult<ProductionTicket>>(
        await http.get("/zsjos/production-ticket/pool/page", { params }),
      ),
    accept: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/accept`, null, {
          params: { version },
        }),
      ),
    rejectAssignment: async (id: number, version: number, reason: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/reject-assignment`, {
          version,
          reason,
          idempotencyKey: createIdempotencyKey(),
        }),
      ),
    claim: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/claim`, {
          version,
          idempotencyKey: createIdempotencyKey(),
        }),
      ),
    startProduction: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(
          `/zsjos/production-ticket/${id}/start-production`,
          null,
          { params: { version } },
        ),
      ),
    submit: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/submit`, null, {
          params: { version },
        }),
      ),
    startCheck: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/start-check`, null, {
          params: { version },
        }),
      ),
    approve: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/approve`, null, {
          params: { version },
        }),
      ),
    reject: async (id: number, version: number, reason: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/reject`, null, {
          params: { version, reason },
        }),
      ),
    reaccept: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/production-ticket/${id}/reaccept`, null, {
          params: { version },
        }),
      ),
  },
  positioningCard: {
    publishedTemplate: async (templateId?: number) =>
      unwrap<DirectorTemplateSnapshot>(
        await http.get("/zsjos/positioning-card/published-template", {
          params: { templateId },
        }),
      ),
    create: async (data: PositioningCardDraftRequest) =>
      unwrap<number>(await http.post("/zsjos/positioning-card/create", data)),
    createDraft: async (data: PositioningCardDraftRequest) =>
      unwrap<PositioningCardDraftResult>(
        await http.post("/zsjos/positioning-card/draft", data),
      ),
    updateDraft: async (
      id: number,
      data: PositioningCardDraftRequest & { version: number },
    ) =>
      unwrap<PositioningCardDraftResult>(
        await http.put(`/zsjos/positioning-card/draft/${id}`, data),
      ),
    importSources: async (params: {
      studentPersonId: number;
      accountId: number;
      serviceRelationId: number;
    }) =>
      unwrap<PositioningCardImportSource[]>(
        await http.get("/zsjos/positioning-card/import-sources", { params }),
      ),
    importSubmission: async (data: {
      sourceSubmissionId: number;
      accountId: number;
      studentPersonId: number;
      serviceRelationId: number;
      targetDraftId?: number;
      version?: number;
    }) =>
      unwrap<PositioningCardImportResult>(
        await http.post("/zsjos/positioning-card/import", data),
      ),
    get: async (id: number) =>
      unwrap<PositioningCard>(
        await http.get("/zsjos/positioning-card/get", { params: { id } }),
      ),
    page: async (params: {
      pageNo: number;
      pageSize: number;
      status?: string;
    }) =>
      unwrap<PageResult<PositioningCard>>(
        await http.get("/zsjos/positioning-card/page", { params }),
      ),
    submitReview: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/positioning-card/${id}/submit-review`, null, {
          params: { version },
        }),
      ),
    operatorApprove: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(
          `/zsjos/positioning-card/${id}/operator-approve`,
          null,
          { params: { version } },
        ),
      ),
    operatorReject: async (id: number, version: number, reason: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/positioning-card/${id}/operator-reject`, null, {
          params: { version, reason },
        }),
      ),
    generateStudentLink: async (id: number, version: number) =>
      unwrap<PositioningLinkResult>(
        await http.post(`/zsjos/positioning-card/${id}/student-link`, null, {
          params: { version },
        }),
      ),
    startRevision: async (id: number, version: number) =>
      unwrap<PositioningCardDraftResult>(
        await http.post(`/zsjos/positioning-card/${id}/start-revision`, null, {
          params: { version },
        }),
      ),
    versions: async (cardId: number) =>
      unwrap<unknown[]>(
        await http.get("/zsjos/positioning/workspace/versions", {
          params: { cardId },
        }),
      ),
    execCard: async (cardId: number) =>
      unwrap<unknown>(
        await http.get("/zsjos/positioning/workspace/exec-card", {
          params: { cardId },
        }),
      ),
  },
  directorConfig: {
    templates: async (positioning: boolean) =>
      unwrap<DirectorTemplate[]>(
        await http.get(
          positioning
            ? "/zsjos/positioning-template/list"
            : "/zsjos/director-interview-template/list",
        ),
      ),
    copyDraft: async (positioning: boolean, id: number, version: number) =>
      unwrap<number>(
        await http.post(
          `${positioning ? "/zsjos/positioning-template" : "/zsjos/director-interview-template"}/${id}/draft/copy`,
          null,
          { params: { version } },
        ),
      ),
    saveDraft: async (
      positioning: boolean,
      id: number,
      data: {
        versionId: number;
        version: number;
        name: string;
        defaultTemplate: boolean;
        fields: StudentContactFormField[];
      },
    ) =>
      unwrap<boolean>(
        await http.put(
          `${positioning ? "/zsjos/positioning-template" : "/zsjos/director-interview-template"}/${id}/draft`,
          data,
        ),
      ),
    publish: async (
      positioning: boolean,
      id: number,
      data: { versionId: number; version: number },
    ) =>
      unwrap<boolean>(
        await http.post(
          `${positioning ? "/zsjos/positioning-template" : "/zsjos/director-interview-template"}/${id}/publish`,
          data,
        ),
      ),
    get: async () =>
      unwrap<DirectorConfig>(await http.get("/zsjos/director-config")),
    update: async (data: DirectorConfig) =>
      unwrap<boolean>(await http.put("/zsjos/director-config", data)),
  },
  mediaReview: {
    list: async () =>
      unwrap<MediaReview[]>(await http.get("/zsjos/reviews/list")),
    create: async (data: {
      reviewType: string;
      subjectType: string;
      subjectId: number;
      reportJson: string;
      evidenceRefsJson?: string;
    }) => unwrap<number>(await http.post("/zsjos/reviews/create", data)),
    submit: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/reviews/${id}/submit`, null, {
          params: { version },
        }),
      ),
    approve: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/reviews/${id}/approve`, null, {
          params: { version },
        }),
      ),
    reject: async (id: number, version: number, reason: string) =>
      unwrap<boolean>(
        await http.post(`/zsjos/reviews/${id}/reject`, null, {
          params: { version, reason },
        }),
      ),
    archive: async (id: number, version: number) =>
      unwrap<boolean>(
        await http.post(`/zsjos/reviews/${id}/archive`, null, {
          params: { version },
        }),
      ),
  },
  duplicateReviewPage: async (params: {
    status: "pending" | "completed";
    pageNo: number;
    pageSize: number;
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter || params.keyword
      ? unwrap<PageResult<LeadDuplicateReview>>(
          await http.post("/zsjos/lead-duplicate-review/search-page", params),
        )
      : unwrap<PageResult<LeadDuplicateReview>>(
          await http.get("/zsjos/lead-duplicate-review/page", { params }),
        ),
  duplicateReviewSalesCandidates: async () =>
    unwrap<AssignmentUser[]>(
      await http.get("/zsjos/lead-duplicate-review/sales-candidates"),
    ),
  uploadDuplicateReviewAttachment: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<LeadAttachment>(
      await http.post("/zsjos/lead-duplicate-review/attachment/upload", data),
    );
  },
  decideDuplicateReview: async (
    id: number,
    data: LeadDuplicateReviewDecision,
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/lead-duplicate-review/${id}/decision`, data),
    ),
  myPendingLeads: async () =>
    unwrap<PendingLead[]>(await http.get("/zsjos/lead/assignment/my-pending")),
  myDispatchStatus: async () =>
    unwrap<SalesDispatchStatus>(
      await http.get("/zsjos/lead/dispatch-status/my"),
    ),
  dispatchHeartbeat: async () =>
    unwrap<SalesDispatchStatus>(
      await http.post("/zsjos/lead/dispatch-status/heartbeat"),
    ),
  updateDispatchMode: async (accepting: boolean) =>
    unwrap<SalesDispatchStatus>(
      await http.put("/zsjos/lead/dispatch-status/mode", { accepting }),
    ),
  dispatchOffline: async () =>
    unwrap<SalesDispatchStatus>(
      await http.post("/zsjos/lead/dispatch-status/offline"),
    ),
  acceptLead: async (id: number) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/accept`)),
  rejectLead: async (id: number) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/reject`)),
  claimPoolPage: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<PendingLead>>(
          await http.post("/zsjos/lead/claim-pool/search-page", params),
        )
      : unwrap<PageResult<PendingLead>>(
          await http.get("/zsjos/lead/claim-pool/page", { params }),
        ),
  claimLead: async (id: number) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/claim`)),
  managedLeadInboxPage: async (
    audience: "submitter" | "owner",
    params: ManagedLeadPageParams,
  ) =>
    params.advancedFilter
      ? unwrap<PageResult<ManagedLead>>(
          await http.post(
            `/zsjos/lead/inbox/${audience === "submitter" ? "submitted" : "owned"}/search-page`,
            params,
          ),
        )
      : unwrap<PageResult<ManagedLead>>(
          await http.get(
            `/zsjos/lead/inbox/${audience === "submitter" ? "submitted" : "owned"}/page`,
            { params },
          ),
        ),
  managedLeadInboxCursor: async (
    audience: "submitter" | "owner",
    params: Omit<ManagedLeadPageParams, "pageNo" | "pageSize"> & {
      cursor?: string;
      limit?: number;
    },
  ) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<ManagedLead>>(
          await http.post(
            `/zsjos/lead/inbox/${audience === "submitter" ? "submitted" : "owned"}/search-cursor`,
            params,
          ),
        )
      : unwrap<CursorPageResult<ManagedLead>>(
          await http.get(
            `/zsjos/lead/inbox/${audience === "submitter" ? "submitted" : "owned"}/cursor`,
            { params },
          ),
        ),
  managedLead: async (id: number) =>
    unwrap<ManagedLead>(await http.get("/zsjos/lead/get", { params: { id } })),
  leadFlowHistory: async (id: number) =>
    unwrap<LeadFlowHistory[]>(await http.get(`/zsjos/lead/${id}/flow-history`)),
  allLeadPage: async (params: ManagedLeadPageParams) =>
    params.advancedFilter
      ? unwrap<PageResult<ManagedLead>>(
          await http.post("/zsjos/lead/search-page", params),
        )
      : unwrap<PageResult<ManagedLead>>(
          await http.get("/zsjos/lead/page", { params }),
        ),
  allLeadCursor: async (
    params: Omit<ManagedLeadPageParams, "pageNo" | "pageSize"> & {
      cursor?: string;
      limit?: number;
    },
  ) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<ManagedLead>>(
          await http.post("/zsjos/lead/search-cursor", params),
        )
      : unwrap<CursorPageResult<ManagedLead>>(
          await http.get("/zsjos/lead/cursor", { params }),
        ),
  agingPoolPage: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    status?: LeadAgingPoolStatus;
    inboxGroup?: string;
    inboxStage?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<LeadAgingPoolItem>>(
          await http.post("/zsjos/lead/aging-pool/search-page", params),
        )
      : unwrap<PageResult<LeadAgingPoolItem>>(
          await http.get("/zsjos/lead/aging-pool/page", { params }),
        ),
  agingPoolCounts: async () =>
    unwrap<Record<string, number>>(
      await http.get("/zsjos/lead/aging-pool/counts"),
    ),
  agingPoolFilterProfile: async () =>
    unwrap<LeadInboxFilterProfile>(
      await http.get("/zsjos/lead/aging-pool/filter-profile"),
    ),
  agingPoolCandidates: async (cycleId: number) =>
    unwrap<Array<{ id: number; nickname: string }>>(
      await http.get(`/zsjos/lead/aging-pool/${cycleId}/candidates`),
    ),
  assignAgingPool: async (cycleId: number, salesUserId: number) =>
    unwrap<boolean>(
      await http.post(`/zsjos/lead/aging-pool/${cycleId}/assign`, {
        salesUserId,
        idempotencyKey: createIdempotencyKey(),
      }),
    ),
  exitAgingPool: async (cycleId: number, reason: string) =>
    unwrap<boolean>(
      await http.post(`/zsjos/lead/aging-pool/${cycleId}/exit`, {
        reason,
        idempotencyKey: createIdempotencyKey(),
      }),
    ),
  requestAgingPoolTransfer: async (cycleId: number, reason: string) =>
    unwrap<number>(
      await http.post(`/zsjos/lead/aging-pool/${cycleId}/transfer-request`, {
        reason,
        idempotencyKey: createIdempotencyKey(),
      }),
    ),
  managedLeadStatusCounts: async () =>
    unwrap<Record<string, number>>(await http.get("/zsjos/lead/status-counts")),
  judgeLeadValid: async (
    id: number,
    data: { leadCategory?: string; remark: string; idempotencyKey: string },
  ) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-valid`, data)),
  judgeLeadInvalid: async (
    id: number,
    data: {
      reasonCode: string;
      description: string;
      attachments: Array<{ infraFileId: number }>;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/judge-invalid`, data)),
  uploadLeadQualificationImage: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<LeadAttachment>(
      await http.post("/zsjos/lead/qualification/attachment/upload", data),
    );
  },
  updateLeadBasicInfo: async (id: number, data: LeadBasicInfoUpdateRequest) =>
    unwrap<boolean>(await http.put(`/zsjos/lead/${id}/basic-info`, data)),
  supplementLead: async (
    id: number,
    data: {
      provinceCode: string;
      cityCode: string;
      leadCategory: string;
      intendedProducts: LeadCreateRequest["intendedProducts"];
      remark?: string;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/lead/${id}/submitter-supplement`, data),
    ),
  urgeLead: async (id: number, reason: string) =>
    unwrap<boolean>(await http.post(`/zsjos/lead/${id}/urge`, { reason })),
  createLeadComplaint: async (
    id: number,
    reason: string,
    evidenceFileIds: number[],
  ) =>
    unwrap<number>(
      await http.post(`/zsjos/lead-complaint/lead/${id}`, {
        reason,
        evidenceFileIds,
        idempotencyKey: createIdempotencyKey(),
      }),
    ),
  leadComplaintPage: async (params: {
    status: "pending" | "handled";
    pageNo: number;
    pageSize: number;
  }) =>
    unwrap<PageResult<LeadComplaint>>(
      await http.get("/zsjos/lead-complaint/page", { params }),
    ),
  leadComplaints: async (leadId: number) =>
    unwrap<LeadComplaint[]>(
      await http.get(`/zsjos/lead-complaint/lead/${leadId}/list`),
    ),
  decideLeadComplaint: async (
    id: number,
    result: "founded" | "unfounded",
    opinion: string,
    evidenceFileIds: number[],
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/lead-complaint/${id}/decision`, {
        result,
        opinion,
        evidenceFileIds,
        idempotencyKey: createIdempotencyKey(),
      }),
    ),
  qualificationExceptionPage: async (
    type: "suspended" | "recycle_pending",
    params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
      advancedFilter?: AdvancedFilterGroup;
    },
  ) =>
    params.advancedFilter
      ? unwrap<PageResult<LeadQualificationException>>(
          await http.post("/zsjos/lead/qualification-exception/search-page", {
            type,
            ...params,
          }),
        )
      : unwrap<PageResult<LeadQualificationException>>(
          await http.get("/zsjos/lead/qualification-exception/page", {
            params: { type, ...params },
          }),
        ),
  leadTransferCandidates: async (id: number) =>
    unwrap<AssignmentUser[]>(
      await http.get(`/zsjos/lead/${id}/transfer-candidates`),
    ),
  restoreLead: async (
    id: number,
    data: { reason: string; idempotencyKey: string },
  ) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/restore`, data)),
  transferLead: async (
    id: number,
    data: { salesUserId: number; reason: string; idempotencyKey: string },
  ) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/transfer`, data)),
  recycleLead: async (
    id: number,
    data: { reason: string; idempotencyKey: string },
  ) => unwrap<boolean>(await http.post(`/zsjos/lead/${id}/recycle`, data)),
  releaseLeadToClaimPool: async (
    id: number,
    data: { reason: string; idempotencyKey: string },
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/lead/${id}/release-to-claim-pool`, data),
    ),
  leadInboxFilterProfile: async (audience: "submitter" | "owner") =>
    unwrap<LeadInboxFilterProfile>(
      await http.get(
        `/zsjos/lead/inbox/${audience === "submitter" ? "submitted" : "owned"}/filter-profile`,
      ),
    ),
  leadFollowUpPage: async (
    leadId: number,
    params: { pageNo: number; pageSize: number },
  ) =>
    unwrap<PageResult<LeadFollowUp>>(
      await http.get(`/zsjos/lead/${leadId}/follow-ups/page`, { params }),
    ),
  createLeadFollowUp: async (leadId: number, data: LeadFollowUpCreateRequest) =>
    unwrap<LeadFollowUp>(
      await http.post(`/zsjos/lead/${leadId}/follow-ups`, data),
    ),
  uploadLeadFollowUpImage: async (leadId: number, file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<LeadAttachment>(
      await http.post(`/zsjos/lead/${leadId}/follow-up-image/upload`, data),
    );
  },
  leadAppeals: async (leadId: number) =>
    unwrap<LeadAppeal[]>(
      await http.get(`/zsjos/lead/appeal/lead/${leadId}/list`),
    ),
  submitLeadAppeal: async (
    leadId: number,
    data: {
      reason: string;
      attachments: Array<{ infraFileId: number }>;
      idempotencyKey: string;
    },
  ) =>
    unwrap<number>(
      await http.post(`/zsjos/lead/appeal/lead/${leadId}/submit`, data),
    ),
  leadAppealInboxPage: async (
    handled: boolean,
    params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
      advancedFilter?: AdvancedFilterGroup;
    },
  ) =>
    params.advancedFilter || params.keyword
      ? unwrap<PageResult<LeadAppeal>>(
          await http.post("/zsjos/lead/appeal/inbox/search-page", {
            handled,
            ...params,
          }),
        )
      : unwrap<PageResult<LeadAppeal>>(
          await http.get("/zsjos/lead/appeal/inbox-page", {
            params: { handled, ...params },
          }),
        ),
  leadAppealInboxCursor: async (
    handled: boolean,
    params: {
      cursor?: string;
      limit?: number;
      keyword?: string;
      advancedFilter?: AdvancedFilterGroup;
    },
  ) =>
    params.advancedFilter || params.keyword
      ? unwrap<CursorPageResult<LeadAppeal>>(
          await http.post("/zsjos/lead/appeal/inbox/search-cursor", {
            handled,
            ...params,
          }),
        )
      : unwrap<CursorPageResult<LeadAppeal>>(
          await http.get("/zsjos/lead/appeal/inbox-cursor", {
            params: { handled, ...params },
          }),
        ),
  decideLeadAppeal: async (
    appealId: number,
    decision: "overturn" | "uphold",
    data: {
      taskId: string;
      reason: string;
      attachments: Array<{ infraFileId: number }>;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/lead/appeal/${appealId}/${decision}`, data),
    ),
  uploadLeadAppealImage: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<LeadAttachment>(
      await http.post("/zsjos/lead/appeal/attachment/upload", data),
    );
  },
  salesOrderCatalog: async () =>
    unwrap<LeadCatalog>(await http.get("/zsjos/sales-order/product/catalog")),
  currentPurchaseIntent: async (
    data: Pick<
      PurchaseIntentDraftRequest,
      "purchaseType" | "leadId" | "personId" | "sourceKey"
    >,
  ) =>
    unwrap<PurchaseIntent | undefined>(
      await http.post("/zsjos/purchase-intent/current", data),
    ),
  savePurchaseIntentDraft: async (data: PurchaseIntentDraftRequest) =>
    unwrap<PurchaseIntent>(
      await http.post("/zsjos/purchase-intent/save-draft", data),
    ),
  createPurchasePaymentLink: async (data: PurchaseIntentDraftRequest) =>
    unwrap<PurchaseIntent>(
      await http.post(
        "/zsjos/purchase-intent/save-and-create-payment-link",
        data,
      ),
    ),
  refreshPurchasePayment: async (id: number) =>
    unwrap<PurchaseIntent>(
      await http.post(`/zsjos/purchase-intent/${id}/refresh-payment`),
    ),
  applyPaymentRefund: async (data: { paymentTransactionId: number; orderId?: number; reason: string; idempotencyKey: string }) =>
    unwrap<PaymentRefund>(await http.post("/zsjos/payment-refund/apply", data)),
  directPaymentRefund: async (data: { paymentTransactionId: number; orderId?: number; reason: string; idempotencyKey: string }) =>
    unwrap<PaymentRefund>(await http.post("/zsjos/payment-refund/direct", data)),
  paymentRefund: async (id: number) => unwrap<PaymentRefund>(await http.get(`/zsjos/payment-refund/${id}`)),
  refreshPaymentRefund: async (id: number) => unwrap<PaymentRefund>(await http.post(`/zsjos/payment-refund/${id}/refresh`)),
  submitSalesOrder: async (leadId: number, data: SalesOrderSubmitRequest) =>
    unwrap<number>(
      await http.post(`/zsjos/sales-order/lead/${leadId}/submit`, data),
    ),
  submitSystemRepurchase: async (
    leadId: number,
    repurchaseReason: string,
    order: SalesOrderSubmitRequest,
  ) =>
    unwrap<number>(
      await http.post(`/zsjos/sales-order/lead/${leadId}/repurchase`, {
        repurchaseReason,
        order,
      }),
    ),
  submitExternalRepurchase: async (data: {
    customerName: string;
    customerMobile?: string;
    customerWechatId?: string;
    repurchaseReason: string;
    order: SalesOrderSubmitRequest;
  }) =>
    unwrap<number>(
      await http.post("/zsjos/sales-order/external-repurchase", data),
    ),
  submitStudentRepurchase: async (
    personId: number,
    data: {
      customerName?: string;
      customerMobile?: string;
      customerWechatId?: string;
      repurchaseReason: string;
      order: SalesOrderSubmitRequest;
    },
  ) =>
    unwrap<number>(
      await http.post(
        `/zsjos/sales-order/student/${personId}/repurchase`,
        data,
      ),
    ),
  customerSalesOrders: async (leadId: number) =>
    unwrap<SalesOrderListItem[]>(
      await http.get(`/zsjos/sales-order/lead/${leadId}/customer-orders`),
    ),
  customerSalesOrder: async (leadId: number, orderId: number) =>
    unwrap<SalesOrder>(
      await http.get(
        `/zsjos/sales-order/lead/${leadId}/customer-orders/${orderId}`,
      ),
    ),
  resubmitSalesOrder: async (orderId: number, data: SalesOrderSubmitRequest) =>
    unwrap<number>(
      await http.put(`/zsjos/sales-order/${orderId}/resubmit`, data),
    ),
  salesOrder: async (orderId: number) =>
    unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/${orderId}`)),
  mySalesOrder: async (orderId: number) =>
    unwrap<SalesOrder>(await http.get(`/zsjos/sales-order/my/${orderId}`)),
  mySalesOrderPage: async (params: {
    pageNo: number;
    pageSize: number;
    status?: SalesOrder["status"];
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<SalesOrderListItem>>(
          await http.post("/zsjos/sales-order/my-search-page", params),
        )
      : unwrap<PageResult<SalesOrderListItem>>(
          await http.get("/zsjos/sales-order/my-page", { params }),
        ),
  mySalesOrderCursor: async (params: {
    cursor?: string;
    limit?: number;
    status?: SalesOrder["status"];
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.post("/zsjos/sales-order/my-search-cursor", params),
        )
      : unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.get("/zsjos/sales-order/my-cursor", { params }),
        ),
  mySalesOrderStatusCounts: async () =>
    unwrap<SalesOrderStatusCounts>(
      await http.get("/zsjos/sales-order/my-status-counts"),
    ),
  teamSalesOrderCursor: async (params: {
    cursor?: string;
    limit?: number;
    status?: SalesOrder["status"];
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.post("/zsjos/sales-order/team-search-cursor", params),
        )
      : unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.get("/zsjos/sales-order/team-cursor", { params }),
        ),
  teamSalesOrderStatusCounts: async () =>
    unwrap<SalesOrderStatusCounts>(
      await http.get("/zsjos/sales-order/team-status-counts"),
    ),
  salesOrderApprovalFilterProfile: async () =>
    unwrap<SalesOrderApprovalFilterProfile>(
      await http.get("/zsjos/sales-order/approval/filter-profile"),
    ),
  salesOrderApprovalTaskTarget: async (taskId: string, view: "todo" | "done" = "todo") =>
    unwrap<SalesOrderApprovalTaskTarget>(
      await http.get("/zsjos/sales-order/approval/task-target", {
        params: { taskId, view },
      }),
    ),
  bpmBusinessTaskTarget: async (taskId: string, view: "todo" | "done") =>
    unwrap<BpmBusinessTaskTarget>(
      await http.get("/zsjos/bpm/business-task-target", {
        params: { taskId, view },
      }),
    ),
  salesOrderApprovalNotificationTarget: async (
    orderId: number,
    sceneCode: string,
    sourceEventKey?: string,
  ) =>
    unwrap<SalesOrderApprovalTaskTarget>(
      await http.get("/zsjos/sales-order/approval/notification-target", {
        params: { orderId, sceneCode, sourceEventKey },
      }),
    ),
  salesOrderApprovalInbox: async (params: {
    pageNo: number;
    pageSize: number;
    center?: "registration" | "finance";
    groupKey?: string;
    optionKey?: string;
    keyword?: string;
    handled?: boolean;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<SalesOrderListItem>>(
          await http.post("/zsjos/sales-order/approval/search-page", params),
        )
      : unwrap<PageResult<SalesOrderListItem>>(
          await http.get("/zsjos/sales-order/approval/inbox-page", { params }),
        ),
  salesOrderApprovalCursor: async (params: {
    cursor?: string;
    limit?: number;
    center?: "registration" | "finance";
    groupKey?: string;
    optionKey?: string;
    keyword?: string;
    handled?: boolean;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.post("/zsjos/sales-order/approval/search-cursor", params),
        )
      : unwrap<CursorPageResult<SalesOrderListItem>>(
          await http.get("/zsjos/sales-order/approval/inbox-cursor", {
            params,
          }),
        ),
  advancedFilterCatalog: async (scene: AdvancedFilterScene) =>
    unwrap<AdvancedFilterCatalog>(
      await http.get("/zsjos/advanced-filter/catalog", { params: { scene } }),
    ),
  decideSalesOrder: async (
    orderId: number,
    decision: "approve" | "reject",
    data: {
      taskId: string;
      reason: string;
      approvalRoundId: number;
      orderVersion: number;
      roundVersion: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/sales-order/${orderId}/${decision}`, data),
    ),
  requestSalesOrderSupervisor: async (
    orderId: number,
    data: {
      taskId: string;
      reason: string;
      approvalRoundId: number;
      orderVersion: number;
      roundVersion: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(
        `/zsjos/sales-order/${orderId}/supervisor-confirmation/request`,
        data,
      ),
    ),
  salesOrderSupervisorInbox: async (params: {
    pageNo: number;
    pageSize: number;
    handled?: boolean;
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<SalesOrderSupervisorInboxItem>>(
          await http.post(
            "/zsjos/sales-order/supervisor-confirmation/search-page",
            params,
          ),
        )
      : unwrap<PageResult<SalesOrderSupervisorInboxItem>>(
          await http.get(
            "/zsjos/sales-order/supervisor-confirmation/inbox-page",
            { params },
          ),
        ),
  salesOrderSupervisorCursor: async (params: {
    cursor?: string;
    limit?: number;
    handled?: boolean;
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<CursorPageResult<SalesOrderSupervisorInboxItem>>(
          await http.post(
            "/zsjos/sales-order/supervisor-confirmation/search-cursor",
            params,
          ),
        )
      : unwrap<CursorPageResult<SalesOrderSupervisorInboxItem>>(
          await http.get(
            "/zsjos/sales-order/supervisor-confirmation/inbox-cursor",
            { params },
          ),
        ),
  salesOrderSupervisorConfirmation: async (confirmationId: number) =>
    unwrap<SalesOrderSupervisorInboxItem>(
      await http.get(
        `/zsjos/sales-order/supervisor-confirmation/${confirmationId}`,
      ),
    ),
  decideSalesOrderSupervisor: async (
    orderId: number,
    decision: "confirm" | "reject",
    data: {
      confirmationId: number;
      taskId: string;
      reason: string;
      approvalRoundId: number;
      orderVersion: number;
      roundVersion: number;
      confirmationVersion: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(
        `/zsjos/sales-order/${orderId}/supervisor-confirmation/${decision}`,
        data,
      ),
    ),
  terminateSalesOrder: async (
    orderId: number,
    data: {
      reason: string;
      approvalRoundId: number;
      orderVersion: number;
      roundVersion: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/sales-order/${orderId}/terminate`, data),
    ),
  uploadSalesOrderVoucher: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<SalesOrderVoucher>(
      await http.post("/zsjos/sales-order/voucher/upload", data),
    );
  },
  businessTaskSummary: async () =>
    unwrap<BusinessTaskSummary>(
      await http.get("/zsjos/business-task/my-summary"),
    ),
  businessTaskPage: async (
    bucket: BusinessTaskBucket,
    params: { pageNo: number; pageSize: number },
  ) =>
    unwrap<PageResult<BusinessTask>>(
      await http.get("/zsjos/business-task/my-page", {
        params: { bucket, ...params },
      }),
    ),
  businessTaskList: async (params: {
    status: "pending" | "done";
    bucket?: BusinessTaskBucket;
    pageNo: number;
    pageSize: number;
  }) =>
    unwrap<PageResult<BusinessTask>>(
      await http.get("/zsjos/business-task/my-task-page", { params }),
    ),
  completeBirthdayCare: async (id: number) =>
    unwrap<boolean>(
      await http.post(`/zsjos/business-task/${id}/complete-birthday-care`),
    ),
  createExportTask: async (
    exportType: ExportTask["exportType"],
    filter: unknown,
  ) =>
    unwrap<number>(
      await http.post("/zsjos/export-task", {
        exportType,
        filterJson: JSON.stringify(filter || {}),
      }),
    ),
  exportTaskPage: async (params: {
    pageNo: number;
    pageSize: number;
    exportType?: ExportTask["exportType"];
  }) =>
    unwrap<PageResult<ExportTask>>(
      await http.get("/zsjos/export-task/page", { params }),
    ),
  cancelExportTask: async (id: number) =>
    unwrap<boolean>(await http.post(`/zsjos/export-task/${id}/cancel`)),
  exportDownloadUrl: async (id: number) =>
    unwrap<string>(await http.get(`/zsjos/export-task/${id}/download-url`)),
  bpmTaskPage: async (
    view: "todo" | "done",
    params: { pageNo: number; pageSize: number },
  ) =>
    unwrap<PageResult<BpmTask>>(
      await http.get(`/bpm/task/${view}-page`, { params }),
    ),
  approveBpmTask: async (id: string, reason: string) =>
    unwrap<boolean>(
      await http.put("/bpm/task/approve", { id, reason, variables: {} }),
    ),
  rejectBpmTask: async (id: string, reason: string) =>
    unwrap<boolean>(await http.put("/bpm/task/reject", { id, reason })),
  simpleUsers: async () =>
    unwrap<SimpleUser[]>(await http.get("/system/user/simple-list")),
  simpleDepartments: async () =>
    unwrap<SimpleDept[]>(await http.get("/system/dept/simple-list")),
  workPlanPage: async (params: {
    pageNo: number;
    pageSize: number;
    periodType?: WorkPlan["periodType"];
    status?: string;
    startDate?: string;
    endDate?: string;
  }) =>
    unwrap<PageResult<WorkPlan>>(
      await http.get("/zsjos/work-plan/page", { params }),
    ),
  workPlan: async (id: number) =>
    unwrap<WorkPlan>(
      await http.get("/zsjos/work-plan/get", { params: { id } }),
    ),
  workTask: async (id: number) =>
    unwrap<WorkTask>(
      await http.get("/zsjos/work-plan/task/get", { params: { id } }),
    ),
  myWorkTaskPage: async (params: {
    pageNo: number;
    pageSize: number;
    status?: string;
  }) =>
    unwrap<PageResult<WorkTask>>(
      await http.get("/zsjos/work-plan/task/my-page", { params }),
    ),
  createWorkPlan: async (data: WorkPlanInput) =>
    unwrap<number>(await http.post("/zsjos/work-plan/create", data)),
  updateWorkPlan: async (id: number, data: WorkPlanInput) =>
    unwrap<boolean>(await http.put(`/zsjos/work-plan/${id}`, data)),
  publishWorkPlan: async (id: number, version: number) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan/${id}/publish`, { version }),
    ),
  cancelWorkPlan: async (id: number, version: number, reason: string) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan/${id}/cancel`, { version, reason }),
    ),
  createTemporaryTask: async (data: WorkTaskInput) =>
    unwrap<number>(await http.post("/zsjos/work-plan/task/temporary", data)),
  addWorkTask: async (planId: number, data: WorkTaskInput) =>
    unwrap<number>(await http.post(`/zsjos/work-plan/${planId}/task`, data)),
  adjustWorkTask: async (id: number, data: WorkTaskInput) =>
    unwrap<boolean>(await http.put(`/zsjos/work-plan/task/${id}`, data)),
  submitWorkReport: async (
    id: number,
    data: {
      completionSummary: string;
      infraFileIds: number[];
      version: number;
      reportFields?: Record<string, unknown>;
    },
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan/task/${id}/report`, data),
    ),
  uploadWorkPlanAttachment: async (file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<WorkPlanAttachmentUpload>(
      await http.post("/zsjos/work-plan/attachment/upload", data),
    );
  },
  confirmWorkReport: async (
    id: number,
    data: {
      decision: "confirmed" | "returned";
      comment?: string;
      version: number;
    },
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan/task/${id}/confirm`, data),
    ),
  cancelWorkTask: async (
    id: number,
    data: { version: number; reason: string; cascadeChildren?: boolean },
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan/task/${id}/cancel`, data),
    ),
  submitWorkPlanSummary: async (
    id: number,
    data: {
      version: number;
      summary: string;
      infraFileIds: number[];
      summaryFields?: Record<string, unknown>;
    },
  ) => unwrap<boolean>(await http.post(`/zsjos/work-plan/${id}/summary`, data)),
  workPlanTypes: async () =>
    unwrap<WorkPlanType[]>(await http.get("/zsjos/work-plan-config/types")),
  workPlanTemplates: async () =>
    unwrap<WorkPlanTemplate[]>(
      await http.get("/zsjos/work-plan/templates/available"),
    ),
  workPlanConfigTemplates: async (typeId?: number) =>
    unwrap<WorkPlanTemplate[]>(
      await http.get("/zsjos/work-plan-config/templates", {
        params: { typeId },
      }),
    ),
  copyWorkPlanTemplateVersion: async (id: number) =>
    unwrap<number>(
      await http.post(`/zsjos/work-plan-config/templates/${id}/versions/copy`),
    ),
  publishWorkPlanTemplate: async (id: number) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan-config/templates/${id}/publish`),
    ),
  disableWorkPlanTemplate: async (id: number) =>
    unwrap<boolean>(
      await http.post(`/zsjos/work-plan-config/templates/${id}/disable`),
    ),
  leadAssignmentRule: async () =>
    unwrap<LeadAssignmentRule>(
      await http.get("/zsjos/lead/assignment-rule/get"),
    ),
  updateLeadAssignmentRule: async (
    data: Pick<
      LeadAssignmentRule,
      "acceptTimeoutSeconds" | "maxAttempts" | "dailyClaimLimit"
    >,
  ) =>
    unwrap<boolean>(await http.put("/zsjos/lead/assignment-rule/update", data)),
  leadFollowUpRule: async () =>
    unwrap<LeadFollowUpRule>(await http.get("/zsjos/lead-follow-up-rule/get")),
  leadRuntimeSetting: async () =>
    unwrap<{ notificationPopupDurationMinutes: number }>(
      await http.get("/zsjos/lead-follow-up-rule/runtime-setting"),
    ),
  updateLeadFollowUpRule: async (
    data: Pick<
      LeadFollowUpRule,
      | "version"
      | "firstFollowUpTimeoutMinutes"
      | "qualificationTimeoutMinutes"
      | "agingPoolTimeoutDays"
      | "noProgressWarningDays"
      | "noProgressGraceDays"
      | "notificationPopupDurationMinutes"
      | "duplicateAutoResolutionEnabled"
    >,
  ) =>
    unwrap<boolean>(await http.put("/zsjos/lead-follow-up-rule/update", data)),
  leadFilterConfig: async (audience: LeadFilterAudience) =>
    unwrap<LeadFilterAdmin>(
      await http.get("/zsjos/lead/inbox-filter/get", { params: { audience } }),
    ),
  leadFilterVersions: async (audience: LeadFilterAudience) =>
    unwrap<LeadFilterVersion[]>(
      await http.get("/zsjos/lead/inbox-filter/versions", {
        params: { audience },
      }),
    ),
  publishLeadFilter: async (audience: LeadFilterAudience) =>
    unwrap<number>(
      await http.post("/zsjos/lead/inbox-filter/publish", undefined, {
        params: { audience },
      }),
    ),
  rollbackLeadFilter: async (audience: LeadFilterAudience, versionNo: number) =>
    unwrap<number>(
      await http.post("/zsjos/lead/inbox-filter/rollback", undefined, {
        params: { audience, versionNo },
      }),
    ),
  saveLeadFilterDraft: async (
    audience: LeadFilterAudience,
    groups: LeadFilterGroupConfig[],
  ) =>
    unwrap<boolean>(
      await http.put("/zsjos/lead/inbox-filter/draft", { audience, groups }),
    ),
  productConfigPage: async (params: {
    pageNo: number;
    pageSize: number;
    name?: string;
    status?: number;
  }) =>
    unwrap<PageResult<ProductConfig>>(
      await http.get("/zsjos/product/page", { params }),
    ),
  productConfig: async (id: number) =>
    unwrap<ProductSaveRequest & { id: number }>(
      await http.get("/zsjos/product/get", { params: { id } }),
    ),
  createProductConfig: async (data: ProductSaveRequest) =>
    unwrap<number>(await http.post("/zsjos/product/create", data)),
  updateProductConfig: async (data: ProductSaveRequest & { id: number }) =>
    unwrap<boolean>(await http.put("/zsjos/product/update", data)),
  deleteProductConfig: async (id: number) =>
    unwrap<boolean>(
      await http.delete("/zsjos/product/delete", { params: { id } }),
    ),
  productCategoryTree: async () =>
    unwrap<ProductCategory[]>(await http.get("/zsjos/product/category/tree")),
  createProductCategory: async (data: ProductCategorySaveRequest) =>
    unwrap<number>(await http.post("/zsjos/product/category/create", data)),
  updateProductCategory: async (
    data: ProductCategorySaveRequest & { id: number },
  ) => unwrap<boolean>(await http.put("/zsjos/product/category/update", data)),
  updateProductConfigStatus: async (id: number, status: number) =>
    unwrap<boolean>(
      await http.put("/zsjos/product/update-status", { id, status }),
    ),
  productSkus: async (spuId: number) =>
    unwrap<ProductSku[]>(
      await http.get("/zsjos/product/sku/list", { params: { spuId } }),
    ),
  createProductSku: async (data: ProductSkuSaveRequest) =>
    unwrap<number>(await http.post("/zsjos/product/sku/create", data)),
  updateProductSku: async (data: ProductSkuSaveRequest & { id: number }) =>
    unwrap<boolean>(await http.put("/zsjos/product/sku/update", data)),
  deleteProductSku: async (id: number) =>
    unwrap<boolean>(
      await http.delete("/zsjos/product/sku/delete", { params: { id } }),
    ),
  updateProductSkuStatus: async (id: number, status: number) =>
    unwrap<boolean>(
      await http.put("/zsjos/product/sku/update-status", { id, status }),
    ),
  productAttributes: async (spuId: number) =>
    unwrap<ProductAttribute[]>(
      await http.get("/zsjos/product/sku/attrs", { params: { spuId } }),
    ),
  saveProductAttributes: async (spuId: number, attrs: ProductAttribute[]) =>
    unwrap<boolean>(
      await http.put("/zsjos/product/sku/attrs", { spuId, attrs }),
    ),
  createWorkPlanTemplate: async (data: WorkPlanTemplateSaveRequest) =>
    unwrap<number>(await http.post("/zsjos/work-plan-config/templates", data)),
  updateWorkPlanTemplate: async (
    id: number,
    data: WorkPlanTemplateSaveRequest,
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/work-plan-config/templates/${id}`, data),
    ),
  subordinateSalesPage: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    accountStatus?: number;
    presence?: string;
    accepting?: boolean;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<SubordinateSales>>(
          await http.post("/zsjos/subordinate-sales/search-page", params),
        )
      : unwrap<PageResult<SubordinateSales>>(
          await http.get("/zsjos/subordinate-sales/page", { params }),
        ),
  subordinatePartners: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    status?: string;
  }) =>
    unwrap<PageResult<SubordinatePartner>>(
      await http.get("/zsjos/subordinate-partners/page", { params }),
    ),
  subordinatePartnerLeads: async (
    partnerId: number,
    params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
      status?: string;
    },
  ) =>
    unwrap<PageResult<ManagedLead>>(
      await http.get(`/zsjos/subordinate-partners/${partnerId}/leads/page`, {
        params,
      }),
    ),
  subordinatePartnerLead: async (leadId: number) =>
    unwrap<ManagedLead>(
      await http.get(`/zsjos/subordinate-partners/leads/${leadId}`),
    ),
  subordinateSalesOverview: async (salesUserId: number) =>
    unwrap<SubordinateSales>(
      await http.get(`/zsjos/subordinate-sales/${salesUserId}/overview`),
    ),
  subordinateSalesLeads: async (
    salesUserId: number,
    params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
      status?: string;
      advancedFilter?: AdvancedFilterGroup;
    },
  ) =>
    params.advancedFilter
      ? unwrap<PageResult<ManagedLead>>(
          await http.post(
            `/zsjos/subordinate-sales/${salesUserId}/leads/search-page`,
            params,
          ),
        )
      : unwrap<PageResult<ManagedLead>>(
          await http.get(`/zsjos/subordinate-sales/${salesUserId}/leads`, {
            params,
          }),
        ),
  subordinateSalesTasks: async (
    salesUserId: number,
    params: { pageNo: number; pageSize: number; bucket?: BusinessTaskBucket },
  ) =>
    unwrap<PageResult<SubordinateTask>>(
      await http.get(`/zsjos/subordinate-sales/${salesUserId}/tasks`, {
        params,
      }),
    ),
  subordinateTransferCandidates: async () =>
    unwrap<AssignmentUser[]>(
      await http.get("/zsjos/subordinate-sales/transfer-candidates"),
    ),
  updateSubordinateAccountStatus: async (
    salesUserId: number,
    status: number,
    reason: string,
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/subordinate-sales/${salesUserId}/account-status`, {
        status,
        reason,
      }),
    ),
  updateSubordinateDispatchMode: async (
    salesUserId: number,
    accepting: boolean,
    reason: string,
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/subordinate-sales/${salesUserId}/dispatch-mode`, {
        accepting,
        reason,
      }),
    ),
  pauseAllSubordinateDispatch: async () =>
    unwrap<SubordinatePauseAllResult>(
      await http.put("/zsjos/subordinate-sales/dispatch-mode/pause-all"),
    ),
  supervisorTransferLead: async (
    id: number,
    targetUserId: number,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/subordinate-sales/leads/${id}/transfer`, {
        targetUserId,
        reason,
        idempotencyKey,
      }),
    ),
  supervisorRestoreLead: async (
    id: number,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/subordinate-sales/leads/${id}/restore`, {
        reason,
        idempotencyKey,
      }),
    ),
  supervisorRecycleLead: async (
    id: number,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/subordinate-sales/leads/${id}/recycle`, {
        reason,
        idempotencyKey,
      }),
    ),
  supervisorReleaseClaimPoolLead: async (
    id: number,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/subordinate-sales/leads/${id}/release-claim-pool`,
        { reason, idempotencyKey },
      ),
    ),
  supervisorReleasePublicSeaLead: async (
    id: number,
    collaboratorUserId: number | undefined,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/subordinate-sales/leads/${id}/release-public-sea`,
        { collaboratorUserId, reason, idempotencyKey },
      ),
    ),
  batchSupervisorLeadAction: async (
    action:
      | "transfer"
      | "restore"
      | "recycle"
      | "release-claim-pool"
      | "release-public-sea",
    leadIds: number[],
    data: {
      reason: string;
      targetUserId?: number;
      collaboratorUserId?: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<SubordinateBatchResult>(
      await http.post(`/zsjos/subordinate-sales/leads/batch-${action}`, {
        leadIds,
        ...data,
      }),
    ),
  unreadNotifyCount: async () =>
    unwrap<number>(await http.get("/system/notify-message/get-unread-count")),
  unreadNotifyMessages: async () =>
    unwrap<NotifyMessage[]>(
      await http.get("/system/notify-message/get-unread-list"),
    ),
  myNotifyMessagePage: async (params: NotifyMessagePageParams) =>
    unwrap<PageResult<NotifyMessage>>(
      await http.get("/system/notify-message/my-page", { params }),
    ),
  myNotifyMessageCursor: async (params: NotifyMessageCursorParams) =>
    unwrap<CursorPageResult<NotifyMessage>>(
      await http.get("/system/notify-message/my-cursor", { params }),
    ),
  myNotifyMessage: async (id: number) =>
    unwrap<NotifyMessage>(
      await http.get("/system/notify-message/my-get", { params: { id } }),
    ),
  markNotifyMessagesRead: async (ids: number[]) => {
    const params = new URLSearchParams();
    ids.forEach((id) => params.append("ids", String(id)));
    return unwrap<boolean>(
      await http.put("/system/notify-message/update-read", undefined, {
        params,
      }),
    );
  },
  markAllNotifyMessagesRead: async () =>
    unwrap<boolean>(await http.put("/system/notify-message/update-all-read")),
  announcementPage: async (params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<Announcement>>(
      await http.get("/system/notice/my-page", { params }),
    ),
  announcement: async (id: number) =>
    unwrap<Announcement>(
      await http.get("/system/notice/my-get", { params: { id } }),
    ),
  announcementUnreadSummary: async () =>
    unwrap<AnnouncementUnreadSummary>(
      await http.get("/system/notice/unread-summary"),
    ),
  forcedFormsPending: async () =>
    unwrap<ForcedForm[]>(await http.get("/zsjos/forced-form/pending")),
  forcedFormStatus: async () =>
    unwrap<ForcedFormStatus>(await http.get("/zsjos/forced-form/status")),
  forcedForm: async (id: number) =>
    unwrap<ForcedForm>(await http.get(`/zsjos/forced-form/${id}`)),
  forcedFormRuntime: async (id: number) =>
    unwrap<ForcedFormRuntime>(await http.get(`/zsjos/forced-form/${id}/runtime`)),
  uploadForcedFormAttachment: async (
    id: number,
    fieldKey: string,
    file: File,
  ) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<ForcedFormAttachmentUploadResult>(
      await http.post(`/zsjos/forced-form/${id}/attachment/upload`, data, {
        params: { fieldKey },
      }),
    );
  },
  submitForcedForm: async (
    id: number,
    payload: { answersJson: string; platform: string },
  ) =>
    unwrap<boolean>(await http.post(`/zsjos/forced-form/${id}/submit`, payload)),
  markAnnouncementRead: async (id: number) =>
    unwrap<boolean>(
      await http.put("/system/notice/mark-read", undefined, { params: { id } }),
    ),
  salesUsers: async () =>
    unwrap<SalesUser[]>(await http.get("/zsjos/lead/sales-user/simple-list")),
  assignmentRelationPage: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    configured?: boolean;
  }) =>
    unwrap<PageResult<AssignmentRelation>>(
      await http.get("/zsjos/lead-assignment/relation/page", { params }),
    ),
  eligibleSalesUsers: async () =>
    unwrap<AssignmentUser[]>(
      await http.get("/zsjos/lead-assignment/eligible-sales"),
    ),
  saveAssignmentRelations: async (data: {
    sourceUserIds: number[];
    targetUserIds: number[];
    mode: "append" | "replace" | "remove";
  }) =>
    unwrap<boolean>(
      await http.put("/zsjos/lead-assignment/relation/save", data),
    ),
  assignmentLogPage: async (params: { pageNo: number; pageSize: number }) =>
    unwrap<PageResult<AssignmentLog>>(
      await http.get("/zsjos/lead-assignment/log/page", { params }),
    ),
  registrationPoolPage: async (params: {
    pageNo: number;
    pageSize: number;
    status?: string;
    keyword?: string;
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<RegistrationCase>>(
          await http.post("/zsjos/registration/pool/search-page", params),
        )
      : unwrap<PageResult<RegistrationCase>>(
          await http.get("/zsjos/registration/pool-page", { params }),
        ),
  registrationCase: async (id: number) =>
    unwrap<RegistrationCase>(await http.get(`/zsjos/registration/${id}`)),
  registrationPlannerCandidates: async () =>
    unwrap<StudyPlanner[]>(
      await http.get("/zsjos/registration/study-planner-candidates"),
    ),
  registrationRouteCandidates: async (id: number, routeId: number) =>
    unwrap<StudyPlanner[]>(
      await http.get(`/zsjos/registration/${id}/routes/${routeId}/candidates`),
    ),
  updateRegistrationItem: async (
    id: number,
    itemId: number,
    data: { checked: boolean; version: number; idempotencyKey: string },
  ) =>
    unwrap<RegistrationCase>(
      await http.put(`/zsjos/registration/${id}/items/${itemId}`, data),
    ),
  updateRegistrationPlanner: async (
    id: number,
    data: {
      studyPlannerUserId: number;
      version: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<RegistrationCase>(
      await http.put(`/zsjos/registration/${id}/study-planner`, data),
    ),
  updateRegistrationRoutes: async (
    id: number,
    data: {
      version: number;
      idempotencyKey: string;
      routes: Array<{
        routeId: number;
        selected: boolean;
        assigneeUserId?: number;
      }>;
    },
  ) =>
    unwrap<RegistrationCase>(
      await http.put(`/zsjos/registration/${id}/routes`, data),
    ),
  uploadRegistrationAttachment: async (
    id: number,
    itemId: number,
    file: File,
    version: number,
  ) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<{
      id: number;
      infraFileId: number;
      fileUrl: string;
      originalName: string;
      contentType?: string;
      fileSize: number;
      version: number;
    }>(
      await http.post(
        `/zsjos/registration/${id}/items/${itemId}/attachments`,
        data,
        { params: { version, idempotencyKey: createIdempotencyKey() } },
      ),
    );
  },
  deleteRegistrationAttachment: async (
    id: number,
    itemId: number,
    attachmentId: number,
    data: { version: number; idempotencyKey: string },
  ) =>
    unwrap<RegistrationCase>(
      await http.delete(
        `/zsjos/registration/${id}/items/${itemId}/attachments/${attachmentId}`,
        { data },
      ),
    ),
  completeRegistration: async (
    id: number,
    data: { version: number; idempotencyKey: string },
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/registration/${id}/complete`, data),
    ),
  closeRegistration: async (
    id: number,
    data: { version: number; idempotencyKey: string; reason: string },
  ) =>
    unwrap<boolean>(await http.post(`/zsjos/registration/${id}/close`, data)),
  myStudents: async (params: {
    pageNo: number;
    pageSize: number;
    keyword?: string;
    serviceStatus?: "active" | "paused" | "completed";
    advancedFilter?: AdvancedFilterGroup;
  }) =>
    params.advancedFilter
      ? unwrap<PageResult<MyStudent>>(
          await http.post("/zsjos/student/my/search-page", params),
        )
      : unwrap<PageResult<MyStudent>>(
          await http.get("/zsjos/student/my-page", { params }),
        ),
  mediaStudents: {
    page: async (params: {
      pageNo: number;
      pageSize: number;
      keyword?: string;
    }) =>
      unwrap<PageResult<MyStudent>>(
        await http.get("/zsjos/media-students/page", { params }),
      ),
    get: async (personId: number) =>
      unwrap<MediaStudentDetail>(
        await http.get(`/zsjos/media-students/${personId}`),
      ),
    target: async (bizType: string, bizId: number) =>
      unwrap<{ personId: number; targetTab: string; recordId: number }>(
        await http.get("/zsjos/media-students/target", {
          params: { bizType, bizId },
        }),
      ),
    talks: async (personId: number) =>
      unwrap<MediaStudentTalkRecord[]>(
        await http.get(`/zsjos/media-students/${personId}/talk-records`),
      ),
    createTalk: async (
      personId: number,
      data: {
        accountId?: number;
        content: string;
        attachmentFileIds?: number[];
      },
    ) =>
      unwrap<number>(
        await http.post(`/zsjos/media-students/${personId}/talk-records`, data),
      ),
  },
  myStudent: async (personId: number) =>
    unwrap<MyStudent>(await http.get(`/zsjos/student/my/${personId}`)),
  myStudentByService: async (relationId: number) =>
    unwrap<MyStudent>(
      await http.get(`/zsjos/student/my/by-service/${relationId}`),
    ),
  studentContactContext: async (relationId: number) =>
    unwrap<StudentContactContext>(
      await http.get(`/zsjos/student/service/${relationId}/contact-context`),
    ),
  studentContactRecords: async (
    relationId: number,
    pageNo = 1,
    pageSize = 100,
  ) =>
    unwrap<PageResult<StudentContactRecord>>(
      await http.get(`/zsjos/student/service/${relationId}/contact-records`, {
        params: { pageNo, pageSize },
      }),
    ),
  studentAccept: async (
    relationId: number,
    version: number,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(`/zsjos/student/service/${relationId}/accept`, {
        version,
        idempotencyKey,
      }),
    ),
  studentUpdateBasicInfo: async (
    relationId: number,
    data: { name: string; mobile?: string; wechatId?: string; reason: string },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/student/service/${relationId}/basic-info`, data),
    ),
  studentFirstContact: async (
    relationId: number,
    data: Record<string, unknown>,
  ) =>
    unwrap<number>(
      await http.post(
        `/zsjos/student/service/${relationId}/first-contact`,
        data,
      ),
    ),
  studentStudyPlan: async (relationId: number, data: Record<string, unknown>) =>
    unwrap<number>(
      await http.post(`/zsjos/student/service/${relationId}/study-plan`, data),
    ),
  studentContact: async (relationId: number, data: Record<string, unknown>) =>
    unwrap<number>(
      await http.post(`/zsjos/student/service/${relationId}/contacts`, data),
    ),
  studentDeliveryStage: async (
    relationId: number,
    data: {
      stage: string;
      successful: boolean;
      remark: string;
      attachmentFileIds?: number[];
      data?: Record<string, unknown>;
      idempotencyKey: string;
    },
  ) =>
    unwrap<number>(
      await http.post(
        `/zsjos/student/service/${relationId}/delivery-stage`,
        data,
      ),
    ),
  studentExamDate: async (
    relationId: number,
    data: { examDate: string; version: number; idempotencyKey: string },
  ) =>
    unwrap<boolean>(
      await http.put(`/zsjos/student/service/${relationId}/exam-date`, data),
    ),
  studentDirectorPrecheckDraft: async (
    relationId: number,
    data: {
      interviewAt?: string;
      data: Record<string, unknown>;
      version: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<number>(
      await http.post(
        `/zsjos/student/service/${relationId}/precheck/draft`,
        data,
      ),
    ),
  studentDirectorPrecheckSubmit: async (
    relationId: number,
    data: {
      interviewAt?: string;
      data: Record<string, unknown>;
      version: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/student/service/${relationId}/precheck/submit`,
        data,
      ),
    ),
  studentDirectorInterviewDraft: async (
    relationId: number,
    data: {
      interviewAt?: string;
      data: Record<string, unknown>;
      version: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<number>(
      await http.post(
        `/zsjos/student/service/${relationId}/interview/draft`,
        data,
      ),
    ),
  studentDirectorInterviewSubmit: async (
    relationId: number,
    data: {
      interviewAt?: string;
      data: Record<string, unknown>;
      version: number;
      idempotencyKey: string;
    },
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/student/service/${relationId}/interview/submit`,
        data,
      ),
    ),
  studentCollaboratorCandidates: async (
    relationId: number,
    type: "content_director" | "career_planner" | "operator",
  ) =>
    unwrap<StudyPlanner[]>(
      await http.get(
        `/zsjos/student/service/${relationId}/collaborator-candidates`,
        { params: { type } },
      ),
    ),
  studentAssignCollaborator: async (
    relationId: number,
    data: {
      collaboratorType: string;
      userId: number;
      version: number;
      idempotencyKey: string;
      correctionReason?: string;
    },
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/student/service/${relationId}/collaborators`,
        data,
      ),
    ),
  studentContactUpload: async (relationId: number, file: File) => {
    const data = new FormData();
    data.append("file", file);
    return unwrap<{
      fileId: number;
      name: string;
      url: string;
      contentType?: string;
      size: number;
    }>(
      await http.post(`/zsjos/student/service/${relationId}/attachments`, data),
    );
  },
  studentContactExtensions: async (
    pageNo = 1,
    pageSize = 20,
    statusScope = "all",
  ) =>
    unwrap<PageResult<StudentContactExtension>>(
      await http.get("/zsjos/student/service/extensions", {
        params: { pageNo, pageSize, statusScope },
      }),
    ),
  studentWithdrawExtension: async (
    extensionId: number,
    version: number,
    reason: string,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post(
        `/zsjos/student/service/extensions/${extensionId}/withdraw`,
        { version, reason, idempotencyKey },
      ),
    ),
  studentCompleteAssistance: async (taskId: number, remark: string) =>
    unwrap<boolean>(
      await http.post(`/zsjos/student/service/assistance/${taskId}/complete`, {
        remark,
      }),
    ),
  studentContactConfig: async () =>
    unwrap<StudentContactConfig>(
      await http.get("/zsjos/student-contact-config"),
    ),
  copyStudentContactConfigDraft: async (
    publishedId: number,
    publishedVersion: number,
    idempotencyKey: string,
  ) =>
    unwrap<number>(
      await http.post("/zsjos/student-contact-config/draft/copy", {
        publishedId,
        publishedVersion,
        idempotencyKey,
      }),
    ),
  saveStudentContactConfigDraft: async (data: Record<string, unknown>) =>
    unwrap<boolean>(
      await http.put("/zsjos/student-contact-config/draft", data),
    ),
  publishStudentContactConfig: async (
    id: number,
    version: number,
    idempotencyKey: string,
  ) =>
    unwrap<boolean>(
      await http.post("/zsjos/student-contact-config/publish", {
        id,
        version,
        idempotencyKey,
      }),
    ),
  registrationChecklistConfig: async () =>
    unwrap<RegistrationChecklistConfig>(
      await http.get("/zsjos/registration-checklist-config"),
    ),
  copyRegistrationChecklistDraft: async (version: number) =>
    unwrap<number>(
      await http.post("/zsjos/registration-checklist-config/draft/copy", {
        version,
        idempotencyKey: crypto.randomUUID(),
      }),
    ),
  saveRegistrationChecklistDraft: async (data: {
    templateVersion: number;
    items: Array<{
      id?: number;
      itemKey?: string;
      itemType: string;
      title: string;
      sort: number;
      enabled: boolean;
      systemRequired?: boolean;
      attachmentRequired?: boolean;
    }>;
    routeOptions: Array<{
      id?: number;
      optionKey: string;
      departmentId: number;
      assigneeType: string;
      sort: number;
      enabled: boolean;
      systemRequired?: boolean;
    }>;
    idempotencyKey: string;
  }) =>
    unwrap<boolean>(
      await http.put("/zsjos/registration-checklist-config/draft", data),
    ),
  publishRegistrationChecklist: async (version: number) =>
    unwrap<boolean>(
      await http.post("/zsjos/registration-checklist-config/publish", {
        version,
        idempotencyKey: crypto.randomUUID(),
      }),
    ),
};
