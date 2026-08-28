export type UserRole = 'LAWYER' | 'ADMIN' | 'CLIENT'

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED'

export type MessageRole = 'USER' | 'ASSISTANT'

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  accessToken: string
  userId: string
  email: string
  role: UserRole
}

export interface LoginResponse {
  mfaRequired: boolean
  mfaToken?: string
  accessToken?: string
  userId?: string
  email?: string
  role?: UserRole
}

export interface MfaLoginRequest {
  mfaToken: string
  code: string
}

export interface MfaStatusResponse {
  enabled: boolean
  mandatory: boolean
}

export interface MfaSetupResponse {
  secret: string
  otpauthUri: string
}

export interface SessionResponse {
  id: string
  ipAddress?: string
  userAgent?: string
  createdAt: string
  lastUsedAt?: string
  current?: boolean
}

export interface ApplyRequest {
  email: string
  password: string
  fullName: string
  specialization: string
  phone: string
  personalDataConsent: boolean
  crossBorderConsent: boolean
  marketingConsent: boolean
  consentPolicyVersion: string
}

export type ConsentPurpose =
  | 'PERSONAL_DATA'
  | 'CROSS_BORDER_TRANSFER'
  | 'ANALYTICS_COOKIES'
  | 'MARKETING'

export type SubjectRequestType = 'ACCESS' | 'ERASURE' | 'CONSENT_WITHDRAWAL'

export type SubjectRequestStatus = 'PENDING' | 'COMPLETED' | 'REJECTED'

export interface PrivacyPolicyResponse {
  policyVersion: string
  operator: string
}

export interface ConsentResponse {
  id: string
  purpose: ConsentPurpose
  mandatory: boolean
  policyVersion: string
  grantedAt: string
  revokedAt: string | null
  source: string
}

export interface SubjectRequestResponse {
  id: string
  userId: string | null
  subjectRef: string
  type: SubjectRequestType
  status: SubjectRequestStatus
  requestedAt: string
  dueAt: string
  completedAt: string | null
  note: string | null
}

export interface PersonalDataSessionRecord {
  ipAddress: string | null
  userAgent: string | null
  createdAt: string
  lastUsedAt: string | null
}

export interface PersonalDataExportResponse {
  userId: string
  email: string
  role: string
  status: string
  registeredAt: string
  fullName: string | null
  specialization: string | null
  phone: string | null
  preferredLanguage: string | null
  telegramLinked: boolean
  consents: ConsentResponse[]
  sessions: PersonalDataSessionRecord[]
  requests: SubjectRequestResponse[]
  operator: string
  exportedAt: string
}

export interface ApplicationResponse {
  id: string
  email: string
  fullName: string
  specialization: string
  phone: string
  status: ApplicationStatus
  submittedAt: string
  reviewedAt: string | null
  emailVerified: boolean
}

export interface ApplicationSubmissionResponse {
  application: ApplicationResponse
  statusToken: string
}

export interface UpdateApplicationRequest {
  email: string
  fullName: string
  password?: string
  specialization: string
  phone: string
}

export interface PageContextRef {
  route: string
  entityType?: string
  entityId?: string
}

export interface ChatRequest {
  conversationId?: string
  message: string
  attachedDocumentIds?: string[]
  caseId?: string
  documentId?: string
  pageContext?: PageContextRef
}

export interface ChatResponse {
  conversationId: string
  messageId: string
  answer: string
  sources: string[]
  followUps: string[]
  proposals?: AiActionProposal[]
}

export interface ConversationResponse {
  id: string
  title: string
  createdAt: string
  updatedAt: string
}

export interface AdminConversationResponse {
  id: string
  lawyerId: string
  orgId: string | null
  title: string
  caseId: string | null
  documentId: string | null
  createdAt: string
  updatedAt: string
}

export interface ToolStepResponse {
  name: string
  status: ToolStepStatus
  durationMs: number
}

export type ToolStepStatus = 'RUNNING' | 'OK' | 'ERROR' | 'SKIPPED'

export interface ChatToolStep {
  name: string
  status: ToolStepStatus
}

export type AiActionProposalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'FAILED'

