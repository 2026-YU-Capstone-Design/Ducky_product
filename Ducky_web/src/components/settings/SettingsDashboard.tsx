"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  ArrowRight,
  AlertCircle,
  Bell,
  Brain,
  Loader2,
  Moon,
  RefreshCw,
  UserRound,
  type LucideIcon,
} from "lucide-react";
import { MaterialLibrary } from "@/components/settings/MaterialLibrary";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { getMe } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/api/client";
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

function ProfileRow({
  label,
  value,
}: {
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-[#E7DDC8] py-3 last:border-b-0 last:pb-0 dark:border-white/10">
      <dt className="text-sm text-gray-500 dark:text-gray-400">{label}</dt>
      <dd className="min-w-0 text-right text-sm font-bold text-gray-950 dark:text-white">
        {value}
      </dd>
    </div>
  );
}

function SectionTitle({
  children,
  icon: Icon,
}: {
  children: string;
  icon: LucideIcon;
}) {
  return (
    <div className="flex items-center gap-2">
      <Icon className="size-5 text-[#B88700]" aria-hidden="true" />
      <h2 className="text-lg font-bold tracking-tight text-gray-950 dark:text-white">
        {children}
      </h2>
    </div>
  );
}

export function SettingsDashboard() {
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
    <section className="mx-auto flex w-full max-w-[944px] flex-1 flex-col px-5 py-9 transition-colors sm:px-8 lg:px-10">
      <div className="border-b border-[#E7DDC8] pb-7 dark:border-white/10">
        <p className="text-sm font-semibold text-[#B88700]">설정</p>
        <h1 className="mt-3 text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
          학습 환경을 조정합니다
        </h1>
        <p className="mt-4 text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300">
          로그인된 사용자 정보와 학습 성향을 백엔드 기준으로 표시합니다.
        </p>
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

      <div className="mt-8 grid gap-4 lg:grid-cols-2">
        <Card className="min-h-[196px] rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
          <CardContent className="p-5">
            <SectionTitle icon={UserRound}>프로필</SectionTitle>

            <dl className="mt-4">
              <ProfileRow label="이름" value={user.name || "-"} />
              <ProfileRow label="이메일" value={user.email || "-"} />
              <ProfileRow label="레벨" value={levelLabels[user.level]} />
            </dl>
          </CardContent>
        </Card>

        <Card className="min-h-[196px] rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
          <CardContent className="p-5">
            <SectionTitle icon={Moon}>화면 설정</SectionTitle>

            <div className="mt-5 flex items-center justify-between gap-6">
              <div className="min-w-0">
                <label
                  className="text-sm font-bold text-gray-950 dark:text-white"
                  htmlFor="settings-dark-mode"
                >
                  다크 모드
                </label>
                <p className="mt-2 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                  장시간 학습 시 눈부심을 줄이는 설정입니다.
                </p>
              </div>
              <Switch
                checked={darkMode}
                className="data-checked:bg-[#FECA43] data-unchecked:bg-[#E3E7ED]"
                id="settings-dark-mode"
                onCheckedChange={setDarkMode}
              />
            </div>
          </CardContent>
        </Card>

        <Card className="min-h-[200px] rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
          <CardContent className="p-5">
            <SectionTitle icon={Bell}>알림</SectionTitle>

            <div className="mt-5 flex items-center justify-between gap-6">
              <div className="min-w-0">
                <label
                  className="text-sm font-bold text-gray-950 dark:text-white"
                  htmlFor="settings-study-reminder"
                >
                  학습 리마인더
                </label>
                <p className="mt-2 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                  매일 한 번 이어서 학습할 시간을 알려줍니다.
                </p>
              </div>
              <Switch
                checked={studyReminder}
                className="data-checked:bg-[#FECA43] data-unchecked:bg-[#E3E7ED]"
                id="settings-study-reminder"
                onCheckedChange={setStudyReminder}
              />
            </div>
          </CardContent>
        </Card>

        <Card className="min-h-[200px] rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
          <CardContent className="p-5">
            <SectionTitle icon={Brain}>학습 스타일</SectionTitle>

            <p className="mt-4 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
              Ducky가 답변 흐름을 맞출 때 참고하는 현재 학습 성향입니다.
            </p>

            <dl className="mt-4 divide-y divide-[#E7DDC8] dark:divide-white/10">
              {learningStyleRows.map((row) => (
                <div
                  key={row.label}
                  className="flex items-center justify-between gap-4 py-3 first:pt-0"
                >
                  <dt className="text-sm text-gray-500 dark:text-gray-400">
                    {row.label}
                  </dt>
                  <dd className="text-right text-sm font-bold text-gray-950 dark:text-white">
                    {row.value}
                  </dd>
                </div>
              ))}
            </dl>

            <Link
              href="/onboarding"
              className="mt-4 inline-flex min-h-10 w-full items-center justify-center gap-2 rounded-lg bg-[#FECA43] px-4 text-sm font-bold text-[#2E2A22] transition-colors hover:bg-[#F5B522] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43]"
            >
              다시 진단하기
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </CardContent>
        </Card>

        <MaterialLibrary />
      </div>
    </section>
  );
}
