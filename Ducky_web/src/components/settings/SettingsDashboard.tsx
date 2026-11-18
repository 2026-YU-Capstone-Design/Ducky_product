"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  AlertCircle,
  Bell,
  Brain,
  Loader2,
  LogOut,
  Moon,
  RefreshCw,
  UserRound,
} from "lucide-react";
import { MaterialLibrary } from "@/components/settings/MaterialLibrary";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { getMe } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/api/client";
import { clearAuthSession } from "@/lib/auth/logout";
import { defaultUser, persistUser } from "@/lib/auth/storage";
import type { LearningLevel, LearningStyle } from "@/types/user";

const levelLabels: Record<LearningLevel, string> = {
  advanced: "advanced",
  beginner: "beginner",
  intermediate: "intermediate",
};

const DARK_MODE_STORAGE_KEY = "ducky.settings.darkMode";

const learningStyleLabels: {
  [Key in keyof LearningStyle]: Record<LearningStyle[Key], string>;
} = {
  processing: {
    active: "직접 시도형",
    reflective: "천천히 사고형",
  },
  expression: {
    visual: "시각 자료형",
    verbal: "언어 설명형",
  },
  understanding: {
    sequential: "단계 학습형",
    global: "전체 구조형",
  },
};

function getInitialDarkMode() {
  if (typeof window === "undefined") {
    return false;
  }

  return window.localStorage.getItem(DARK_MODE_STORAGE_KEY) === "true";
}