export interface AiActionProposal {
  id: string
  conversationId: string
  messageId?: string | null
  toolName: string
  title: string
  status: AiActionProposalStatus
  arguments: Record<string, unknown>
  result?: string | null
  failureReason?: string | null
  createdAt: string
  expiresAt: string
}

export interface AiTrustedTool {
  toolName: string
  grantedAt: string
}

export interface MessageResponse {
  id: string
  role: MessageRole
  content: string
  sources?: string[]
  rating?: number | null
  createdAt: string
  toolSteps?: ToolStepResponse[]
}

export interface DocumentResponse {
  id: string
  title: string
  fileName: string
  status: DocumentStatus
  uploadedAt: string
  visibleToClient?: boolean
}

export type DocumentSummaryStatus = 'NONE' | 'PENDING' | 'READY' | 'FAILED'

export interface DocumentInsightResponse {
  documentId: string
  title: string
  status: DocumentStatus
  summaryStatus: DocumentSummaryStatus
  summary: string | null
  keyPoints: string[]
  generatedAt: string | null
}

export interface DocumentUploadResponse {
  id: string
  title: string
  fileName: string
  status: 'PROCESSING'
}

export interface LegislationResponse {
  id: string
  title: string
  actCanonical: string
  articleNumber: string
  editionDate: string
  status: DocumentStatus
  superseded: boolean
}

export interface LegislationUpload {
  file: File
  actCanonical: string
  articleNumber: string
  editionDate: string
  title?: string
}

export interface LawyerResponse {
  id: string
  email: string
  fullName: string
  specialization: string
  phone: string
}

export type CaseStatus = 'INTAKE' | 'IN_PROGRESS' | 'SUBMITTED' | 'CLOSED_WON' | 'CLOSED_LOST'

export type CourtSystem = 'ARBITR' | 'GENERAL_JURISDICTION'

export interface CaseResponse {
  id: string
  ownerId: string
  orgId: string | null
  title: string
  description: string | null
  clientId: string | null
  clientName: string | null
  status: CaseStatus
  statusName: string
  filingDeadline: string | null
  nextHearingDate: string | null
  expiresAt: string | null
  courtSystem: CourtSystem
  courtSystemName: string
  courtCaseNumber: string | null
  courtCardUrl: string | null
  defaultHourlyRate: number | null
  createdAt: string
}

export interface CaseHearingEvent {
  id: string
  eventDate: string | null
  eventType: string | null
  description: string | null
  courtName: string | null
}

export interface EventTypeCount {
  type: string
  count: number
}

export interface CaseParty {
  name: string
  role: string | null
}

export interface CaseTimelineStats {
  hearingCount: number
  firstEventDate: string | null
  lastEventDate: string | null
  spanDays: number | null
  averageIntervalDays: number | null
  courts: string[]
  eventTypes: EventTypeCount[]
  nextHearingDate: string | null
  daysToNextHearing: number | null
  judge: string | null
  parties: CaseParty[]
}

export interface OutcomeStat {
  name: string
  totalCases: number
  wonCases: number
  lostCases: number
  winRatePercent: number | null
}

export interface AiCaseAnalysisDto {
  content: string
  generatedAt: string
}

export interface CaseAnalyticsResponse {
  timeline: CaseTimelineStats
  courtStats: OutcomeStat[]
  judgeStats: OutcomeStat[]
  partyStats: OutcomeStat[]
  aiAnalysis: AiCaseAnalysisDto | null
}

export interface CreateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
  orgId?: string | null
  filingDeadline?: string | null
  nextHearingDate?: string | null
  expiresAt?: string | null
  courtCaseNumber?: string | null
  courtSystem?: CourtSystem | null
  defaultHourlyRate?: number | null
}

export interface UpdateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
  filingDeadline?: string | null
  nextHearingDate?: string | null
  expiresAt?: string | null
  courtCaseNumber?: string | null
  courtSystem?: CourtSystem | null
  defaultHourlyRate?: number | null
}

export type OrgRole = 'OWNER' | 'MANAGER' | 'MEMBER'

export interface Organization {
  id: string
  name: string
  ownerId: string
  myRole: OrgRole
  memberCount: number
  createdAt: string
}

