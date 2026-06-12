export type ChatRole = "user" | "assistant" | "system";

export type ChatMessageType =
  | "question"
  | "answer"
  | "hint"
  | "feedback"
  | "system";

export interface ChatAttachment {
  id: string;
  name: string;
  mimeType: string;
  sizeLabel: string;
  url?: string;
}

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  type: ChatMessageType;
  createdAt: string;
  hintLevel?: 1 | 2 | 3;
  hintNumber?: number;
  attachments?: ChatAttachment[];
}

export type SessionProgressStatus = "learning" | "completed";

export interface HintRecord {
  id: string;
  number: number;
  depth: 1 | 2 | 3;
  title: string;
  content: string;
}

export interface ChatSessionSummary {
  id: string;
  title: string;
  topic: string;
  status: string;
  summary?: string | null;
  hintCount?: number;
  messageCount?: number;
  startedAt?: string | null;
  updatedAt?: string | null;
  completedAt?: string | null;
}