export function SettingsDashboard() {
  const router = useRouter();
  const [darkMode, setDarkMode] = useState(getInitialDarkMode);
  const [studyReminder, setStudyReminder] = useState(true);
  const [user, setUser] = useState(defaultUser);
  const [isLoadingUser, setIsLoadingUser] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    document.documentElement.classList.toggle("dark", darkMode);
    window.localStorage.setItem(DARK_MODE_STORAGE_KEY, String(darkMode));
  }, [darkMode]);

  const loadUser = useCallback(async () => {
    if (!getAccessToken()) {
      setError("로그인 후 사용자 정보를 확인할 수 있습니다.");
      setIsLoadingUser(false);
      return;
    }

    setIsLoadingUser(true);
    setError(null);

    try {
      const nextUser = await getMe();
      setUser(nextUser);
      persistUser(nextUser);
    } catch (loadError) {
      setError(
        loadError instanceof Error
          ? loadError.message
          : "사용자 정보를 불러오지 못했습니다.",
      );
    } finally {
      setIsLoadingUser(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      void loadUser();
    }, 0);

    return () => window.clearTimeout(timer);
  }, [loadUser]);

  const handleLogout = useCallback(() => {
    clearAuthSession();
    setUser(defaultUser);
    router.replace("/");
  }, [router]);

  const learningStyleRows = [
    {
      label: "처리 방식",
      value: learningStyleLabels.processing[user.learningStyle.processing],
    },
    {
      label: "표현 선호",
      value: learningStyleLabels.expression[user.learningStyle.expression],
    },
    {
      label: "이해 구조",
      value:
        learningStyleLabels.understanding[user.learningStyle.understanding],
    },
  ];

  return (
    <div className="flex min-h-full w-full flex-1 flex-col bg-[#FFFCF3] transition-colors dark:bg-[#16130A]">
      <section className="mx-auto flex w-full max-w-[944px] flex-1 flex-col px-5 py-8 sm:px-8 lg:px-10">
        <div className="mb-2 py-6">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 mb-3 text-[10px] font-bold tracking-[0.12em] text-amber-700 uppercase dark:bg-amber-900/40 dark:text-amber-300">
            <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
            Settings
          </span>
          <h1 className="mt-3 text-[30px] font-black leading-[1.15] tracking-[-1px] text-gray-950 dark:text-white sm:text-[40px]">
            학습 환경을 조정하세요
            <span className="text-amber-400">.</span>
          </h1>
          <Button
            type="button"
            variant="outline"
            className="mt-4 h-10 shrink-0 gap-2 border-[#E7DDC8] bg-white px-4 font-bold text-gray-600 hover:bg-red-50 hover:text-red-600 dark:border-white/10 dark:bg-[#24211D] dark:text-gray-200 dark:hover:bg-red-500/10 dark:hover:text-red-200"
            onClick={handleLogout}
          >
            <LogOut className="size-4" aria-hidden="true" />
            로그아웃
          </Button>
          <div className="mt-8 border-t border-amber-200/60 dark:border-amber-800/40" />
        </div>
        {(isLoadingUser || error) && (
          <div className="mt-6 rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300">
            {isLoadingUser ? (
              <span className="inline-flex items-center gap-2">
                <Loader2 className="size-4 animate-spin text-[#B88700]" />
                사용자 정보를 불러오는 중입니다.
              </span>
            ) : (
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <span className="inline-flex min-w-0 items-start gap-2 text-red-600 dark:text-red-200">
                  <AlertCircle className="mt-0.5 size-4 shrink-0" />
                  <span className="break-keep">{error}</span>
                </span>
                <Button
                  type="button"
                  variant="outline"
                  className="h-9 shrink-0 gap-2"
                  onClick={() => void loadUser()}
                >
                  <RefreshCw className="size-4" />
                  다시 시도
                </Button>
              </div>
            )}
          </div>
        )}
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="flex flex-col gap-4">
            <div className="overflow-hidden rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16]">
              <div className="flex h-12 items-center gap-1.5 border-b border-amber-100/80 px-4 dark:border-white/8">
                <UserRound
                  className="size-3.5 text-amber-500"
                  strokeWidth={1.8}
                />
                <h2 className="text-xs font-bold tracking-widest text-amber-700 uppercase dark:text-amber-400">
                  프로필
                </h2>
              </div>
              <dl className="flex flex-col divide-y divide-amber-50 dark:divide-white/[0.06]">
                {[
                  { label: "이름", value: user.name || "—" },
                  { label: "이메일", value: user.email || "—" },
                  { label: "레벨", value: levelLabels[user.level] },
                ].map((row) => (
                  <div
                    key={row.label}
                    className="flex items-center justify-between gap-4 px-5 py-3"
                  >
                    <dt className="text-[10px] font-semibold uppercase tracking-widest text-[#876c45] dark:text-amber-300/75">
                      {row.label}
                    </dt>
                    <dd className="min-w-0 break-all text-right text-sm font-bold text-gray-950 dark:text-white">
                      {isLoadingUser || error ? "—" : row.value}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>

            <div className="overflow-hidden rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16]">
              <div className="flex h-12 items-center gap-1.5 border-b border-amber-100/80 px-4 dark:border-white/8">
                <Moon className="size-3.5 text-amber-500" strokeWidth={1.8} />
                <h2 className="text-xs font-bold tracking-widest text-amber-700 uppercase dark:text-amber-400">
                  화면 설정
                </h2>
              </div>
              <div className="flex items-center justify-between gap-6 px-5 py-4">
                <div className="min-w-0">
                  <label
                    className="text-sm font-bold text-gray-950 dark:text-white"
                    htmlFor="settings-dark-mode"
                  >
                    다크 모드
                  </label>
                  <p className="mt-1 text-xs leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                    장시간 학습 시 눈부심을 줄이는 설정입니다.
                  </p>
                </div>
                <Switch
                  aria-label="다크 모드"
                checked={darkMode}
                  className="data-checked:bg-[#2E2A22] data-unchecked:bg-amber-100 shrink-0 dark:data-checked:bg-[#FECA43] dark:data-unchecked:bg-white/10"
                  id="settings-dark-mode"
                  onCheckedChange={setDarkMode}
                />
              </div>
            </div>

            <div className="overflow-hidden rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16]">
              <div className="flex h-12 items-center gap-1.5 border-b border-amber-100/80 px-4 dark:border-white/8">
                <Bell className="size-3.5 text-amber-500" strokeWidth={1.8} />
                <h2 className="text-xs font-bold tracking-widest text-amber-700 uppercase dark:text-amber-400">
                  알림
                </h2>
              </div>
              <div className="flex items-center justify-between gap-6 px-5 py-4">
                <div className="min-w-0">
                  <label
                    className="text-sm font-bold text-gray-950 dark:text-white"
                    htmlFor="settings-study-reminder"
                  >
                    학습 리마인더
                  </label>
                  <p className="mt-1 text-xs leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                    매일 한 번 이어서 학습할 시간을 알려줍니다.
                  </p>
                </div>
                <Switch
                  aria-label="학습 리마인더"
                checked={studyReminder}
                  className="data-checked:bg-[#2E2A22] data-unchecked:bg-amber-100 shrink-0 dark:data-checked:bg-[#FECA43] dark:data-unchecked:bg-white/10"
                  id="settings-study-reminder"
                  onCheckedChange={setStudyReminder}
                />
              </div>
            </div>
          </div>

          <div className="flex flex-col overflow-hidden rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16]">
            <div className="flex h-12 items-center gap-1.5 border-b border-amber-100/80 px-4 dark:border-white/8">
              <Brain className="size-3.5 text-amber-500" strokeWidth={1.8} />
              <h2 className="text-xs font-bold tracking-widest text-amber-700 uppercase dark:text-amber-400">
                학습 스타일
              </h2>
            </div>
            <div className="flex flex-1 flex-col p-5">
              <p className="text-xs leading-relaxed text-[#876c45] break-keep dark:text-amber-300/75">
                Ducky가 답변 흐름을 맞출 때 참고하는 현재 학습 성향입니다.
              </p>
              <dl className="mt-3 flex flex-1 flex-col gap-2">
                {learningStyleRows.map((row) => (
                  <div
                    key={row.label}
                    className="flex flex-1 flex-col justify-center gap-1.5 rounded-xl bg-amber-50/60 px-4 py-3.5 dark:bg-white/[0.03]"
                  >
                    <div className="flex items-center justify-between gap-3">
                      <dt className="text-[11px] font-bold uppercase tracking-widest text-[#876c45] dark:text-amber-300/75">
                        {row.label}
                      </dt>
                      <dd className="shrink-0 rounded-lg bg-gradient-to-b from-amber-50 to-amber-100/70 px-2.5 py-0.5 text-[11px] font-bold text-amber-700 ring-1 ring-amber-200/50 dark:from-amber-900/30 dark:to-amber-900/20 dark:text-amber-300 dark:ring-amber-800/30">
                        {isLoadingUser || error ? "—" : row.value}
                      </dd>
                    </div>
                  </div>
                ))}
              </dl>
              <Link
                href="/onboarding"
                className="mt-4 inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-xl bg-[#2E2A22] px-4 text-sm font-bold text-white transition-colors hover:bg-[#1a1814] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#2E2A22] dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#F5B522]"
              >
                다시 진단하기
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </div>
          </div>

          <MaterialLibrary />
        </div>
      </section>
    </div>
  );
}
