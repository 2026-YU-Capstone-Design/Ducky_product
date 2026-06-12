import type {
  BackendChatMessage,
  BackendConversationDetail,
  BackendConversationSummary,
} from "@/lib/api/chat";
import type {
  ChatMessage,
  ChatMessageType,
  ChatRole,
  ChatSessionSummary,
} from "@/types/chat";
import type { Session, SessionStatus } from "@/types/session";

function toChatRole(value: string): ChatRole {
  if (value === "user" || value === "assistant" || value === "system") {
    return value;
  }

  return "assistant";
}

function toChatMessageType(value: string): ChatMessageType {
  if (
    value === "question" ||
    value === "answer" ||
    value === "hint" ||
    value === "feedback" ||
    value === "system"
  ) {
    return value;
  }

  return "question";
}

function toHintLevel(value: number | null | undefined): 1 | 2 | 3 | undefined {
  if (value === 1 || value === 2 || value === 3) {
    return value;
  }

  return undefined;
}

function toSessionStatus(value: string): SessionStatus {
  return value === "completed" ? "completed" : "in_progress";
}

function fallbackDate(value: string | null | undefined) {
  return value ?? new Date().toISOString();
}

export function toChatMessage(message: BackendChatMessage): ChatMessage {
  const hintLevel = toHintLevel(message.hintLevel);

  return {
    id: message.id,
    role: toChatRole(message.role),
    content: message.content,
    type: toChatMessageType(message.type),
    createdAt: message.createdAt,
    ...(hintLevel ? { hintLevel } : {}),
    ...(message.hintNumber ? { hintNumber: message.hintNumber } : {}),
  };
}

export function toChatSessionSummary(
  conversation: BackendConversationSummary,
): ChatSessionSummary {
  return {
    id: conversation.id,
    title: conversation.title,
    topic: conversation.topic,
    status: conversation.status,
    summary: conversation.summary,
    hintCount: conversation.hintCount,
    messageCount: conversation.messageCount,
    startedAt: conversation.startedAt,
    updatedAt: conversation.updatedAt,
    completedAt: conversation.completedAt,
  };
}

export function toSessionSummary(
  conversation: BackendConversationSummary,
): Session {
  const updatedAt = fallbackDate(conversation.updatedAt);

  return {
    id: conversation.id,
    title: conversation.title,
    topic: conversation.topic,
    status: toSessionStatus(conversation.status),
    summary: conversation.summary ?? "진행 중인 러버덕 학습 세션입니다.",
    hintCount: conversation.hintCount,
    messageCount: conversation.messageCount,
    startedAt: fallbackDate(conversation.startedAt ?? updatedAt),
    updatedAt,
    completedAt: conversation.completedAt ?? undefined,
    messages: [],
  };
}

export function toSessionDetail(conversation: BackendConversationDetail): Session {
  return {
    ...toSessionSummary(conversation),
    messages: conversation.messages.map(toChatMessage),
  };
}
