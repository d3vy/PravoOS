export type UserRole = 'LAWYER' | 'ADMIN'

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type DocumentStatus = 'PROCESSING' | 'READY' | 'FAILED'

export type MessageRole = 'USER' | 'ASSISTANT'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
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
}

export interface ChatRequest {
  conversationId?: string
  message: string
}

export interface ChatResponse {
  conversationId: string
  answer: string
  sources: string[]
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
