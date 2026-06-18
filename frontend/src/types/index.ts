export type UserRole = 'LAWYER' | 'ADMIN'

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

export interface ApplyRequest {
  email: string
  password: string
  fullName: string
  barNumber: string
  specialization: string
  phone: string
}

export interface ApplicationResponse {
  id: string
  email: string
  fullName: string
  barNumber: string
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
  barNumber: string
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
  barNumber: string
  specialization: string
  phone: string
}

export type CaseStatus = 'INTAKE' | 'IN_PROGRESS' | 'SUBMITTED' | 'CLOSED_WON' | 'CLOSED_LOST'

export interface CaseResponse {
  id: string
  title: string
  description: string | null
  clientId: string | null
  clientName: string | null
  status: CaseStatus
  statusName: string
  createdAt: string
}

export interface CreateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
}

export interface UpdateCaseRequest {
  title: string
  description?: string
  clientId?: string | null
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

export interface CreateClientRequest {
  name: string
  type: ClientType
  phone?: string
  email?: string
  inn?: string
  notes?: string
}

export type UpdateClientRequest = CreateClientRequest

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
  barNumber: string | null
  specialization: string | null
  phone: string | null
}

export interface UpdateProfileRequest {
  fullName: string
  barNumber?: string
  specialization?: string
  phone?: string
}
