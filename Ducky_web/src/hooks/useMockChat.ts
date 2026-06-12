"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { mockSessions } from "@/data/mockSessions";
import type { ChatAttachment, ChatMessage } from "@/types/chat";

export type SessionProgressStatus = "learning" | "completed";

export interface HintRecord {
  id: string;
  number: number;
  depth: 1 | 2 | 3;
  title: string;
  content: string;
}

const activeMockSession =
  mockSessions.find((session) => session.status === "in_progress") ??
  mockSessions[0];

const replyPrompts = [
  "좋아요. 지금 헷갈리는 지점을 URL, 파일, 화면 중 어디에서 시작했는지 한 문장으로 다시 설명해볼까요?",
  "방금 설명에서 핵심은 공통 구조와 실제 페이지가 나뉜다는 점이에요. 그렇다면 layout.tsx가 맡으면 좋은 일은 무엇일까요?",
  "좋아요. 방금 말한 공통 UI가 실제로 언제 유지되는지 예시를 하나 들어볼까요?",
  "거의 정리됐어요. page.tsx와 layout.tsx를 예시 URL 하나에 연결해서 말해보세요.",
];

const hintCatalog: Array<Omit<HintRecord, "id" | "number">> = [
  {
    depth: 1,
    title: "관찰 지점",
    content:
      "먼저 주소창의 URL과 app 폴더 아래 파일 경로를 나란히 놓고 비교해보세요.",
  },
  {
    depth: 1,
    title: "작게 나누기",
    content:
      "헷갈리면 page, layout, segment를 한꺼번에 보지 말고 page.tsx가 맡는 일만 먼저 말해보세요.",
  },
  {
    depth: 2,
    title: "핵심 개념",
    content:
      "page.tsx는 해당 주소에서 실제로 바뀌어 보이는 화면이고, layout.tsx는 그 화면을 감싸는 공통 구조입니다.",
  },
  {
    depth: 2,
    title: "유지되는 것 찾기",
    content:
      "화면을 이동해도 계속 남아 있는 사이드바, 하단 탭, 공통 헤더 같은 요소는 layout이나 AppShell 쪽 책임일 가능성이 큽니다.",
  },
  {
    depth: 1,
    title: "질문 바꾸기",
    content:
      "'이 파일이 왜 필요하지?' 대신 '이 URL에서 사용자가 실제로 보는 부분은 무엇이지?'라고 질문을 바꿔보세요.",
  },
  {
    depth: 3,
    title: "구체 예시",
    content:
      "/dashboard는 app/dashboard/page.tsx가 화면 내용을 만들고, AppShell은 사이드바와 하단 탭을 감쌉니다.",
  },
  {
    depth: 2,
    title: "역할 구분",
    content:
      "새 URL을 만들 때는 폴더와 page.tsx가 필요하고, 여러 하위 화면이 공유할 틀이 필요할 때 layout.tsx를 떠올리면 됩니다.",
  },
  {
    depth: 3,
    title: "말로 완성하기",
    content:
      "'/chat에서 대화 화면은 page.tsx가 렌더링하고, 앱 공통 내비게이션은 AppShell이 맡는다'처럼 한 문장으로 연결해보세요.",
  },
  {
    depth: 1,
    title: "반례 찾기",
    content:
      "만약 페이지 이동 때마다 사라지는 UI라면 공통 layout보다는 개별 page 안에 있을 가능성이 큽니다.",
  },
  {
    depth: 2,
    title: "판단 기준",
    content:
      "여러 화면에서 반복되는 구조인지, 특정 화면에서만 필요한 내용인지가 layout과 page를 나누는 기준입니다.",
  },
  {
    depth: 3,
    title: "최종 점검",
    content:
      "이제 새 라우트 /sessions를 만든다고 가정하고, 어떤 파일이 화면을 만들고 어떤 컴포넌트가 공통 틀을 제공하는지 말해보세요.",
  },
  {
    depth: 1,
    title: "마무리 신호",
    content:
      "스스로 설명이 가능해졌다면 '답을 알겠어요'라고 말해 세션을 마무리하면 됩니다.",
  },
];