export interface OrganizationMember {
  userId: string
  email: string | null
  fullName: string | null
  orgRole: OrgRole
  joinedAt: string
}

export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED'

export interface OrgInvite {
  id: string
  email: string
  orgRole: OrgRole
  status: InviteStatus
  expiresAt: string
  createdAt: string
}

export interface CreateOrganizationRequest {
  name: string
}

export interface CreateInviteRequest {
  email: string
  orgRole: OrgRole
}

export interface UpdateCaseStatusRequest {
  status: CaseStatus
}

export interface CaseTaskResponse {
  id: string
  caseId: string
  text: string
  dueDate: string | null
  done: boolean
  createdAt: string
}

export interface CreateCaseTaskRequest {
  text: string
  dueDate?: string | null
}

export interface UpdateCaseTaskRequest {
  text: string
  dueDate?: string | null
  done: boolean
}

export interface TimeEntryResponse {
  id: string
  caseId: string
  description: string
  activityDate: string
  minutes: number
  hourlyRate: number
  amount: number
  billable: boolean
  running: boolean
  startedAt: string | null
  invoiced: boolean
  createdAt: string
}

export interface CaseTimeSummary {
  entries: TimeEntryResponse[]
  totalMinutes: number
  billableMinutes: number
  uninvoicedBillableMinutes: number
  billableAmount: number
  uninvoicedBillableAmount: number
}

export interface CreateTimeEntryRequest {
  description: string
  activityDate: string
  minutes: number
  hourlyRate: number
  billable: boolean
}

export type UpdateTimeEntryRequest = CreateTimeEntryRequest

export interface StartTimerRequest {
  description: string
  hourlyRate: number
  billable: boolean
}

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CANCELED'

export interface InvoiceLineResponse {
  id: string
  description: string
  minutes: number
  hourlyRate: number
  amount: number
}

export interface InvoiceResponse {
  id: string
  clientId: string
  clientName: string | null
  number: string
  status: InvoiceStatus
  statusLabel: string
  issueDate: string
  dueDate: string | null
  currency: string
  subtotal: number
  vatRate: number | null
  vatAmount: number
  total: number
  notes: string | null
  lines: InvoiceLineResponse[]
  createdAt: string
}

export interface InvoiceSummary {
  id: string
  clientId: string
  clientName: string | null
  number: string
  status: InvoiceStatus
  statusLabel: string
  issueDate: string
  dueDate: string | null
  currency: string
  total: number
  createdAt: string
}

export interface BillingProfileRequest {
  name: string
  inn?: string | null
  kpp?: string | null
  ogrn?: string | null
  legalAddress?: string | null
  bankName?: string | null
  bankBic?: string | null
  bankAccount?: string | null
  corrAccount?: string | null
  email?: string | null
  phone?: string | null
}

export interface BillingProfileResponse extends BillingProfileRequest {
  updatedAt: string
}

export interface InvoicePaymentResponse {
  invoiceId: string
  confirmationUrl: string
}

export interface CreateInvoiceRequest {
  clientId: string
  caseId?: string | null
  timeEntryIds?: string[] | null
  dueDate?: string | null
  vatRate?: number | null
  notes?: string | null
}

export type DeadlineType = 'FILING_DEADLINE' | 'NEXT_HEARING' | 'EXPIRY'

export interface DashboardStatusCount {
  status: CaseStatus
  statusName: string
  count: number
}

export interface DashboardDeadline {
  caseId: string
  caseTitle: string
  type: DeadlineType
  typeName: string
  date: string
  daysLeft: number
}

export interface DashboardRecentCase {
  id: string
  title: string
  status: CaseStatus
  statusName: string
  createdAt: string
}

export interface DashboardMoneyOnTable {
  uninvoicedMinutes: number
  uninvoicedAmount: number
}

export interface DashboardTodayTask {
  id: string
  caseId: string
  caseTitle: string
  text: string
  dueDate: string
  daysOverdue: number
}

export interface DashboardUnpaidInvoiceItem {
  id: string
  number: string
  clientName: string | null
  total: number
  currency: string
  dueDate: string | null
  daysOverdue: number
}

