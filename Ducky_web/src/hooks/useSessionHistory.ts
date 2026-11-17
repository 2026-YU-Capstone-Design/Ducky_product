"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getAccessToken } from "@/lib/api/client";
import { deleteConversation, getConversation, listConversations } from "@/lib/api/chat";
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
  const [deletingSessionId, setDeletingSessionId] = useState<string | null>(null);
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

  const deleteSession = useCallback(async (session: Session) => {
    if (!window.confirm(`"${session.title}" 대화를 삭제할까요?`)) {
      return;
    }

    setDeletingSessionId(session.id);
    setError(null);

    try {
      await deleteConversation(session.id);
      setSessions((prev) => prev.filter((item) => item.id !== session.id));
      setSelectedSession((current) =>
        current?.id === session.id ? null : current,
      );
    } catch (deleteError) {
      setError(getErrorMessage(deleteError));
    } finally {
      setDeletingSessionId(null);
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
    deleteSession,
    deletingSessionId,
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
