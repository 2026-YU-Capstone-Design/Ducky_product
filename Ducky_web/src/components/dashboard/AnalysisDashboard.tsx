"use client";

import { useMemo } from "react";
import {
  AlertCircle,
  BarChart3,
  Brain,
  CheckCircle2,
  Flame,
  Lightbulb,
  Loader2,
  RefreshCw,
  Target,
  TrendingUp,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Progress, ProgressLabel } from "@/components/ui/progress";
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

const priorityStyles: Record<ImprovementPoint["priority"], string> = {
  high:
    "border-[#F2B8A2] bg-[#FFF0EA] text-[#8A3B20] dark:border-[#F2B8A2]/50 dark:bg-[#2A211D] dark:text-[#F2B8A2]",
  medium:
    "border-[#FECA43] bg-[#FFF7E0] text-[#6B5200] dark:border-[#FECA43]/60 dark:bg-[#2A251D] dark:text-[#FECA43]",
  low:
    "border-[#BFD8C4] bg-[#EAF7ED] text-[#236B35] dark:border-[#BFD8C4]/40 dark:bg-[#1D2A22] dark:text-[#BFD8C4]",
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
    if (new Date(session.updatedAt).getTime() > new Date(current.lastPracticedAt).getTime()) {
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
          : Math.round((progress.completedSessions / progress.totalSessions) * 100),
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

function buildAnalysis(user: User | null, sessions: Session[]): AnalysisSummary | null {
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

function completionText(analysis: AnalysisSummary) {
  return `${analysis.completedSessions}/${analysis.totalSessions}`;
}

function HintUsageBars({ hints }: { hints: HintUsage[] }) {
  const maxCount = Math.max(...hints.map((hint) => hint.count), 1);

  return (
    <div className="mt-5 space-y-4">
      {hints.map((hint) => {
        const width = hint.count === 0 ? "0%" : `${Math.max((hint.count / maxCount) * 100, 8)}%`;

        return (
          <div key={hint.level} className="min-w-0">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="text-sm font-bold text-gray-950 dark:text-white">
                  Level {hint.level}
                </p>
                <p className="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                  {hint.label}
                </p>
              </div>
              <span className="shrink-0 text-sm font-bold tabular-nums text-gray-950 dark:text-white">
                {hint.count}회
              </span>
            </div>
            <div className="mt-2 h-2 overflow-hidden rounded-full bg-[#E7DDC8] dark:bg-white/10">
              <div
                className="h-full rounded-full bg-[#245C7A]"
                style={{ width }}
              />
            </div>
          </div>
        );
      })}
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

  return (
    <div className="mt-5 space-y-5">
      {topics.map((topic) => (
        <div key={topic.topic} className="min-w-0">
          <Progress
            value={topic.progress}
            className="[&_[data-slot=progress-indicator]]:bg-[#236B35] [&_[data-slot=progress-track]]:h-2 [&_[data-slot=progress-track]]:bg-[#E7DDC8] dark:[&_[data-slot=progress-track]]:bg-white/10"
          >
            <ProgressLabel className="min-w-0 text-sm font-bold text-gray-950 dark:text-white">
              {topic.topic}
            </ProgressLabel>
            <span className="ml-auto text-sm font-bold text-gray-950 dark:text-white">
              {topic.progress}%
            </span>
          </Progress>
          <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
            <span>
              완료 {topic.completedSessions}/{topic.totalSessions}
            </span>
            <span>최근 {formatDate(topic.lastPracticedAt)}</span>
          </div>
        </div>
      ))}
    </div>
  );
}

function ImprovementList({ points }: { points: ImprovementPoint[] }) {
  return (
    <div className="space-y-3">
      {points.map((point) => (
        <article
          key={point.id}
          className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 transition-colors dark:border-white/10 dark:bg-[#1D1B18]"
        >
          <div className="flex items-start justify-between gap-3">
            <h3 className="min-w-0 text-sm font-bold leading-snug text-gray-950 break-keep dark:text-white">
              {point.title}
            </h3>
            <Badge
              variant="outline"
              className={cn("shrink-0", priorityStyles[point.priority])}
            >
              {priorityLabels[point.priority]}
            </Badge>
          </div>
          <p className="mt-2 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-300">
            {point.description}
          </p>
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
    <div className="divide-y divide-[#E7DDC8] dark:divide-white/10">
      {topics.map((topic) => (
        <div
          key={topic.topic}
          className="grid gap-3 py-4 first:pt-0 last:pb-0 sm:grid-cols-[minmax(0,1fr)_8rem]"
        >
          <div className="min-w-0">
            <p className="font-bold text-gray-950 dark:text-white">
              {topic.topic}
            </p>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              최근 학습 {formatDate(topic.lastPracticedAt)}
            </p>
          </div>
          <div className="sm:text-right">
            <p className="text-lg font-bold tabular-nums text-gray-950 dark:text-white">
              {topic.progress}%
            </p>
            <p className="text-xs text-gray-500 dark:text-gray-400">
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
          value: styleLabels.understanding[analysis.learningStyle.understanding],
          description:
            styleDescriptions.understanding[analysis.learningStyle.understanding],
        },
      ]
    : [];

  const statCards = analysis
    ? [
        {
          label: "전체 세션",
          value: `${analysis.totalSessions}개`,
          detail: `${analysis.completedSessions}개 완료`,
          icon: BarChart3,
          tone: "text-[#245C7A]",
          bg: "bg-[#EAF4F8]",
        },
        {
          label: "완료율",
          value:
            analysis.totalSessions === 0
              ? "0%"
              : `${Math.round(
                  (analysis.completedSessions / analysis.totalSessions) * 100,
                )}%`,
          detail: "최근 학습 기준",
          icon: CheckCircle2,
          tone: "text-[#236B35]",
          bg: "bg-[#EAF7ED]",
        },
        {
          label: "연속 학습",
          value: `${analysis.currentStreakDays}일`,
          detail: "꾸준한 복습 흐름",
          icon: Flame,
          tone: "text-[#B88700]",
          bg: "bg-[#FFF1BC]",
        },
        {
          label: "평균 힌트",
          value: `${analysis.averageHintCount}회`,
          detail: "세션당 사용량",
          icon: Lightbulb,
          tone: "text-[#7A4C12]",
          bg: "bg-[#F7E7CE]",
        },
      ]
    : [];

  return (
    <section className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 py-8 sm:px-8 lg:px-10">
      <div className="border-b border-[#E7DDC8] pb-8 dark:border-white/10">
        <p className="text-sm font-semibold text-[#B88700]">학습 분석</p>
        <div className="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <h1 className="max-w-3xl text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
              학습 흐름을 한 번에 확인하세요
            </h1>
            <p className="mt-4 max-w-3xl text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300">
              실제 세션 기록과 학습 유형을 기준으로 진행률, 힌트 사용량,
              다음 개선 포인트를 정리했습니다.
            </p>
          </div>

          <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 dark:border-white/10 dark:bg-[#24211D]">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              최근 업데이트
            </p>
            <p className="mt-1 text-sm font-bold text-gray-950 dark:text-white">
              {analysis ? formatDate(analysis.updatedAt) : "-"}
            </p>
          </div>
        </div>
      </div>

      {(isLoading || error || !analysis) && (
        <div className="mt-6 rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300">
          {isLoading || !analysis ? (
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
          <div className="mt-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {statCards.map((stat) => {
              const Icon = stat.icon;

              return (
                <Card
                  key={stat.label}
                  className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none"
                >
                  <CardContent className="flex min-h-32 flex-col justify-between p-5">
                    <span
                      className={cn(
                        "flex size-10 items-center justify-center rounded-lg",
                        stat.bg,
                        "dark:bg-[#2A251D]",
                      )}
                    >
                      <Icon className={cn("size-5", stat.tone)} />
                    </span>
                    <div>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {stat.label}
                      </p>
                      <p className="mt-1 text-2xl font-bold tracking-tight text-gray-950 dark:text-white">
                        {stat.value}
                      </p>
                      <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                        {stat.detail}
                      </p>
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>

          <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
            <div className="min-w-0 space-y-6">
              <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
                <CardContent className="p-5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-[#B88700]">
                        주제별 진행률
                      </p>
                      <h2 className="mt-2 text-xl font-bold tracking-tight text-gray-950 dark:text-white">
                        완료 흐름
                      </h2>
                    </div>
                    <Badge className="bg-[#EAF7ED] text-[#236B35] dark:bg-[#1D2A22] dark:text-[#BFD8C4]">
                      {completionText(analysis)} 완료
                    </Badge>
                  </div>

                  <TopicProgressList topics={analysis.topicProgress} />
                </CardContent>
              </Card>

              <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
                <CardContent className="p-5">
                  <Tabs defaultValue="improvements" className="gap-5">
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0">
                        <p className="text-xs font-semibold text-[#B88700]">
                          최근 분석
                        </p>
                        <h2 className="mt-2 text-xl font-bold tracking-tight text-gray-950 dark:text-white">
                          다음 학습 포인트
                        </h2>
                      </div>
                      <TabsList className="h-9 w-full bg-[#FAF8F5] dark:bg-[#1D1B18] sm:w-fit">
                        <TabsTrigger value="improvements" className="px-3">
                          개선 포인트
                        </TabsTrigger>
                        <TabsTrigger value="topics" className="px-3">
                          주제 상세
                        </TabsTrigger>
                      </TabsList>
                    </div>

                    <TabsContent value="improvements">
                      <ImprovementList points={analysis.improvementPoints} />
                    </TabsContent>
                    <TabsContent value="topics">
                      <TopicDetailList topics={analysis.topicProgress} />
                    </TabsContent>
                  </Tabs>
                </CardContent>
              </Card>
            </div>

            <aside className="space-y-6">
              <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-[#B88700]">
                        학습 유형
                      </p>
                      <h2 className="mt-2 text-xl font-bold tracking-tight text-gray-950 dark:text-white">
                        현재 패턴
                      </h2>
                    </div>
                    <Brain className="size-5 shrink-0 text-[#245C7A]" />
                  </div>

                  <dl className="mt-5 divide-y divide-[#E7DDC8] dark:divide-white/10">
                    {styleRows.map((row) => (
                      <div key={row.label} className="py-4 first:pt-0">
                        <dt className="text-xs font-medium text-gray-500 dark:text-gray-400">
                          {row.label}
                        </dt>
                        <dd className="mt-1 text-sm font-bold text-gray-950 dark:text-white">
                          {row.value}
                        </dd>
                        <dd className="mt-2 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-300">
                          {row.description}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </CardContent>
              </Card>

              <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
                <CardContent className="p-5">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold text-[#B88700]">
                        힌트 사용량
                      </p>
                      <h2 className="mt-2 text-xl font-bold tracking-tight text-gray-950 dark:text-white">
                        단계별 분포
                      </h2>
                    </div>
                    <TrendingUp className="size-5 shrink-0 text-[#236B35]" />
                  </div>

                  <HintUsageBars hints={analysis.hintUsage} />
                </CardContent>
              </Card>

              <Card className="rounded-lg border border-[#E7DDC8] bg-[#2E2A22] py-0 text-white shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
                <CardContent className="p-5">
                  <Target className="size-5 text-[#FECA43]" />
                  <p className="mt-4 text-sm font-semibold text-[#FECA43]">
                    다음 목표
                  </p>
                  <p className="mt-2 text-lg font-bold leading-snug break-keep">
                    {analysis.improvementPoints[0]?.title ??
                      "다음 학습 세션 시작하기"}
                  </p>
                  <p className="mt-3 text-sm leading-relaxed text-white/70 break-keep">
                    실제 학습 기록을 기준으로 가장 먼저 챙길 흐름을 표시합니다.
                    새 세션을 완료하면 분석이 다시 갱신됩니다.
                  </p>
                </CardContent>
              </Card>
            </aside>
          </div>
        </>
      )}
    </section>
  );
}
