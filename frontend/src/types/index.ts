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
}

export interface ApplyRequest {
  email: string
  password: string
  fullName: string
  specialization: string
  phone: string
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

export interface ChatRequest {
  conversationId?: string
  message: string
  attachedDocumentIds?: string[]
}

export interface ChatResponse {
  conversationId: string
  answer: string
  sources: string[]
  followUps: string[]
}

export interface ConversationResponse {
  id: string
  title: string
  createdAt: string
}

export interface MessageResponse {
  id: string
  role: MessageRole
  content: string
  sources?: string[]
  rating?: number | null
  createdAt: string
}

export interface DocumentResponse {
  id: string
  title: string
  fileName: string
  status: DocumentStatus
  uploadedAt: string
  visibleToClient?: boolean
}

export interface DocumentUploadResponse {
  id: string
  title: string
  fileName: string
  status: 'PROCESSING'
}

export interface LawyerResponse {
  id: string
  email: string
  fullName: string
  specialization: string
  phone: string
}

export type CaseStatus = 'INTAKE' | 'IN_PROGRESS' | 'SUBMITTED' | 'CLOSED_WON' | 'CLOSED_LOST'

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
  arbitrCaseNumber: string | null
  arbitrCardUrl: string | null
  createdAt: string
}

export interface CaseHearingEvent {
  id: string
  eventDate: string | null
  eventType: string | null
  description: string | null
  courtName: string | null
}

export interface CreateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
  orgId?: string | null
  filingDeadline?: string | null
  nextHearingDate?: string | null
  expiresAt?: string | null
  arbitrCaseNumber?: string | null
}

export interface UpdateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
  filingDeadline?: string | null
  nextHearingDate?: string | null
  expiresAt?: string | null
  arbitrCaseNumber?: string | null
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

export interface DashboardResponse {
  pipeline: DashboardStatusCount[]
  activeCases: number
  openTasks: number
  upcomingDeadlines: DashboardDeadline[]
  recentCases: DashboardRecentCase[]
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

export interface GlobalSearchResponse {
  cases: SearchCaseHit[]
  conversations: SearchConversationHit[]
  documents: SearchDocumentHit[]
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
  arbitrCaseNumber: string | null
  arbitrCardUrl: string | null
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

export type CitationType = 'COURT_CASE' | 'STATUTE'
export type CitationStatus = 'VERIFIED' | 'NOT_FOUND' | 'UNVERIFIED'

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

export interface NotificationSettingsResponse {
  loginAlertEmail: boolean
  loginAlertTelegram: boolean
  caseMessageEmail: boolean
  caseMessageTelegram: boolean
  telegramLinked: boolean
}

export interface UpdateNotificationSettingsRequest {
  loginAlertEmail: boolean
  loginAlertTelegram: boolean
  caseMessageEmail: boolean
  caseMessageTelegram: boolean
}

export type MessageAuthorRole = 'LAWYER' | 'CLIENT'

export interface CaseMessageResponse {
  id: string
  authorUserId: string
  authorRole: MessageAuthorRole
  body: string
  createdAt: string
}
