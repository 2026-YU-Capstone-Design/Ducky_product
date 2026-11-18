"use client";

import Link from "next/link";
import {
  AlertCircle,
  ArrowRight,
  Loader2,
  RefreshCw,
  Flame,
  MessageCircle,
  Target,
  Trophy,
  ChevronRight,
  BookOpen,
  Sparkles,
  PlayCircle,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useDashboardData } from "@/hooks/useDashboardData";
import type { LearningLevel, LearningStyle } from "@/types/user";
import type { Session } from "@/types/session";

const levelLabels: Record<LearningLevel, string> = {
  beginner: "Beginner",
  intermediate: "Intermediate",
  advanced: "Advanced",
};

const styleLabels: {
  [Key in keyof LearningStyle]: Record<LearningStyle[Key], string>;
} = {
  processing: { active: "직접 시도형", reflective: "천천히 사고형" },
  expression: { visual: "시각 자료형", verbal: "언어 설명형" },
  understanding: { sequential: "단계 학습형", global: "전체 구조형" },
};

function statusLabel(status: Session["status"]) {
  return status === "completed" ? "완료" : "진행 중";
}

function formatSessionDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function SessionRow({ session }: { session: Session }) {
  const isInProgress = session.status === "in_progress";

  return (
    <Link
      href="/sessions"
      className="group relative flex items-center gap-4 rounded-xl p-3.5 outline-none transition-all duration-200 hover:bg-amber-50/80 focus-visible:bg-amber-50/80 focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-1 dark:hover:bg-white/[0.04]"
      aria-label={`${session.title} 기록 보기`}
    >
      <div className="relative mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-amber-100 to-amber-50 ring-1 ring-amber-200/60 transition-transform duration-200 group-hover:scale-105 dark:from-amber-900/50 dark:to-amber-900/20 dark:ring-amber-800/40">
        <BookOpen
          className="size-4 text-amber-600 dark:text-amber-400"
          aria-hidden="true"
        />
        {isInProgress && (
          <span className="absolute -right-0.5 -top-0.5 flex size-2.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-blue-400 opacity-75" />
            <span className="relative inline-flex size-2.5 rounded-full bg-blue-500 ring-2 ring-white dark:ring-[#1E1B16]" />
          </span>
        )}
      </div>

      <div className="min-w-0 flex-1">
        <p className="line-clamp-1 text-sm font-bold text-gray-900 break-keep dark:text-white">
          {session.title}
        </p>
        <p className="mt-0.5 line-clamp-1 text-xs font-medium text-amber-600 dark:text-amber-400">
          {session.topic}
        </p>
        <div className="mt-1.5 flex items-center gap-2.5 text-[11px] text-gray-400 dark:text-gray-500">
          <span>힌트 {session.hintCount}회</span>
          <span aria-hidden="true">·</span>
          <span>{session.messageCount}개 메시지</span>
          <span aria-hidden="true">·</span>
          <span>{formatSessionDate(session.updatedAt)}</span>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-3">
        <span
          className={`rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums ${
            isInProgress
              ? "bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-300"
              : "bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-300"
          }`}
        >
          {statusLabel(session.status)}
        </span>
        <ChevronRight
          className="size-5 text-gray-300 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-amber-500 dark:text-white/20"
          aria-hidden="true"
        />
      </div>
    </Link>
  );
}

