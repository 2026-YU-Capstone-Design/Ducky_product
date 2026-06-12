import Link from "next/link";
import {
  ArrowRight,
  Flame,
  MessageCircle,
  Target,
  Trophy,
} from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { mockAnalysis, mockSessions, mockUser } from "@/data/mockData";
import type { LearningLevel, LearningStyle } from "@/types/user";
import type { Session } from "@/types/session";

const levelLabels: Record<LearningLevel, string> = {
  beginner: "beginner",
  intermediate: "intermediate",
  advanced: "advanced",
};

const styleLabels: {
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

const styleRows = [
  {
    label: "처리 방식",
    value: styleLabels.processing[mockUser.learningStyle.processing],
  },
  {
    label: "표현 선호",
    value: styleLabels.expression[mockUser.learningStyle.expression],
  },
  {
    label: "이해 구조",
    value: styleLabels.understanding[mockUser.learningStyle.understanding],
  },
];

const recentSessions = [...mockSessions]
  .sort(
    (a, b) =>
      new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  )
  .slice(0, 2);

const activeSession =
  recentSessions.find((session) => session.status === "in_progress") ??
  recentSessions[0];

const statCards = [
  {
    label: "연속 학습",
    value: `${mockAnalysis.currentStreakDays}일`,
    icon: Flame,
    tone: "text-[#B88700]",
  },
  {
    label: "완료 세션",
    value: `${mockAnalysis.completedSessions}개`,
    icon: Trophy,
    tone: "text-[#B88700]",
  },
  {
    label: "평균 힌트",
    value: `${mockAnalysis.averageHintCount}회`,
    icon: MessageCircle,
    tone: "text-[#B88700]",
  },
  {
    label: "현재 레벨",
    value: levelLabels[mockUser.level],
    icon: Target,
    tone: "text-[#B88700]",
  },
];

function statusLabel(status: Session["status"]) {
  return status === "completed" ? "완료" : "진행 중";
}

function formatSessionDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function SessionCard({ session }: { session: Session }) {
  return (
    <Link
      href="/sessions"
      className="group block min-w-0 rounded-lg outline-none focus-visible:ring-3 focus-visible:ring-[#FECA43]/50"
      aria-label={`${session.title} 기록 보기`}
    >
      <Card className="h-full rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors group-hover:border-[#FECA43] dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
        <CardContent className="flex h-full flex-col gap-4 p-4 sm:p-5">
          <div className="flex items-start justify-between gap-3">
            <p className="min-w-0 text-xs font-semibold text-[#B88700]">
              {session.topic}
            </p>
            <span className="shrink-0 rounded-full bg-[#FFF1BC] px-2.5 py-1 text-xs font-semibold text-[#6B5200] dark:bg-[#2A251D] dark:text-[#FECA43]">
              {statusLabel(session.status)}
            </span>
          </div>

          <div className="min-w-0 space-y-2">
            <h3 className="line-clamp-2 text-base font-bold leading-snug text-gray-950 break-keep dark:text-white">
              {session.title}
            </h3>
            <p className="line-clamp-2 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-300">
              {session.summary}
            </p>
          </div>

          <dl className="mt-auto grid grid-cols-3 gap-3 text-sm">
            <div>
              <dt className="text-xs text-gray-400">힌트</dt>
              <dd className="font-bold text-gray-950 dark:text-white">
                {session.hintCount}회
              </dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">메시지</dt>
              <dd className="font-bold text-gray-950 dark:text-white">
                {session.messageCount}개
              </dd>
            </div>
            <div>
              <dt className="text-xs text-gray-400">최근</dt>
              <dd className="font-bold text-gray-950 dark:text-white">
                {formatSessionDate(session.updatedAt)}
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>
    </Link>
  );
}

export function HomeDashboard() {
  return (
    <section className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 py-8 sm:px-8 lg:px-10">
      <div className="flex flex-col gap-6 border-b border-[#E7DDC8] pb-8 dark:border-white/10 lg:flex-row lg:items-end lg:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-[#B88700]">오늘의 학습</p>
          <h1 className="mt-3 max-w-3xl text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
            {mockUser.name}님, 이어서 설명해볼까요?
          </h1>
          <p className="mt-4 max-w-3xl text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300">
            최근 질문 흐름을 바탕으로 Ducky가 답을 바로 주기보다 생각을
            정리할 수 있는 질문을 이어갑니다.
          </p>
        </div>

        <Link
          href="/chat"
          className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-lg bg-[#FECA43] px-5 text-sm font-bold text-[#2E2A22] shadow-sm transition-colors hover:bg-[#F5B522] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] sm:w-auto"
        >
          새 대화 시작
          <ArrowRight className="size-4" aria-hidden="true" />
        </Link>
      </div>

      <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {statCards.map((stat) => {
          const Icon = stat.icon;

          return (
            <Card
              key={stat.label}
              className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none"
            >
              <CardContent className="flex min-h-32 flex-col justify-between p-5">
                <Icon className={`size-5 ${stat.tone}`} aria-hidden="true" />
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {stat.label}
                  </p>
                  <p className="mt-1 text-2xl font-bold tracking-tight text-gray-950 dark:text-white">
                    {stat.value}
                  </p>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
        <div className="min-w-0">
          <div className="mb-4 flex items-center justify-between gap-4">
            <h2 className="text-xl font-bold tracking-tight text-gray-950 dark:text-white">
              최근 학습 세션
            </h2>
            <Link
              href="/sessions"
              className="shrink-0 text-sm font-semibold text-[#B88700] transition-colors hover:text-[#6B5200] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43]"
            >
              전체 보기
            </Link>
          </div>

          <div className="grid gap-4 xl:grid-cols-2">
            {recentSessions.map((session) => (
              <SessionCard key={session.id} session={session} />
            ))}
          </div>
        </div>

        <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
          <CardContent className="p-5">
            <h2 className="text-xl font-bold tracking-tight text-gray-950 dark:text-white">
              학습 유형
            </h2>

            <dl className="mt-5 divide-y divide-[#E7DDC8] dark:divide-white/10">
              {styleRows.map((row) => (
                <div
                  key={row.label}
                  className="flex items-center justify-between gap-4 py-4 first:pt-0"
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

            {activeSession && (
              <div className="mt-5 border-t border-[#E7DDC8] pt-5 dark:border-white/10">
                <p className="text-xs font-semibold text-[#B88700]">
                  이어하기
                </p>
                <h3 className="mt-2 line-clamp-2 text-base font-bold leading-snug text-gray-950 break-keep dark:text-white">
                  {activeSession.title}
                </h3>
                <p className="mt-2 line-clamp-3 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-300">
                  {activeSession.summary}
                </p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
