"use client";

import { useMemo } from "react";
import {
  AlertCircle,
  Brain,
  Loader2,
  RefreshCw,
  Target,
  TrendingUp,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useDashboardData } from "@/hooks/useDashboardData";
import { cn } from "@/lib/utils";
import type {
  AnalysisSummary,
  HintUsage,
  ImprovementPoint,
  TopicProgress,
} from "@/types/analysis";
import type { Session } from "@/types/session";
import type { LearningStyle, User } from "@/types/user";

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

const styleDescriptions: {
  [Key in keyof LearningStyle]: Record<LearningStyle[Key], string>;
} = {
  processing: {
    active: "문제를 먼저 풀어보며 흐름을 잡는 편입니다.",
    reflective: "조건과 원인을 정리한 뒤 접근할 때 안정적입니다.",
  },
  expression: {
    visual: "구조와 관계가 보이면 개념을 더 빠르게 정리합니다.",
    verbal: "개념을 말이나 글로 풀어낼 때 이해가 깊어집니다.",
  },
  understanding: {
    sequential: "작은 단계가 이어질 때 답변 품질이 안정됩니다.",
    global: "전체 그림을 먼저 잡으면 세부 흐름을 더 잘 연결합니다.",
  },
};

const priorityLabels: Record<ImprovementPoint["priority"], string> = {
  high: "중요",
  medium: "보통",
  low: "낮음",
};

