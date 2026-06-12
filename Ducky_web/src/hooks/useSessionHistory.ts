"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getAccessToken } from "@/lib/api/client";
import { getConversation, listConversations } from "@/lib/api/chat";
import {
  toSessionDetail,
  toSessionSummary,
} from "@/lib/chat/transformers";
import type { Session } from "@/types/session";

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "세션 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function useSessionHistory() {
  const isLoadingRef = useRef(false);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingDetail, setIsLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (isLoadingRef.current) {
      return;
    }

    if (!getAccessToken()) {
      setError("로그인 후 세션 기록을 확인할 수 있습니다.");
      setIsLoading(false);
      return;
    }

    isLoadingRef.current = true;
    setIsLoading(true);
    setError(null);

    try {
      const conversations = await listConversations();
      setSessions(
        conversations
          .map(toSessionSummary)
          .sort(
            (a, b) =>
              new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
          ),
      );
    } catch (loadError) {
      setError(getErrorMessage(loadError));
    } finally {
      isLoadingRef.current = false;
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void load();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [load]);

  const selectSession = useCallback(async (session: Session) => {
    setSelectedSession(session);
    if (session.messages.length > 0) {
      return;
    }

    setIsLoadingDetail(true);
    setError(null);

    try {
      const detail = toSessionDetail(await getConversation(session.id));
      setSelectedSession(detail);
      setSessions((prev) =>
        prev.map((item) => (item.id === detail.id ? detail : item)),
      );
    } catch (detailError) {
      setError(getErrorMessage(detailError));
    } finally {
      setIsLoadingDetail(false);
    }
  }, []);

  const counts = useMemo(
    () => ({
      total: sessions.length,
      completed: sessions.filter((session) => session.status === "completed")
        .length,
      inProgress: sessions.filter(
        (session) => session.status === "in_progress",
      ).length,
    }),
    [sessions],
  );

  return {
    counts,
    error,
    isLoading,
    isLoadingDetail,
    retry: load,
    selectSession,
    selectedSession,
    sessions,
    setSelectedSession,
  };
}
