"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { uploadDocument, waitForDocument } from "@/lib/api/documents";
import {
  endConversation,
  getConversation,
  listConversations,
  requestChatHint,
  sendChatMessage,
  startConversation,
  type BackendChatMessage,
  type BackendConversationSummary,
} from "@/lib/api/chat";
import { getAccessToken } from "@/lib/api/client";
import type {
  ChatAttachment,
  ChatMessage,
  ChatMessageType,
  ChatRole,
  ChatSessionSummary,
  HintRecord,
  SessionProgressStatus,
} from "@/types/chat";

interface UseChatSessionOptions {
  enabled?: boolean;
}

const DEFAULT_SESSION: ChatSessionSummary = {
  id: "",
  title: "Ducky learning session",
  topic: "러버덕 질문 훈련",
  status: "in_progress",
  summary: "진행 중인 러버덕 학습 세션입니다.",
  hintCount: 0,
  messageCount: 0,
};

function createClientId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function createAttachments(
  files: File[] | undefined,
): ChatAttachment[] | undefined {
  if (!files?.length) return undefined;

  return files.map((file) => ({
    id: createClientId("attachment"),
    name: file.name,
    mimeType: file.type || "application/octet-stream",
    sizeLabel: formatFileSize(file.size),
  }));
}

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

function toChatMessage(
  message: BackendChatMessage,
  attachments?: ChatAttachment[],
): ChatMessage {
  const hintLevel = toHintLevel(message.hintLevel);

  return {
    id: message.id,
    role: toChatRole(message.role),
    content: message.content,
    type: toChatMessageType(message.type),
    createdAt: message.createdAt,
    sources: message.sources,
    responseMode: message.responseMode,
    responseStrategy: message.responseStrategy,
    ...(hintLevel ? { hintLevel } : {}),
    ...(message.hintNumber ? { hintNumber: message.hintNumber } : {}),
    ...(attachments?.length ? { attachments } : {}),
  };
}

