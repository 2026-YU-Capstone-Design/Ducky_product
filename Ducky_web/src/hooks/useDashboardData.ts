"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getMe } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/api/client";
import { listConversations } from "@/lib/api/chat";
import { toSessionSummary } from "@/lib/chat/transformers";
import type { Session } from "@/types/session";
import type { User } from "@/types/user";

function getErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function useDashboardData() {
  const isLoadingRef = useRef(false);
  const [user, setUser] = useState<User | null>(null);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (isLoadingRef.current) {
      return;
    }

    if (!getAccessToken()) {
      setError("로그인 후 데이터를 확인할 수 있습니다.");
      setIsLoading(false);
      return;
    }

    isLoadingRef.current = true;
    setIsLoading(true);
    setError(null);

    try {
      const [nextUser, conversations] = await Promise.all([
        getMe(),
        listConversations(),
      ]);
      const nextSessions = conversations
        .map(toSessionSummary)
        .sort(
          (a, b) =>
            new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
        );

      setUser(nextUser);
      setSessions(nextSessions);
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

  const summary = useMemo(() => {
    const completedSessions = sessions.filter(
      (session) => session.status === "completed",
    );
    const averageHintCount =
      sessions.length === 0
        ? 0
        : sessions.reduce((sum, session) => sum + session.hintCount, 0) /
          sessions.length;

    return {
      totalSessions: sessions.length,
      completedSessions: completedSessions.length,
      inProgressSessions: sessions.filter(
        (session) => session.status === "in_progress",
      ).length,
      currentStreakDays: user?.streakDays ?? 0,
      averageHintCount: Number(averageHintCount.toFixed(1)),
    };
  }, [sessions, user?.streakDays]);

  return {
    error,
    isLoading,
    recentSessions: sessions.slice(0, 2),
    retry: load,
    sessions,
    summary,
    user,
  };
}