const hintLabels: Record<HintUsage["level"], string> = {
  1: "가벼운 방향 제시",
  2: "핵심 개념 힌트",
  3: "구체적인 예시",
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function getHintLevel(hintNumber: number): HintUsage["level"] {
  if (hintNumber <= 2) {
    return 1;
  }

  if (hintNumber <= 4) {
    return 2;
  }

  return 3;
}

function getHintUsage(sessions: Session[]): HintUsage[] {
  const counts: Record<HintUsage["level"], number> = {
    1: 0,
    2: 0,
    3: 0,
  };

  sessions.forEach((session) => {
    for (let hintNumber = 1; hintNumber <= session.hintCount; hintNumber += 1) {
      counts[getHintLevel(hintNumber)] += 1;
    }
  });

  return ([1, 2, 3] as const).map((level) => ({
    level,
    label: hintLabels[level],
    count: counts[level],
  }));
}

function getTopicProgress(sessions: Session[]): TopicProgress[] {
  const topics = new Map<
    string,
    {
      completedSessions: number;
      totalSessions: number;
      lastPracticedAt: string;
    }
  >();

  sessions.forEach((session) => {
    const topicName = session.topic?.trim() || "러버덕 질문 훈련";
    const current = topics.get(topicName);
    const isCompleted = session.status === "completed";

    if (!current) {
      topics.set(topicName, {
        completedSessions: isCompleted ? 1 : 0,
        totalSessions: 1,
        lastPracticedAt: session.updatedAt,
      });
      return;
    }

    current.totalSessions += 1;
    current.completedSessions += isCompleted ? 1 : 0;
    if (
      new Date(session.updatedAt).getTime() >
      new Date(current.lastPracticedAt).getTime()
    ) {
      current.lastPracticedAt = session.updatedAt;
    }
  });

  return Array.from(topics.entries())
    .map(([topic, progress]) => ({
      topic,
      ...progress,
      progress:
        progress.totalSessions === 0
          ? 0
          : Math.round(
              (progress.completedSessions / progress.totalSessions) * 100,
            ),
    }))
    .sort(
      (a, b) =>
        new Date(b.lastPracticedAt).getTime() -
        new Date(a.lastPracticedAt).getTime(),
    );
}

function getImprovementPoints(
  sessions: Session[],
  topicProgress: TopicProgress[],
  averageHintCount: number,
): ImprovementPoint[] {
  if (sessions.length === 0) {
    return [
      {
        id: "start-first-session",
        title: "첫 학습 세션 시작하기",
        description:
          "아직 분석할 학습 기록이 없습니다. 러버덕에게 질문을 남기면 다음 학습 포인트가 자동으로 쌓입니다.",
        priority: "high",
      },
    ];
  }

  const points: ImprovementPoint[] = [];
  const inProgressCount = sessions.filter(
    (session) => session.status === "in_progress",
  ).length;
  const weakestTopic = topicProgress
    .filter((topic) => topic.totalSessions > 0)
    .sort((a, b) => a.progress - b.progress)[0];

  if (inProgressCount > 0) {
    points.push({
      id: "finish-open-sessions",
      title: "진행 중인 세션 마무리하기",
      description:
        "끝내지 않은 학습 흐름이 있습니다. 세션을 완료하면 회고와 다음 학습 포인트가 더 정확해집니다.",
      priority: "high",
    });
  }

  if (weakestTopic && weakestTopic.progress < 100) {
    points.push({
      id: `topic-${weakestTopic.topic}`,
      title: `${weakestTopic.topic} 복습 흐름 보강하기`,
      description:
        "완료율이 낮은 주제입니다. 같은 주제로 짧은 질문을 한 번 더 진행하면 흐름을 안정적으로 이어갈 수 있습니다.",
      priority: inProgressCount > 0 ? "medium" : "high",
    });
  }

  if (averageHintCount >= 3) {
    points.push({
      id: "reduce-hint-dependency",
      title: "힌트 요청 전 조건을 한 문장으로 정리하기",
      description:
        "힌트를 보기 전에 현재 알고 있는 조건과 막힌 지점을 먼저 말하면 Ducky의 답변이 더 정확해집니다.",
      priority: "medium",
    });
  }

  if (points.length === 0) {
    points.push({
      id: "keep-explaining",
      title: "풀이 근거를 계속 말로 남기기",
      description:
        "최근 세션 흐름이 좋습니다. 다음 세션에서도 왜 그렇게 생각했는지 함께 설명하면 복습 자료가 더 선명해집니다.",
      priority: "low",
    });
  }

  return points.slice(0, 3);
}

function buildAnalysis(
  user: User | null,
  sessions: Session[],
): AnalysisSummary | null {
  if (!user) {
    return null;
  }

  const completedSessions = sessions.filter(
    (session) => session.status === "completed",
  ).length;
  const averageHintCount =
    sessions.length === 0
      ? 0
      : Number(
          (
            sessions.reduce((sum, session) => sum + session.hintCount, 0) /
            sessions.length
          ).toFixed(1),
        );
  const topicProgress = getTopicProgress(sessions);

  return {
    userId: user.id,
    updatedAt:
      sessions[0]?.updatedAt ?? user.joinedAt ?? new Date().toISOString(),
    learningStyle: user.learningStyle,
    totalSessions: sessions.length,
    completedSessions,
    currentStreakDays: user.streakDays,
    averageHintCount,
    topicProgress,
    hintUsage: getHintUsage(sessions),
    improvementPoints: getImprovementPoints(
      sessions,
      topicProgress,
      averageHintCount,
    ),
  };
}

function HintUsageBars({ hints }: { hints: HintUsage[] }) {
  const maxCount = Math.max(...hints.map((hint) => hint.count), 1);
  const sizeMap = hints.map((h) => {
    const ratio = h.count / maxCount;
    if (ratio >= 0.8) return "text-[32px]";
    if (ratio >= 0.4) return "text-[22px]";
    return "text-[14px]";
  });

  return (
    <div className="flex h-full flex-col divide-y divide-[#f0e4cc] dark:divide-white/8">
      {hints.map((hint, i) => (
        <div
          key={hint.level}
          className="flex flex-1 items-center justify-between gap-2"
        >
          <div>
            <span className="text-sm font-bold text-gray-900 dark:text-white">
              Lv.{hint.level}
            </span>
            <span className="ml-1.5 text-xs text-[#876c45] dark:text-gray-400">
              {hint.label}
            </span>
          </div>
          <div className="flex shrink-0 items-baseline gap-0.5">
            <span
              className={cn(
                "font-black leading-none tabular-nums tracking-tight text-amber-900 dark:text-amber-300",
                sizeMap[i],
              )}
            >
              {hint.count}
            </span>
            <span className="text-xs font-semibold text-[#876c45] dark:text-gray-500">
              회
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

function TopicProgressList({ topics }: { topics: TopicProgress[] }) {
  if (topics.length === 0) {
    return (
      <p className="mt-5 rounded-lg border border-dashed border-[#E7DDC8] bg-[#FAF8F5] px-4 py-6 text-center text-sm text-gray-500 dark:border-white/10 dark:bg-[#1D1B18] dark:text-gray-400">
        아직 주제별로 분석할 학습 기록이 없습니다.
      </p>
    );
  }

  const sorted = [...topics].sort((a, b) => b.progress - a.progress);
  return (
    <div className="divide-y divide-[#f5e8cc] dark:divide-white/8">
      {sorted.map((topic) => (
        <div
          key={topic.topic}
          className="flex items-center gap-4 py-3 first:pt-0 last:pb-0"
        >
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold text-gray-900 dark:text-white">
              {topic.topic}
            </p>
            <p className="mt-0.5 text-[11px] text-[#876c45] dark:text-gray-400">
              {topic.completedSessions}/{topic.totalSessions}개 ·{" "}
              {formatDate(topic.lastPracticedAt)}
            </p>
          </div>
          <div className="flex w-28 shrink-0 items-center gap-2">
            <div className="h-[5px] flex-1 overflow-hidden rounded-full bg-[#f5e8cc] dark:bg-white/10">
              <div
                className="h-full rounded-full bg-amber-400"
                style={{ width: `${topic.progress}%` }}
              />
            </div>
            <span className="w-8 text-right text-xs font-bold tabular-nums text-gray-900 dark:text-white">
              {topic.progress}%
            </span>
          </div>
        </div>
      ))}
    </div>
  );
}

function ImprovementTimeline({ points }: { points: ImprovementPoint[] }) {
  const order: ImprovementPoint["priority"][] = ["high", "medium", "low"];
  const sorted = [...points].sort(
    (a, b) => order.indexOf(a.priority) - order.indexOf(b.priority),
  );
  return (
    <div>
      {sorted.map((point, i) => (
        <article key={point.id} className="flex gap-3 pb-4 last:pb-0">
          <div className="flex w-5 shrink-0 flex-col items-center pt-1">
            <span
              className={cn(
                "size-2 shrink-0 rounded-full",
                point.priority === "high"
                  ? "bg-amber-500"
                  : "bg-[#e8dcc8] dark:bg-white/15",
              )}
            />
            {i < sorted.length - 1 && (
              <span className="mt-1.5 w-px flex-1 bg-[#f0e4cc] dark:bg-white/10" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-bold leading-snug text-gray-900 break-keep dark:text-white">
              {point.title}
            </h3>
            <p className="mt-1 text-xs leading-relaxed text-gray-500 break-keep dark:text-gray-400">
              {point.description}
            </p>
          </div>
          <div className="flex w-10 shrink-0 items-start justify-center pt-1">
            {point.priority === "high" ? (
              <span className="inline-flex rounded-full border border-[#f5d87a] bg-[#fef3c7] px-2 py-0.5 text-[10px] font-bold text-amber-700 dark:border-amber-700/40 dark:bg-amber-900/30 dark:text-amber-300">
                {priorityLabels[point.priority]}
              </span>
            ) : (
              <span className="text-[10px] font-medium text-[#876c45] dark:text-gray-500">
                {priorityLabels[point.priority]}
              </span>
            )}
          </div>
        </article>
      ))}
    </div>
  );
}

function TopicDetailList({ topics }: { topics: TopicProgress[] }) {
  if (topics.length === 0) {
    return (
      <p className="rounded-lg border border-dashed border-[#E7DDC8] bg-[#FAF8F5] px-4 py-6 text-center text-sm text-gray-500 dark:border-white/10 dark:bg-[#1D1B18] dark:text-gray-400">
        세션을 완료하면 주제별 상세 기록이 표시됩니다.
      </p>
    );
  }

  return (
    <div className="divide-y divide-[#f0e4cc] dark:divide-white/8">
      {topics.map((topic) => (
        <div
          key={topic.topic}
          className="grid gap-3 py-3 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_5rem]"
        >
          <div className="min-w-0">
            <p className="text-sm font-bold text-gray-900 dark:text-white">
              {topic.topic}
            </p>
            <p className="mt-0.5 text-xs text-[#876c45] dark:text-gray-400">
              최근 {formatDate(topic.lastPracticedAt)}
            </p>
          </div>
          <div className="sm:text-right">
            <p className="text-base font-bold tabular-nums text-gray-900 dark:text-white">
              {topic.progress}%
            </p>
            <p className="text-xs text-[#876c45] dark:text-gray-400">
              {topic.completedSessions}개 완료
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}

export function AnalysisDashboard() {
  const { error, isLoading, retry, sessions, user } = useDashboardData();
  const analysis = useMemo(
    () => buildAnalysis(user, sessions),
    [sessions, user],
  );

  const styleRows = analysis
    ? [
        {
          label: "처리 방식",
          value: styleLabels.processing[analysis.learningStyle.processing],
          description:
            styleDescriptions.processing[analysis.learningStyle.processing],
        },
        {
          label: "표현 선호",
          value: styleLabels.expression[analysis.learningStyle.expression],
          description:
            styleDescriptions.expression[analysis.learningStyle.expression],
        },
        {
          label: "이해 구조",
          value:
            styleLabels.understanding[analysis.learningStyle.understanding],
          description:
            styleDescriptions.understanding[
              analysis.learningStyle.understanding
            ],
        },
      ]
    : [];

  const completionRate = analysis?.totalSessions
    ? Math.round((analysis.completedSessions / analysis.totalSessions) * 100)
    : 0;

  return (
    <div className="flex min-h-full w-full flex-1 flex-col bg-[#FFFCF3] transition-colors dark:bg-[#16130A]">
      <section className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 pt-8 pb-[calc(6rem+env(safe-area-inset-bottom))] sm:px-8 lg:px-10 md:py-8">
        <div className="py-6 mb-2 ">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 mb-3 text-[10px] font-bold tracking-[0.12em] text-amber-700 uppercase dark:bg-amber-900/40 dark:text-amber-300">
            <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
            Learning Analysis Report
          </span>
          <h1 className="mt-3 text-[30px] font-black leading-[1.15] tracking-[-1px] text-gray-950 dark:text-white sm:text-[40px]">
            지금까지 잘<br className="sm:hidden" /> 달려왔어요
            <span className="text-amber-400">.</span>
          </h1>
          <div className="mt-4 ml-1.5 flex items-center justify-between gap-4">
            <p className="text-[13px] font-medium leading-relaxed text-[#806437] dark:text-amber-400/70">
              세션 기록을 바탕으로 강점과 다음 액션을 정리했어요.
            </p>
            <p className="shrink-0 text-[11px] text-[#876c45] dark:text-gray-500">
              {analysis && `${formatDate(analysis.updatedAt)} 기준`}
            </p>
          </div>
        </div>
        {(isLoading || error || !analysis) && (
          <div className="mt-6 rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300">
            {isLoading || (!analysis && !error) ? (
              <span className="inline-flex items-center gap-2">
                <Loader2 className="size-4 animate-spin text-[#B88700]" />
                분석 데이터를 불러오는 중입니다.
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
        {analysis && (
          <>
            <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-[1.6fr_1fr_1fr]">
              <div className="relative overflow-hidden rounded-3xl border border-amber-200/50 p-6 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_8px_24px_-12px_rgba(217,119,6,0.12)] [background:linear-gradient(to_bottom_right,#fffbeb,#ffffff,#fefce8)] dark:border-white/8 dark:[background:#1E1B16] dark:shadow-none">
                <div className="pointer-events-none absolute -right-10 -top-10 h-28 w-28 rounded-full bg-amber-200/30 blur-3xl dark:hidden" />
                <div className="pointer-events-none absolute -bottom-8 left-1/3 h-20 w-20 rounded-full bg-yellow-200/30 blur-2xl dark:hidden" />
                <div className="relative">
                  <p className="mb-2.5 text-[10px] font-semibold uppercase tracking-widest text-amber-600 dark:text-amber-400">
                    완료율
                  </p>
                  <div className="mb-1.5 flex items-baseline gap-1">
                    <span className="text-[52px] font-extrabold leading-none tabular-nums tracking-tight text-gray-950 dark:text-white">
                      {completionRate}
                    </span>
                    <span className="text-xl font-bold text-[#876c45] dark:text-gray-500">
                      %
                    </span>
                  </div>
                  <p className="mb-4 text-xs text-[#876c45] dark:text-gray-400">
                    {analysis.completedSessions}/{analysis.totalSessions}개 세션
                    완료
                  </p>
                  <div className="h-[5px] overflow-hidden rounded-full bg-[#f5e8cc] dark:bg-white/10">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-amber-400 to-orange-400"
                      style={{ width: `${completionRate}%` }}
                    />
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 sm:contents">
                <div className="relative overflow-hidden rounded-2xl border border-amber-200/40 bg-[#fef3c7] p-5 dark:border-white/8 dark:bg-[#1E1B16]">
                  <span className="absolute right-4 top-4 text-right text-[11px] font-extrabold leading-[1.4] tracking-[.08em] text-amber-700/30 uppercase dark:text-amber-200/15">
                    연속
                    <br />
                    학습
                  </span>
                  <div className="pt-9">
                    <div className="flex items-baseline gap-1">
                      <span className="text-[52px] font-black leading-none tabular-nums tracking-[-3px] text-amber-900 dark:text-white">
                        {analysis.currentStreakDays}
                      </span>
                      <span className="text-base font-bold text-amber-700/30 dark:text-gray-500">
                        일
                      </span>
                    </div>
                    <p className="mt-2 text-[13px] font-semibold text-amber-800/50 dark:text-gray-400">
                      꾸준한 복습 흐름
                    </p>
                  </div>
                </div>

                <div className="relative overflow-hidden rounded-2xl border border-amber-200/40 bg-[#fef3c7] p-5 dark:border-white/8 dark:bg-[#1E1B16]">
                  <span className="absolute right-4 top-4 text-right text-[11px] font-extrabold leading-[1.4] tracking-[.08em] text-amber-700/30 uppercase dark:text-amber-200/15">
                    평균
                    <br />
                    힌트
                  </span>
                  <div className="pt-9">
                    <div className="flex items-baseline gap-1">
                      <span className="text-[52px] font-black leading-none tabular-nums tracking-[-3px] text-amber-900 dark:text-white">
                        {analysis.averageHintCount}
                      </span>
                      <span className="text-base font-bold text-amber-700/30 dark:text-gray-500">
                        회
                      </span>
                    </div>
                    <p className="mt-2 text-[13px] font-semibold text-amber-800/50 dark:text-gray-400">
                      세션당 사용량
                    </p>
                  </div>
                </div>
              </div>
            </div>

            <div className="mb-4 grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div className="overflow-hidden rounded-2xl border border-[#e8dcc8] bg-white shadow-[0_1px_3px_rgba(0,0,0,0.03)] sm:col-span-2 dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
                <div className="flex h-12 items-center justify-between border-b border-[#ede0c4] px-4 dark:border-white/8 dark:bg-[#1A1714] [background:linear-gradient(to_right,#fffbeb,#fef9ec)] dark:[background:#1A1714]">
                  <h2 className="text-xs font-bold text-amber-900 dark:text-white">
                    주제별 진행률
                  </h2>
                  <span className="rounded-full border border-amber-300/60 bg-[#fffbeb] px-2.5 py-0.5 text-[11px] font-bold text-amber-700 dark:border-amber-800/30 dark:bg-amber-900/20 dark:text-amber-300">
                    {analysis.completedSessions}/{analysis.totalSessions} 완료
                  </span>
                </div>
                <div className="p-4">
                  <TopicProgressList topics={analysis.topicProgress} />
                </div>
              </div>

              <div className="flex flex-col overflow-hidden rounded-2xl border border-[#ede0c4] bg-[#fff8e1] shadow-[0_1px_3px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
                <div className="flex h-12 items-center gap-1.5 border-b border-[#ede0c4] px-4 dark:border-white/8 dark:bg-[#1A1714]">
                  <Target
                    className="size-3.5 text-amber-600 dark:text-amber-400"
                    strokeWidth={1.8}
                  />
                  <h2 className="text-xs font-bold uppercase tracking-widest text-amber-700 dark:text-amber-300">
                    다음 목표
                  </h2>
                </div>
                <div className="flex flex-1 flex-col items-center justify-center px-5 py-6 text-center">
                  <p className="text-[18px] font-extrabold leading-[1.45] tracking-[-0.2px] text-amber-900 break-keep dark:text-white">
                    {analysis.improvementPoints[0]?.title ??
                      "다음 학습 세션 시작하기"}
                  </p>
                  <div className="my-4 h-px w-full bg-[#ede0c4] dark:bg-white/8" />
                  <p className="text-xs leading-relaxed text-[#806437] break-keep dark:text-gray-400">
                    새 세션을 완료하면 학습 기록을 바탕으로 분석이 갱신됩니다.
                  </p>
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-[1.6fr_1fr_1fr]">
              <div className="overflow-hidden rounded-2xl border border-[#e8dcc8] bg-[#fdfaf3] shadow-[0_1px_3px_rgba(0,0,0,0.03)] sm:col-span-2 lg:col-span-1 dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
                <Tabs defaultValue="improvements" className="gap-0">
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-amber-100 bg-white px-4 py-2 dark:border-white/10 dark:bg-[#1A1714]">
                    <h2 className="text-xs font-bold">다음 학습 포인트</h2>
                    <TabsList variant="line">
                      <TabsTrigger value="improvements">
                        개선 포인트
                      </TabsTrigger>
                      <TabsTrigger value="topics">주제 상세</TabsTrigger>
                    </TabsList>
                  </div>
                  <TabsContent value="improvements" className="p-4">
                    <ImprovementTimeline points={analysis.improvementPoints} />
                  </TabsContent>
                  <TabsContent value="topics" className="p-4">
                    <TopicDetailList topics={analysis.topicProgress} />
                  </TabsContent>
                </Tabs>
              </div>

              <div className="overflow-hidden rounded-2xl border border-[#e8dcc8] bg-white shadow-[0_1px_3px_rgba(0,0,0,0.03)] dark:border-white/8 dark:bg-[#1E1B16] dark:shadow-none">
                <div className="flex items-center justify-between border-b border-[#f5e8cc] px-4 py-3 dark:border-white/8 dark:bg-[#1A1714]">
                  <h2 className="text-xs font-bold text-gray-900 dark:text-white">
                    학습 유형
                  </h2>
                  <Brain
                    className="size-3.5 text-[#e8c86a] dark:text-amber-400"
                    strokeWidth={1.8}
                  />
                </div>
                <dl className="divide-y divide-[#f5e8cc] dark:divide-white/8">
                  {styleRows.map((row) => (
                    <div key={row.label} className="flex gap-3 px-4 py-3">
                      <div className="my-0.5 w-[3px] shrink-0 rounded-full bg-amber-300" />
                      <div className="min-w-0">
                        <dt className="mb-1 text-[10px] font-semibold uppercase tracking-wide text-[#876c45] dark:text-gray-500">
                          {row.label}
                        </dt>
                        <dd className="mb-1 text-xs font-bold text-gray-900 dark:text-white">
                          {row.value}
                        </dd>
                        <dd className="text-[11px] leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                          {row.description}
                        </dd>
                      </div>
                    </div>
                  ))}
                </dl>
              </div>

              <div className="overflow-hidden rounded-2xl border border-[#ede0c4] bg-[#fafaf8] dark:border-white/8 dark:bg-[#1E1B16]">
                <div className="flex items-center justify-between border-b border-[#ede0c4] bg-white px-4 py-3 dark:border-white/8 dark:bg-[#1A1714]">
                  <h2 className="text-xs font-bold text-gray-900 dark:text-white">
                    힌트 사용량
                  </h2>
                  <TrendingUp
                    className="size-3.5 text-[#e8c86a] dark:text-amber-400"
                    strokeWidth={1.8}
                  />
                </div>
                <div className="flex h-[calc(100%-41px)] flex-col divide-y divide-[#f0e4cc] px-4 dark:divide-white/8">
                  <HintUsageBars hints={analysis.hintUsage} />
                </div>
              </div>
            </div>
          </>
        )}
      </section>
    </div>
  );
}