export interface DashboardUnpaidInvoices {
  count: number
  totalAmount: number
  items: DashboardUnpaidInvoiceItem[]
}

export interface DashboardResponse {
  pipeline: DashboardStatusCount[]
  activeCases: number
  openTasks: number
  upcomingDeadlines: DashboardDeadline[]
  recentCases: DashboardRecentCase[]
  moneyOnTable: DashboardMoneyOnTable
  tasksToday: DashboardTodayTask[]
  unpaidInvoices: DashboardUnpaidInvoices
}

export type CalendarEventType = 'DEADLINE' | 'HEARING' | 'TASK'

export interface CalendarEvent {
  id: string
  type: CalendarEventType
  typeName: string
  caseId: string
  caseTitle: string
  title: string
  detail: string | null
  date: string
}

export interface SearchCaseHit {
  id: string
  title: string
  status: CaseStatus
  statusName: string
  clientName: string | null
}

export interface SearchConversationHit {
  id: string
  title: string
}

export interface SearchDocumentHit {
  id: string
  title: string
  fileName: string
  caseId: string | null
  snippet: string | null
}

export interface SearchClientHit {
  id: string
  name: string
  email: string | null
  phone: string | null
}

export interface SearchInvoiceHit {
  id: string
  number: string
  clientName: string | null
  total: number
  currency: string
  status: InvoiceStatus
  statusName: string
}

export interface GlobalSearchResponse {
  cases: SearchCaseHit[]
  conversations: SearchConversationHit[]
  documents: SearchDocumentHit[]
  clients: SearchClientHit[]
  invoices: SearchInvoiceHit[]
}

export type ClientType = 'INDIVIDUAL' | 'COMPANY'

export interface ClientResponse {
  id: string
  name: string
  type: ClientType
  typeName: string
  phone: string | null
  email: string | null
  inn: string | null
  notes: string | null
  createdAt: string
  caseCount: number
}

export interface ClientDetailResponse {
  client: ClientResponse
  cases: CaseResponse[]
}

export type PortalAccessStatus = 'NONE' | 'PENDING' | 'ACCEPTED'

export interface PortalInviteStatusResponse {
  status: PortalAccessStatus
  email: string | null
  expiresAt: string | null
}

export interface PortalCaseResponse {
  id: string
  title: string
  description: string | null
  status: CaseStatus
  statusName: string
  filingDeadline: string | null
  nextHearingDate: string | null
  createdAt: string
}

export interface PortalCaseDetailResponse {
  id: string
  title: string
  description: string | null
  status: CaseStatus
  statusName: string
  filingDeadline: string | null
  nextHearingDate: string | null
  courtSystem: CourtSystem
  courtSystemName: string
  courtCaseNumber: string | null
  courtCardUrl: string | null
  createdAt: string
  hearings: CaseHearingEvent[]
}

export interface PortalInvitePreviewResponse {
  email: string
  clientName: string | null
  accountExists: boolean
}

export interface CreateClientRequest {
  name: string
  type: ClientType
  phone?: string
  email?: string
  inn?: string
  notes?: string
}

export type UpdateClientRequest = CreateClientRequest

export type ConflictSource = 'CLIENT' | 'CASE_PARTY'

export interface ConflictHit {
  source: ConflictSource
  matchedName: string
  clientId: string | null
  caseId: string | null
  caseTitle: string | null
  role: string | null
}

export type ContactType = 'CALL' | 'MEETING' | 'LETTER' | 'EMAIL' | 'MESSENGER'

export interface ContactResponse {
  id: string
  clientId: string
  type: ContactType
  typeName: string
  contactDate: string
  notes: string | null
  createdAt: string
}

export interface CreateContactRequest {
  type: ContactType
  contactDate: string
  notes?: string
}

export type UpdateContactRequest = CreateContactRequest

export interface TemplateResponse {
  id: string
  name: string
  content: string
  createdAt: string
}

export interface CreateTemplateRequest {
  name: string
  content: string
}

export type UpdateTemplateRequest = CreateTemplateRequest

export interface WorkflowInfo {
  id: string
  displayName: string
  instruction: string
}