function createMessageId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${Math.round(size / 1024)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function createAttachments(files: File[] | undefined): ChatAttachment[] | undefined {
  if (!files?.length) return undefined;

  return files.map((file) => ({
    id: createMessageId("attachment"),
    name: file.name,
    mimeType: file.type || "application/octet-stream",
    sizeLabel: formatFileSize(file.size),
  }));
}

function createHint(number: number): HintRecord {
  const template = hintCatalog[(number - 1) % hintCatalog.length];

  return {
    ...template,
    id: createMessageId("hint-record"),
    number,
  };
}

function isCompletionText(text: string) {
  const normalized = text.replace(/\s/g, "");

  return (
    /답.*알겠/.test(normalized) ||
    /이제.*알겠/.test(normalized) ||
    /이해했/.test(normalized) ||
    /이해됐/.test(normalized) ||
    /풀수있/.test(normalized)
  );
}

export function useMockChat() {
  const [messages, setMessages] = useState<ChatMessage[]>(() => [
    ...activeMockSession.messages,
  ]);
  const [isThinking, setIsThinking] = useState(false);
  const [hintHistory, setHintHistory] = useState<HintRecord[]>([]);
  const [status, setStatus] = useState<SessionProgressStatus>("learning");
  const timersRef = useRef<number[]>([]);
  const messagesRef = useRef(messages);
  const hintCountRef = useRef(0);
  const statusRef = useRef<SessionProgressStatus>(status);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    const timers = timersRef.current;

    return () => {
      timers.forEach((timer) => window.clearTimeout(timer));
    };
  }, []);

  const schedule = useCallback((callback: () => void, delay: number) => {
    const timer = window.setTimeout(callback, delay);
    timersRef.current.push(timer);
  }, []);

  const completeSession = useCallback((source: "button" | "message") => {
    if (statusRef.current === "completed") return;

    statusRef.current = "completed";
    setStatus("completed");
    setIsThinking(false);

    const completionMessages: ChatMessage[] = [];

    if (source === "button") {
      completionMessages.push({
        id: createMessageId("user-complete"),
        role: "user",
        content: "답을 알겠어요.",
        type: "answer",
        createdAt: new Date().toISOString(),
      });
    }

    completionMessages.push({
      id: createMessageId("assistant-complete"),
      role: "assistant",
      content:
        "좋아요. 지금처럼 스스로 설명할 수 있으면 이 세션은 마무리해도 됩니다. 다음에는 같은 기준으로 새 라우트를 직접 구분해보세요.",
      type: "feedback",
      createdAt: new Date().toISOString(),
    });

    setMessages((prev) => [...prev, ...completionMessages]);
  }, []);

  const sendMessage = useCallback(
    ({ text, files }: { text: string; files?: File[] }) => {
      const trimmedText = text.trim();
      const attachments = createAttachments(files);

      if (!trimmedText && !attachments?.length) return;
      if (statusRef.current === "completed") return;

      const userQuestionCount = messagesRef.current.filter(
        (message) => message.role === "user",
      ).length;
      const userMessage: ChatMessage = {
        id: createMessageId("user"),
        role: "user",
        content: trimmedText || "첨부한 자료를 보고 설명해볼게요.",
        type: isCompletionText(trimmedText) ? "answer" : "question",
        createdAt: new Date().toISOString(),
        attachments,
      };

      setMessages((prev) => [...prev, userMessage]);

      if (isCompletionText(trimmedText)) {
        schedule(() => completeSession("message"), 500);
        return;
      }

      setIsThinking(true);

      schedule(() => {
        if (statusRef.current === "completed") return;

        const assistantMessage: ChatMessage = {
          id: createMessageId("assistant"),
          role: "assistant",
          content: replyPrompts[userQuestionCount % replyPrompts.length],
          type: "question",
          createdAt: new Date().toISOString(),
        };

        setMessages((prev) => [...prev, assistantMessage]);
        setIsThinking(false);
      }, 900);
    },
    [completeSession, schedule],
  );

  const requestHint = useCallback(() => {
    if (statusRef.current === "completed") return;

    const nextHintNumber = hintCountRef.current + 1;
    const nextHint = createHint(nextHintNumber);

    hintCountRef.current = nextHintNumber;
    setHintHistory((prev) => [...prev, nextHint]);
    setMessages((prev) => [
      ...prev,
      {
        id: createMessageId("hint"),
        role: "assistant",
        content: nextHint.content,
        type: "hint",
        hintLevel: nextHint.depth,
        hintNumber: nextHint.number,
        createdAt: new Date().toISOString(),
      },
    ]);
  }, []);

  const userMessageCount = useMemo(
    () => messages.filter((message) => message.role === "user").length,
    [messages],
  );

  return {
    activeSession: activeMockSession,
    completeSession: () => completeSession("button"),
    hintCount: hintHistory.length,
    hintHistory,
    isCompleted: status === "completed",
    isThinking,
    messages,
    requestHint,
    sendMessage,
    stageIndex: Math.min(userMessageCount, 3),
    status,
  };
}
