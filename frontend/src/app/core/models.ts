// TypeScript shapes of the backend DTOs (records in the Java code). Numbers are only displayed, never calculated.

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 9457 error body returned by every failing API call. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Stable machine-readable reason, sent by the AI endpoints (AI_KEY_MISSING, AI_REJECTED...). */
  code?: string;
  /** How long the AI provider asked us to wait, when it said (AI_DAILY_QUOTA_EXHAUSTED, AI_RATE_LIMITED). */
  retryAfterSeconds?: number;
}

// ---- catalog ----

export interface Supplier {
  id: number;
  name: string;
  vatNumber: string;
}

export interface SupplierRequest {
  name: string;
  vatNumber: string;
}

export interface Item {
  id: number;
  code: string;
  description: string;
  unit: string;
  reorderThreshold: number;
}

/** What the item picker hands over when a person chooses an item (a search hit has no full Item). */
export type PickedItem = Pick<Item, 'id' | 'code' | 'description' | 'unit'>;

export interface ItemRequest {
  code: string;
  description: string;
  unit: string;
  reorderThreshold: number;
}

export interface SupplierItemCode {
  id: number;
  supplierId: number;
  supplierCode: string;
  itemId: number;
  itemCode: string;
}

export interface SupplierItemCodeRequest {
  supplierCode: string;
  itemId: number;
}

// ---- inventory ----

export type MovementType = 'IN' | 'OUT';

export interface StockMovement {
  id: number;
  itemId: number;
  itemCode: string;
  type: MovementType;
  quantity: number;
  reason: string;
  sourceDocument: string | null;
  createdAt: string;
}

export interface StockMovementRequest {
  itemId: number;
  type: MovementType;
  quantity: number;
  reason: string;
  sourceDocument: string | null;
}

/** An item with its current stock (GET /api/inventory/stock and /low-stock). */
export interface StockLevel {
  itemId: number;
  itemCode: string;
  description: string;
  unit: string;
  quantity: number;
  reorderThreshold: number;
}

// ---- imports ----

export type DraftStatus = 'DRAFT' | 'CONFIRMED';
export type DraftSource = 'XML' | 'PDF';
export type LineStatus = 'MATCHED' | 'PENDING_REVIEW' | 'SKIPPED';

export interface DraftSummary {
  id: number;
  status: DraftStatus;
  supplierName: string;
  tipoDocumento: string;
  number: string;
  date: string;
  total: number;
  createdAt: string;
}

export interface DraftLine {
  id: number;
  lineNumber: number;
  supplierCode: string | null;
  description: string;
  quantity: number | null;
  unit: string | null;
  unitPrice: number | null;
  totalPrice: number;
  vatRate: number;
  stockDelta: number | null;
  status: LineStatus;
  itemId: number | null;
  itemCode: string | null;
}

export interface DdtReference {
  number: string;
  date: string | null;
}

export interface Draft {
  id: number;
  status: DraftStatus;
  source: DraftSource;
  supplierId: number;
  supplierName: string;
  tipoDocumento: string;
  number: string;
  date: string;
  total: number;
  createdAt: string;
  confirmedAt: string | null;
  ddtReferences: DdtReference[];
  pendingLines: number;
  lines: DraftLine[];
}

// ---- AI ----

export interface AiStatus {
  available: boolean;
}

export type SuggestionStatus = 'SUGGESTED' | 'ACCEPTED' | 'REJECTED';

export interface Suggestion {
  id: number;
  draftId: number;
  draftLineId: number;
  itemId: number | null;
  itemCode: string | null;
  itemDescription: string | null;
  justification: string;
  status: SuggestionStatus;
  createdAt: string;
}

export interface MatchRun {
  suggestions: Suggestion[];
  linesLeftForNextRequest: number;
}

export interface AssistantAnswer {
  answer: string;
  toolCalls: number;
}

// ---- item search (GET /api/items/search) ----

/** HYBRID = meaning and spelling together ("ricerca intelligente"); TEXT = spelling only ("ricerca testuale"). */
export type SearchMode = 'HYBRID' | 'TEXT';

/** Why a smart search ran as a text search (null when it did not fall back). */
export type SearchFallbackReason = 'NOT_CONFIGURED' | 'DIMENSION_MISMATCH' | 'NOT_INDEXED' | 'PROVIDER_ERROR';

export interface ItemHit {
  itemId: number;
  code: string;
  description: string;
  unit: string;
  reorderThreshold: number;
  quantity: number;
  /** 0 to 1, how well the item matches the search. */
  score: number;
  vectorScore: number | null;
  textScore: number | null;
}

export interface ItemSearchResult {
  mode: SearchMode;
  fallbackReason: SearchFallbackReason | null;
  results: ItemHit[];
}