export interface SourceReference {
  title: string
  fragment: string
}

export interface AiResponseDto {
  id: string
  caseId: string
  workflowId: string
  workflowName: string
  query: string
  result: string
  sources: SourceReference[]
  rating: number | null
  ratingComment: string | null
  createdAt: string
  followUps: string[]
}

export interface RateRequest {
  rating: number
  comment?: string
}

export type ContractRiskLevel = 'HIGH' | 'MEDIUM' | 'LOW'

export interface ContractRisk {
  clause: string
  category: string
  level: ContractRiskLevel
  explanation: string
  recommendation: string
}

export interface ContractReviewDto {
  id: string
  caseId: string
  documentId: string
  documentTitle: string
  summary: string
  riskScore: number
  highRiskCount: number
  findings: ContractRisk[]
  createdAt: string
}

export type DiffChangeType = 'ADDED' | 'REMOVED' | 'MODIFIED'
export type DiffSegmentType = 'EQUAL' | 'REMOVED' | 'ADDED'

export interface DiffSegment {
  type: DiffSegmentType
  text: string
}

export interface DiffChange {
  order: number
  type: DiffChangeType
  baseText: string
  revisedText: string
  riskLevel: ContractRiskLevel | null
  comment: string | null
  segments: DiffSegment[]
}

export interface DocumentComparisonDto {
  id: string
  caseId: string
  baseDocumentId: string
  revisedDocumentId: string
  baseDocumentTitle: string
  revisedDocumentTitle: string
  summary: string
  riskScore: number
  changeCount: number
  highRiskCount: number
  changes: DiffChange[]
  createdAt: string
}

export type CitationType = 'COURT_CASE' | 'STATUTE'
export type CitationStatus = 'VERIFIED' | 'NOT_FOUND' | 'OUTDATED' | 'UNVERIFIED'

export interface CitationCheck {
  raw: string
  type: CitationType
  normalized: string
  status: CitationStatus
  detail: string
}

export interface CitationCheckResult {
  citations: CitationCheck[]
  total: number
  verified: number
  notFound: number
  outdated: number
  unverified: number
}

export interface WorkflowStat {
  workflowId: string
  workflowName: string
  count: number
  avgRating: number | null
}

export interface AiStatsResponse {
  totalResponses: number
  ratedResponses: number
  positiveRatings: number
  negativeRatings: number
  guardChecks: number
  guardRefusals: number
  citationsChecked: number
  citationsVerified: number
  workflows: WorkflowStat[]
}

export interface ClientStatsResponse {
  newThisWeek: number
  totalActive: number
}

export interface DraftTypeInfo {
  id: string
  displayName: string
}

export interface CaseDraftDto {
  id: string
  caseId: string
  draftType: string
  draftTypeName: string
  title: string
  content: string
  createdAt: string
  updatedAt: string | null
}

export interface CaseDraftVersionDto {
  id: string
  versionNo: number
  note: string | null
  content: string
  createdAt: string
}

export interface UpdateDraftRequest {
  content: string
  note?: string
}

export interface RefineDraftRequest {
  instruction: string
  selectedText?: string
}

export interface RefineDraftResponse {
  revisedText: string
}

export interface CaseDraftSummaryDto {
  id: string
  caseId: string
  draftType: string
  draftTypeName: string
  title: string
  createdAt: string
}

export interface GenerateDraftRequest {
  draftType: string
  seedAnswer?: string
}

export interface LawyerProfileResponse {
  userId: string
  email: string
  fullName: string
  specialization: string | null
  phone: string | null
  telegramLinked: boolean
}

export interface TelegramLinkResponse {
  code: string
  deepLink: string
  expiresAt: string
}

export interface UpdateProfileRequest {
  fullName: string
  specialization?: string
  phone?: string
}

export interface LanguageSettingsResponse {
  language: string
}

export interface UpdateLanguageRequest {
  language: string
}

export interface NotificationSettingsResponse {
  loginAlertEmail: boolean
  loginAlertTelegram: boolean
  loginAlertPush: boolean
  caseMessageEmail: boolean
  caseMessageTelegram: boolean
  caseMessagePush: boolean
  digestPush: boolean
  telegramLinked: boolean
}