export function HomeDashboard() {
  const { error, isLoading, recentSessions, retry, sessions, summary, user } =
    useDashboardData();
  const activeSession =
    sessions.find((session) => session.status === "in_progress") ??
    recentSessions[0];

  const styleRows = user
    ? [
        {
          label: "처리 방식",
          value: styleLabels.processing[user.learningStyle.processing],
        },
        {
          label: "표현 선호",
          value: styleLabels.expression[user.learningStyle.expression],
        },
        {
          label: "이해 구조",
          value: styleLabels.understanding[user.learningStyle.understanding],
        },
      ]
    : [];

  const statCards = [
    {
      label: "연속 학습",
      value: `${summary.currentStreakDays}일`,
      icon: Flame,
    },
    {
      label: "완료 세션",
      value: `${summary.completedSessions}개`,
      icon: Trophy,
    },
    {
      label: "평균 힌트",
      value: `${summary.averageHintCount}회`,
      icon: MessageCircle,
    },
    {
      label: "현재 레벨",
      value: user ? levelLabels[user.level] : "-",
      icon: Target,
    },
  ];

  return (
    <div className="flex min-h-full w-full flex-1 flex-col bg-[#FFFCF3] transition-colors dark:bg-[#16130A]">
      <section className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 py-10 sm:px-8 lg:px-10">
        <div className="relative overflow-hidden rounded-3xl border border-amber-100/80 bg-gradient-to-br from-amber-50 via-white to-yellow-50 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_8px_24px_-12px_rgba(217,119,6,0.15)] dark:border-amber-900/30 dark:from-[#1E1B16] dark:via-[#1A1714] dark:to-[#1E1B10] dark:shadow-none">
          <div className="pointer-events-none absolute -right-16 -top-16 h-56 w-56 rounded-full bg-amber-200/40 blur-3xl dark:bg-amber-600/10" />
          <div className="pointer-events-none absolute -bottom-10 left-1/4 h-40 w-40 rounded-full bg-yellow-200/40 blur-2xl dark:bg-yellow-700/10" />

          <div className="relative flex flex-col">
            <div className="flex flex-col justify-between gap-8 px-6 py-8 sm:px-8 sm:py-10">
              <div>
                <div className="mb-4 inline-flex items-center gap-1.5 rounded-full bg-amber-100/80 px-3 py-1 ring-1 ring-amber-200/60 dark:bg-amber-900/40 dark:ring-amber-800/40">
                  <Sparkles
                    className="size-3 text-amber-500"
                    aria-hidden="true"
                  />
                  <span className="text-xs font-semibold tracking-wide text-amber-700 dark:text-amber-300">
                    오늘의 학습
                  </span>
                </div>
                <h1 className="max-w-2xl text-3xl font-extrabold leading-[1.15] tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
                  {user?.name ?? "Ducky"}님,{" "}
                  <span className="bg-gradient-to-r from-amber-500 to-orange-500 bg-clip-text text-transparent">
                    이어서 설명
                  </span>
                  해볼까요?
                </h1>
                <p className="mt-3 max-w-sm text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                  Ducky가 답을 바로 주기보다 생각을 정리할 수 있는 질문을
                  이어갑니다.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-3">
                <Link
                  href="/chat"
                  className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-gradient-to-b from-amber-400 to-amber-500 px-5 text-sm font-bold text-gray-900 shadow-[0_1px_2px_rgba(0,0,0,0.06),0_4px_14px_-4px_rgba(217,119,6,0.45)] transition-all duration-200 hover:shadow-[0_1px_2px_rgba(0,0,0,0.06),0_6px_18px_-4px_rgba(217,119,6,0.55)] hover:brightness-[1.03] active:scale-[0.98] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-400"
                >
                  새 대화 시작
                  <ArrowRight
                    className="size-4 transition-transform duration-200 group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </Link>
              </div>
            </div>

            <div className="relative grid grid-cols-2 border-t border-amber-200/70 bg-amber-100/50 sm:grid-cols-4 dark:border-amber-900/40 dark:bg-black/10">
              {statCards.map(({ label, value, icon: Icon }, i) => (
                <div
                  key={label}
                  className={`relative flex flex-col gap-3.5 px-5 py-5 sm:px-6 sm:py-6 ${
                    i % 2 === 0
                      ? "border-r border-amber-200/60 sm:border-r-0"
                      : ""
                  } ${i < 2 ? "border-b border-amber-200/60 sm:border-b-0" : ""} ${
                    i > 0 ? "sm:border-l sm:border-amber-200/60" : ""
                  } dark:border-amber-900/30`}
                >
                  <Icon
                    className="size-6 text-amber-600/80 dark:text-amber-400/80"
                    strokeWidth={1.6}
                    aria-hidden="true"
                  />
                  <div>
                    <p className="text-xl font-extrabold tabular-nums leading-none tracking-tight text-gray-900 sm:text-2xl dark:text-white">
                      {isLoading || error ? "—" : value}
                    </p>
                    <p className="mt-2.5 text-[11px] font-medium text-gray-500 dark:text-gray-400">
                      {label}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
        {(isLoading || error) && (
          <div className="mt-6 rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300">
            {isLoading ? (
              <span className="inline-flex items-center gap-2">
                <Loader2 className="size-4 animate-spin text-[#B88700]" />
                대시보드 데이터를 불러오는 중입니다.
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
                  onClick={() => void retry()}
                >
                  <RefreshCw className="size-4" />
                  다시 시도
                </Button>
              </div>
            )}
          </div>
        )}

        <div className="mt-6 grid flex-1 gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
          <div className="flex flex-col rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
            <div className="flex items-center justify-between border-b border-amber-100/80 px-5 py-4 dark:border-white/8">
              <h2 className="text-base font-bold tracking-tight text-gray-900 dark:text-white">
                최근 학습 세션
              </h2>
              {recentSessions.length > 0 && (
                <Link
                  href="/sessions"
                  className="group flex items-center gap-2 text-xs font-semibold text-amber-600 transition-colors hover:text-amber-700 dark:text-amber-400"
                >
                  전체 보기
                  <ChevronRight
                    className="size-5 transition-transform group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </Link>
              )}
            </div>

            {recentSessions.length > 0 ? (
              <div className="flex flex-col divide-y divide-gray-100 px-1.5 dark:divide-white/10">
                {recentSessions.map((session) => (
                  <SessionRow key={session.id} session={session} />
                ))}
              </div>
            ) : !isLoading && !error ? (
              <div className="flex flex-1 flex-col items-center justify-center gap-4 py-16 text-center">
                <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-gradient-to-br from-amber-100 to-amber-50 ring-1 ring-amber-200/60 dark:from-amber-900/40 dark:to-amber-900/10 dark:ring-amber-800/30">
                  <BookOpen
                    className="size-6 text-amber-500"
                    aria-hidden="true"
                  />
                </div>
                <div>
                  <p className="text-sm font-semibold text-gray-700 dark:text-gray-300">
                    아직 학습 세션이 없어요
                  </p>
                  <p className="mt-1 text-xs text-gray-400">
                    첫 대화를 시작해보세요
                  </p>
                </div>
                <Link
                  href="/chat"
                  className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-b from-amber-400 to-amber-500 px-4 py-2 text-sm font-bold text-gray-900 shadow-sm transition-all hover:shadow-md active:scale-[0.98]"
                >
                  대화 시작하기
                  <ArrowRight className="size-3.5" aria-hidden="true" />
                </Link>
              </div>
            ) : null}
          </div>

          <div className="flex flex-col gap-4">
            <div className="rounded-2xl border border-amber-100/80 bg-white shadow-[0_1px_2px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
              <div className="border-b border-amber-100/80 px-5 py-4 dark:border-white/8">
                <h2 className="text-base font-bold tracking-tight text-gray-900 dark:text-white">
                  학습 유형
                </h2>
                <p className="mt-0.5 text-xs text-gray-400">
                  {user?.name ?? "Ducky"}님의 학습 스타일
                </p>
              </div>

              <dl className="divide-y divide-amber-50 dark:divide-white/5">
                {styleRows.map((row) => (
                  <div
                    key={row.label}
                    className="flex items-center justify-between px-5 py-3.5"
                  >
                    <dt className="text-sm text-gray-500 dark:text-gray-400">
                      {row.label}
                    </dt>
                    <dd className="rounded-lg bg-gradient-to-b from-amber-50 to-amber-100/70 px-2.5 py-1 text-xs font-bold text-amber-700 ring-1 ring-amber-200/50 dark:from-amber-900/30 dark:to-amber-900/20 dark:text-amber-300 dark:ring-amber-800/30">
                      {row.value}
                    </dd>
                  </div>
                ))}
              </dl>
            </div>

            {activeSession && (
              <Link
                href={activeSession.status === "in_progress" ? "/chat" : "/sessions"}
                className="group relative flex flex-col gap-3 overflow-hidden rounded-2xl border border-amber-200/80 bg-gradient-to-br from-amber-50 to-yellow-50/60 p-5 shadow-[0_1px_2px_rgba(0,0,0,0.03)] transition-all duration-200 hover:-translate-y-0.5 hover:border-amber-400 hover:shadow-[0_8px_24px_-8px_rgba(217,119,6,0.3)] active:scale-[0.99] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-400 dark:border-amber-800/40 dark:from-amber-900/20 dark:to-yellow-900/10"
              >
                <div
                  className="pointer-events-none absolute -right-10 -top-10 h-32 w-32 rounded-full bg-amber-300/30 blur-2xl"
                  aria-hidden="true"
                />

                <div className="relative flex items-center justify-between">
                  <span className="inline-flex items-center gap-1.5 text-xs font-bold tracking-wide text-amber-600 dark:text-amber-400">
                    <PlayCircle className="size-3.5" aria-hidden="true" />
                    {activeSession.status === "in_progress" ? "이어서 대화" : "학습 기록 보기"}
                  </span>
                  <ChevronRight
                    className="size-4 text-amber-400 transition-transform duration-200 group-hover:translate-x-0.5"
                    aria-hidden="true"
                  />
                </div>

                <div className="relative">
                  <p className="line-clamp-2 text-sm font-bold leading-snug text-gray-900 break-keep dark:text-white">
                    {activeSession.title}
                  </p>
                  <p className="mt-1.5 line-clamp-2 text-xs leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                    {activeSession.summary}
                  </p>
                </div>


                <div className="relative flex items-center gap-2 text-xs text-gray-400">
                  <span
                    className={`rounded-full px-2 py-0.5 font-semibold ${
                      activeSession.status === "in_progress"
                        ? "bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-300"
                        : "bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-300"
                    }`}
                  >
                    {statusLabel(activeSession.status)}
                  </span>
                  <span>힌트 {activeSession.hintCount}회</span>
                  <span>{activeSession.messageCount}개 메시지</span>
                </div>
              </Link>
            )}
          </div>
        </div>
      </section>
    </div>
  );
}