function toSessionSummary(
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

function toHintRecord(
  message: ChatMessage,
  fallbackNumber: number,
): HintRecord {
  const number = message.hintNumber ?? fallbackNumber;
  const depth =
    message.hintLevel ?? toHintLevel(Math.min(Math.ceil(number / 2), 3)) ?? 3;

  return {
    id: message.id,
    number,
    depth,
    title: `힌트 ${number}`,
    content: message.content,
  };
}

function buildHintHistory(messages: ChatMessage[]) {
  return messages
    .filter((message) => message.type === "hint")
    .map((message, index) => toHintRecord(message, index + 1));
}

function toProgressStatus(status: string): SessionProgressStatus {
  return status === "completed" ? "completed" : "learning";
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "채팅 서버와 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function useChatSession({ enabled = true }: UseChatSessionOptions = {}) {
  const isLoadingRef = useRef(false);
  const processingController = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    processingController.current = controller;
    return () => controller.abort();
  }, []);
  const [activeSession, setActiveSession] =
    useState<ChatSessionSummary>(DEFAULT_SESSION);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [hintHistory, setHintHistory] = useState<HintRecord[]>([]);
  const [status, setStatus] = useState<SessionProgressStatus>("learning");
  const [isLoadingSession, setIsLoadingSession] = useState(enabled);
  const [isThinking, setIsThinking] = useState(false);
  const [processingMessage, setProcessingMessage] = useState<string | null>(
    null,
  );
  const [error, setError] = useState<string | null>(null);
  const [isReady, setIsReady] = useState(false);

  const loadSession = useCallback(async () => {
    if (!enabled) {
      return;
    }
    if (isLoadingRef.current) {
      return;
    }

    if (!getAccessToken()) {
      setError("로그인 후 채팅을 시작할 수 있습니다.");
      setIsReady(false);
      setIsLoadingSession(false);
      return;
    }

    isLoadingRef.current = true;
    setIsLoadingSession(true);
    setError(null);

    try {
      const conversations = await listConversations();
      const inProgressConversation = conversations.find(
        (conversation) => conversation.status === "in_progress",
      );
      const conversation =
        inProgressConversation ??
        (await startConversation({
          title: "Ducky learning session",
          topic: "러버덕 질문 훈련",
        }));

      const detail = await getConversation(conversation.id);
      const nextMessages = detail.messages.map((message) =>
        toChatMessage(message),
      );

      setActiveSession(toSessionSummary(detail));
      setMessages(nextMessages);
      setHintHistory(buildHintHistory(nextMessages));
      setStatus(toProgressStatus(detail.status));
      setIsReady(true);
    } catch (sessionError) {
      setError(getErrorMessage(sessionError));
      setIsReady(false);
    } finally {
      isLoadingRef.current = false;
      setIsLoadingSession(false);
    }
  }, [enabled]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadSession();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [loadSession]);

  const sendMessage = useCallback(
    async ({ text, files }: { text: string; files?: File[] }) => {
      const trimmedText = text.trim();
      const attachments = createAttachments(files);

      if (!enabled || !activeSession.id) return false;
      if (!trimmedText && !attachments?.length) return false;
      if (status === "completed" || isThinking) return false;
      const signal = processingController.current?.signal;

      const optimisticId = createClientId("message");

      const optimisticMessage: ChatMessage = {
        id: optimisticId,
        role: "user",
        content:
          trimmedText ||
          "첨부한 자료를 기준으로 어떤 부분이 막혔는지 설명해볼게요.",
        type: "question",
        createdAt: new Date().toISOString(),
        ...(attachments?.length ? { attachments } : {}),
      };

      setMessages((prev) => [...prev, optimisticMessage]);

      setIsThinking(true);
      setError(null);

      try {
        signal?.throwIfAborted();

        // Persist attachments and wait for indexing before asking about them.
        const attachedDocumentIds: number[] = [];

        for (const file of files ?? []) {
          signal?.throwIfAborted();
          setProcessingMessage(`${file.name} 문서를 처리하고 있습니다.`);

          if (file.size > 20 * 1024 * 1024)
            throw new Error("파일은 20MB 이하로 올려 주세요.");

          const document = await waitForDocument(
            await uploadDocument(file),
            signal,
          );

          attachedDocumentIds.push(Number(document.id));
        }

        setProcessingMessage(null);
        signal?.throwIfAborted();

        const turn = await sendChatMessage({
          conversationId: activeSession.id,
          message:
            trimmedText ||
            "첨부한 자료를 기준으로 어떤 부분이 막혔는지 설명해볼게요.",
          inputType: "text",
          ...(attachedDocumentIds.length
            ? { documentIds: attachedDocumentIds }
            : {}),
        });

        const userMessage = toChatMessage(turn.userMessage, attachments);
        const assistantMessage = toChatMessage(turn.aiResponse);

        setMessages((prev) => [
          ...prev.map((message) =>
            message.id === optimisticId ? userMessage : message,
          ),
          assistantMessage,
        ]);

        setActiveSession((prev) => ({
          ...prev,
          messageCount: (prev.messageCount ?? 0) + 2,
          status:
            userMessage.type === "answer" ||
            assistantMessage.type === "feedback"
              ? "completed"
              : prev.status,
        }));

        if (
          userMessage.type === "answer" ||
          assistantMessage.type === "feedback"
        ) {
          setStatus("completed");
        }

        return true;
      } catch (sendError) {
        setMessages((prev) =>
          prev.filter((message) => message.id !== optimisticId),
        );

        setError(getErrorMessage(sendError));
        return false;
      } finally {
        setProcessingMessage(null);
        setIsThinking(false);
      }
    },
    [activeSession.id, enabled, isThinking, status],
  );

  const requestHint = useCallback(async () => {
    if (!enabled || !activeSession.id) return;
    if (status === "completed" || isThinking) return;

    setIsThinking(true);
    setError(null);

    try {
      const response = await requestChatHint(activeSession.id);
      const hintMessage = toChatMessage(response.hintMessage);
      const isHint = hintMessage.type === "hint";

      setMessages((prev) => [...prev, hintMessage]);
      if (isHint) {
        setHintHistory((prev) => [
          ...prev,
          toHintRecord(hintMessage, prev.length + 1),
        ]);
      }
      setActiveSession((prev) => ({
        ...prev,
        hintCount: (prev.hintCount ?? 0) + (isHint ? 1 : 0),
        messageCount: (prev.messageCount ?? 0) + 1,
      }));
    } catch (hintError) {
      setError(getErrorMessage(hintError));
    } finally {
      setIsThinking(false);
    }
  }, [activeSession.id, enabled, isThinking, status]);

  const completeSession = useCallback(async () => {
    if (!enabled || !activeSession.id) return;
    if (status === "completed" || isThinking) return;

    setIsThinking(true);
    setError(null);

    try {
      const response = await endConversation(activeSession.id);
      const feedbackMessage = toChatMessage(response.feedbackMessage);

      setMessages((prev) => [...prev, feedbackMessage]);
      setActiveSession((prev) => ({
        ...prev,
        status: response.status,
        messageCount: (prev.messageCount ?? 0) + 1,
        completedAt: new Date().toISOString(),
      }));
      setStatus("completed");
    } catch (completeError) {
      setError(getErrorMessage(completeError));
    } finally {
      setIsThinking(false);
    }
  }, [activeSession.id, enabled, isThinking, status]);

  const refreshMessages = useCallback(async () => {
    if (!enabled || !activeSession.id || !getAccessToken()) {
      return;
    }

    try {
      const detail = await getConversation(activeSession.id);
      const nextMessages = detail.messages.map((message) =>
        toChatMessage(message),
      );

      setMessages(nextMessages);
      setHintHistory(buildHintHistory(nextMessages));
      setActiveSession(toSessionSummary(detail));
      setStatus(toProgressStatus(detail.status));
      setError(null);
    } catch (refreshError) {
      setError(getErrorMessage(refreshError));
    }
  }, [activeSession.id, enabled]);

  return {
    activeSession,
    completeSession,
    error,
    hintCount: hintHistory.length,
    hintHistory,
    isCompleted: status === "completed",
    isLoadingSession,
    isReady,
    isThinking,
    messages,
    processingMessage,
    refreshMessages,
    requestHint,
    retry: loadSession,
    sendMessage,
    status,
  };
}
