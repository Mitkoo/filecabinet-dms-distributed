export interface AuthResponse {
  token: string
  username: string
  role: string
}

export interface Category {
  id: string
  name: string
  description: string | null
}

export interface DocumentListItem {
  id: string
  title: string
  documentType: string
  status: string
  uploadedOn: string
  categoryName: string
  ownerUsername: string
}

export interface DocumentField {
  id: string
  fieldName: string
  fieldValue: string
  confidence: number | null
}

export interface DocumentDetail {
  id: string
  title: string
  documentType: string
  status: string
  filePath: string
  uploadedOn: string
  categoryId: string
  categoryName: string
  ownerUsername: string
  fields: DocumentField[]
}

export interface Paged<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface WorkflowStep {
  id: string
  stepOrder: number
  reviewerUsername: string
  status: string
  comment: string | null
  decidedOn: string | null
}

export interface WorkflowEvent {
  id: string
  eventType: string
  actorUsername: string | null
  message: string | null
  createdOn: string
}

export interface WorkflowDetail {
  id: string
  documentId: string
  documentTitle: string
  status: string
  initiatorUsername: string
  message: string | null
  createdOn: string
  completedOn: string | null
  steps: WorkflowStep[]
  events: WorkflowEvent[]
}

export interface WorkflowSummary {
  id: string
  documentId: string
  documentTitle: string
  initiatorUsername: string
  status: string
  createdOn: string
}

export interface UserSummary {
  id: string
  username: string
  email: string
  role: string
  fullName: string | null
}

export interface ReviewerOption {
  id: string
  username: string
  role: string
}

export interface Profile {
  id: string
  username: string
  email: string
  role: string
  fullName: string | null
  phone: string | null
  jobTitle: string | null
  companyName: string | null
  companyAddress: string | null
}

export interface FieldBox {
  page: number
  x: number
  y: number
  width: number
  height: number
  pageWidth: number
  pageHeight: number
}

export interface ExtractionField {
  id: string
  fieldName: string
  fieldValue: string
  confidence: number
  box: FieldBox | null
}

export interface LineItem {
  id: string
  lineNumber: number | null
  description: string | null
  quantity: number | null
  unitPrice: number | null
  vatRatePercent: number | null
  totalAmount: number | null
  category: string | null
  box: FieldBox | null
}

export interface ExtractionJob {
  id: string
  documentId: string
  provider: string
  status: string
  attempts: number
  requestedOn: string
  completedOn: string | null
  fields: ExtractionField[]
  lineItems: LineItem[]
}
