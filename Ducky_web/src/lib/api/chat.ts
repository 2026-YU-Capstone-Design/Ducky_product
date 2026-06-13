import { apiRequest } from "./client";

export interface BackendChatMessage {
  id: string;
  role: string;
  content: string;
  type: string;
  createdAt: string;
  hintLevel?: number | null;
  hintNumber?: number | null;
}

export interface BackendConversationSummary {
  id: string;
  title: string;
  topic: string;
  status: string;
  summary: string | null;
  hintCount: number;
  messageCount: number;
  startedAt: string | null;
  updatedAt: string | null;
  completedAt: string | null;
}

export interface BackendConversationDetail
  extends BackendConversationSummary {
  messages: BackendChatMessage[];
}

export interface BackendChatTurn {
  conversationId: string;
  userMessage: BackendChatMessage;
  aiResponse: BackendChatMessage;
}

export interface BackendHintResponse {
  conversationId: string;
  hintMessage: BackendChatMessage;
}

export interface BackendEndConversationResponse {
  conversationId: string;
  status: string;
  feedbackMessage: BackendChatMessage;
}

export function startConversation(payload: {
  title: string;
  topic: string;
}) {
  return apiRequest<BackendConversationSummary>("/api/chat/conversations", {
    method: "POST",
    auth: true,
    body: payload,
  });
}

export function listConversations() {
  return apiRequest<BackendConversationSummary[]>("/api/chat/conversations", {
    method: "GET",
    auth: true,
  });
}

export function getConversation(conversationId: string) {
  return apiRequest<BackendConversationDetail>(
    `/api/chat/conversations/${conversationId}`,
    {
      method: "GET",
      auth: true,
    },
  );
}

export function sendChatMessage(payload: {
  conversationId: string;
  message: string;
  inputType?: "text" | "voice";
}) {
  return apiRequest<BackendChatTurn>("/api/chat/messages", {
    method: "POST",
    auth: true,
    body: {
      conversationId: Number(payload.conversationId),
      message: payload.message,
      inputType: payload.inputType ?? "text",
    },
  });
}

export function requestChatHint(conversationId: string) {
  return apiRequest<BackendHintResponse>("/api/chat/hints", {
    method: "POST",
    auth: true,
    body: {
      conversationId: Number(conversationId),
    },
  });
}

export function endConversation(conversationId: string) {
  return apiRequest<BackendEndConversationResponse>(
    `/api/chat/conversations/${conversationId}/end`,
    {
      method: "PATCH",
      auth: true,
    },
  );
}

export function deleteConversation(conversationId: string) {
  return apiRequest<void>(`/api/chat/conversations/${conversationId}`, {
    method: "DELETE",
    auth: true,
  });
}
