import type { ChatMessage } from "./chat";

export type SessionStatus = "in_progress" | "completed";

export interface Session {
  id: string;
  title: string;
  topic: string;
  status: SessionStatus;
  summary: string;
  hintCount: number;
  messageCount: number;
  startedAt: string;
  updatedAt: string;
  completedAt?: string;
  messages: ChatMessage[];
}