export interface UpdateNotificationSettingsRequest {
  loginAlertEmail: boolean
  loginAlertTelegram: boolean
  loginAlertPush: boolean
  caseMessageEmail: boolean
  caseMessageTelegram: boolean
  caseMessagePush: boolean
  digestPush: boolean
}

export interface PushConfigResponse {
  configured: boolean
  publicKey: string | null
}

export interface PushSubscriptionRequest {
  endpoint: string
  p256dh: string
  auth: string
  userAgent?: string
}

export type MessageAuthorRole = 'LAWYER' | 'CLIENT'

export interface CaseMessageResponse {
  id: string
  authorUserId: string
  authorRole: MessageAuthorRole
  body: string
  createdAt: string
}

export interface CaseThreadResponse {
  caseId: string
  caseTitle: string
  clientName: string | null
  lastMessagePreview: string
  lastAuthorRole: MessageAuthorRole
  lastMessageAt: string
  unreadCount: number
}

export type WorkflowStepType = 'AI_ANALYSIS' | 'GENERATE_DRAFT' | 'GENERATE_TASKS' | 'SET_DEADLINE'
export type WorkflowCategory = 'BANKRUPTCY' | 'DEBT_COLLECTION' | 'REGISTRATION' | 'CUSTOM'
export type WorkflowRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED'
export type WorkflowStepStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'

export interface WorkflowStepConfig {
  order: number
  type: WorkflowStepType
  title: string
  instruction?: string | null
  draftType?: string | null
  deadlineType?: DeadlineType | null
  deadlineOffsetDays?: number | null
}

export interface WorkflowDefinitionDto {
  id: string
  name: string
  description: string | null
  category: WorkflowCategory
  categoryName: string
  system: boolean
  editable: boolean
  steps: WorkflowStepConfig[]
  createdAt: string
  updatedAt: string
}

export interface WorkflowStepInput {
  type: WorkflowStepType
  title: string
  instruction?: string | null
  draftType?: string | null
  deadlineType?: DeadlineType | null
  deadlineOffsetDays?: number | null
}

export interface SaveWorkflowDefinitionRequest {
  name: string
  description?: string | null
  category: WorkflowCategory
  steps: WorkflowStepInput[]
}

export interface WorkflowStepRun {
  order: number
  type: WorkflowStepType
  title: string
  status: WorkflowStepStatus
  detail?: string | null
  aiResponseId?: string | null
  draftId?: string | null
  error?: string | null
}

export interface WorkflowRunDto {
  id: string
  caseId: string
  definitionId: string
  definitionName: string
  category: WorkflowCategory
  categoryName: string
  status: WorkflowRunStatus
  statusName: string
  steps: WorkflowStepRun[]
  startedAt: string
  finishedAt: string | null
}

export type SubscriptionStatus = 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED'

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'CANCELED'

export interface BillingStatus {
  planCode: string
  planName: string
  status: SubscriptionStatus
  trialEnd: string | null
  currentPeriodEnd: string | null
  cancelAtPeriodEnd: boolean
  dailyRequests: number
  dailyTokens: number
  seats: number
}

export interface BillingPlan {
  code: string
  name: string
  priceKopecks: number
  dailyRequests: number
  dailyTokens: number
  seats: number
  isDefault: boolean
}

export interface PaymentRecord {
  id: string
  planCode: string | null
  amountKopecks: number
  status: PaymentStatus
  confirmationUrl: string | null
  paidAt: string | null
  createdAt: string
}

export interface CheckoutResponse {
  paymentId: string
  confirmationUrl: string | null
}

export type SignatureStatus = 'PENDING' | 'SIGNED' | 'DECLINED' | 'CANCELED' | 'EXPIRED'

export type SignatureProviderType = 'SIMPLE' | 'DETACHED_CMS' | 'DIADOC'

export type SignatureSignerRole = 'CLIENT' | 'LAWYER'

