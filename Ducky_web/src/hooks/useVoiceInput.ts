"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type VoiceInputStatus =
  | "idle"
  | "listening"
  | "transcribing"
  | "thinking";

const statusLabels: Record<VoiceInputStatus, string> = {
  idle: "음성 입력",
  listening: "듣는 중",
  transcribing: "텍스트 변환 중",
  thinking: "문장 정리 중",
};

const mockTranscript =
  "page.tsx는 실제 화면이고 layout.tsx는 여러 화면을 감싸는 공통 구조라고 이해했어요.";

export function useVoiceInput({
  onTranscript,
}: {
  onTranscript: (text: string) => void;
}) {
  const [status, setStatus] = useState<VoiceInputStatus>("idle");
  const timersRef = useRef<number[]>([]);

  const clearTimers = useCallback(() => {
    timersRef.current.forEach((timer) => window.clearTimeout(timer));
    timersRef.current = [];
  }, []);

  const schedule = useCallback((callback: () => void, delay: number) => {
    const timer = window.setTimeout(callback, delay);
    timersRef.current.push(timer);
  }, []);

  const cancel = useCallback(() => {
    clearTimers();
    setStatus("idle");
  }, [clearTimers]);

  const start = useCallback(() => {
    if (status !== "idle") return;

    clearTimers();
    setStatus("listening");
    schedule(() => setStatus("transcribing"), 1400);
    schedule(() => setStatus("thinking"), 2600);
    schedule(() => {
      onTranscript(mockTranscript);
      setStatus("idle");
    }, 3800);
  }, [clearTimers, onTranscript, schedule, status]);

  useEffect(() => {
    return () => {
      clearTimers();
    };
  }, [clearTimers]);

  return {
    cancel,
    isActive: status !== "idle",
    start,
    status,
    statusLabel: statusLabels[status],
  };
}
