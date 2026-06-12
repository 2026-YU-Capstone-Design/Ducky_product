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