export interface SignatureRequestResponse {
  id: string
  documentId: string
  caseId: string
  provider: SignatureProviderType
  signerRole: SignatureSignerRole
  signerLawyerId: string | null
  status: SignatureStatus
  documentHash: string
  message: string | null
  signerName: string | null
  signerIp: string | null
  signedAt: string | null
  declineReason: string | null
  expiresAt: string | null
  createdAt: string
  certificateSubject: string | null
  certificateIssuer: string | null
  certificateSerial: string | null
  certificateValidFrom: string | null
  certificateValidTo: string | null
  signatureAlgorithm: string | null
  declaredSigningTime: string | null
  hasSignatureFile: boolean
}

export type TabularReviewStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'

export type ReviewAnswerConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NOT_FOUND'

export interface ReviewCitation {
  chunkIndex: number
  quote: string
}

export interface TabularReviewDocumentDto {
  documentId: string
  documentTitle: string
  position: number
  status: TabularReviewStatus
  errorMessage: string | null
}

export interface TabularReviewCellDto {
  documentId: string
  questionIndex: number
  answer: string
  confidence: ReviewAnswerConfidence
  citations: ReviewCitation[]
}

export interface TabularReviewDto {
  id: string
  caseId: string
  title: string
  status: TabularReviewStatus
  questions: string[]
  documents: TabularReviewDocumentDto[]
  cells: TabularReviewCellDto[]
  filledCells: number
  totalCells: number
  errorMessage: string | null
  createdAt: string
  completedAt: string | null
}

export interface TabularReviewSummaryDto {
  id: string
  caseId: string
  title: string
  status: TabularReviewStatus
  documentCount: number
  questionCount: number
  createdAt: string
  completedAt: string | null
}

export interface CreateTabularReviewRequest {
  caseId: string
  title?: string
  documentIds: string[]
  questions: string[]
}

export type SavedViewScope = 'CASES' | 'CLIENTS' | 'INVOICES' | 'TEMPLATES'

export interface SavedViewResponse {
  id: string
  scope: SavedViewScope
  name: string
  config: string
  sharedWithTeam: boolean
  orgId: string | null
  owned: boolean
  updatedAt: string
}

export interface CreateSavedViewRequest {
  scope: SavedViewScope
  name: string
  config: string
  sharedWithTeam: boolean
  orgId?: string | null
}

export interface UpdateSavedViewRequest {
  name: string
  config: string
  sharedWithTeam: boolean
  orgId?: string | null
}

export type MailboxStatus = 'PENDING' | 'OK' | 'ERROR'

export interface MailboxResponse {
  id: string
  emailAddress: string
  imapHost: string
  imapPort: number
  imapSsl: boolean
  folder: string
  syncEnabled: boolean
  status: MailboxStatus
  lastError: string | null
  lastSyncAt: string | null
  createdAt: string
}

export interface MailHostPresetResponse {
  id: string
  displayName: string
  imapHost: string
  imapPort: number
  imapSsl: boolean
  domains: string[]
}

export interface CreateMailboxRequest {
  emailAddress: string
  password: string
  imapHost?: string
  imapPort?: number
  imapSsl?: boolean
  folder?: string
}

export interface UpdateMailboxRequest {
  password?: string
  imapHost?: string
  imapPort?: number
  imapSsl?: boolean
  folder?: string
  syncEnabled?: boolean
}

export interface MailboxTestResult {
  success: boolean
  status: MailboxStatus
  message: string | null
}

export interface MailSyncResult {
  mailboxId: string
  success: boolean
  status: MailboxStatus
  fetched: number
  saved: number
  reindexed: boolean
  error: string | null
}

export type EmailDirection = 'IN' | 'OUT'

export type EmailLinkSource = 'CASE_NUMBER' | 'THREAD' | 'ADDRESS' | 'MANUAL'

export interface EmailMessageResponse {
  id: string
  mailboxId: string
  direction: EmailDirection
  fromAddress: string
  toAddresses: string
  ccAddresses: string | null
  subject: string | null
  bodyText: string | null
  sentAt: string
  hasAttachments: boolean
  attachmentCount: number
  caseId: string | null
  clientId: string | null
  linkSource: EmailLinkSource | null
  linkSourceName: string | null
  linkedAt: string | null
}

export interface LinkEmailRequest {
  caseId?: string | null
  clientId?: string | null
}
