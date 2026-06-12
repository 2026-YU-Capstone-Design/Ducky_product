import {
  BarChart3,
  Brain,
  CheckCircle2,
  Flame,
  Lightbulb,
  Target,
  TrendingUp,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Progress,
  ProgressLabel,
} from "@/components/ui/progress";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { mockAnalysis } from "@/data/mockAnalysis";
import type { ImprovementPoint } from "@/types/analysis";
import type { LearningStyle } from "@/types/user";
import { cn } from "@/lib/utils";

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
    value: styleLabels.processing[mockAnalysis.learningStyle.processing],
    description: "문제를 먼저 풀어보며 흐름을 잡는 편입니다.",
  },
  {
    label: "표현 선호",
    value: styleLabels.expression[mockAnalysis.learningStyle.expression],
    description: "구조와 관계가 보이면 개념을 더 빠르게 정리합니다.",
  },
  {
    label: "이해 구조",
    value: styleLabels.understanding[mockAnalysis.learningStyle.understanding],
    description: "작은 단계가 이어질 때 답변 품질이 안정됩니다.",
  },
];

const statCards = [
  {
    label: "전체 세션",
    value: `${mockAnalysis.totalSessions}개`,
    detail: `${mockAnalysis.completedSessions}개 완료`,
    icon: BarChart3,
    tone: "text-[#245C7A]",
    bg: "bg-[#EAF4F8]",
  },
  {
    label: "완료율",
    value: `${Math.round(
      (mockAnalysis.completedSessions / mockAnalysis.totalSessions) * 100,
    )}%`,
    detail: "최근 학습 기준",
    icon: CheckCircle2,
    tone: "text-[#236B35]",
    bg: "bg-[#EAF7ED]",
  },
  {
    label: "연속 학습",
    value: `${mockAnalysis.currentStreakDays}일`,
    detail: "꾸준한 복습 흐름",
    icon: Flame,
    tone: "text-[#B88700]",
    bg: "bg-[#FFF1BC]",
  },
  {
    label: "평균 힌트",
    value: `${mockAnalysis.averageHintCount}회`,
    detail: "세션당 사용량",
    icon: Lightbulb,
    tone: "text-[#7A4C12]",
    bg: "bg-[#F7E7CE]",
  },
];

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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function completionText() {
  return `${mockAnalysis.completedSessions}/${mockAnalysis.totalSessions}`;
}

function HintUsageBars() {
  const maxCount = Math.max(...mockAnalysis.hintUsage.map((hint) => hint.count));

  return (
    <div className="mt-5 space-y-4">
      {mockAnalysis.hintUsage.map((hint) => {
        const width = `${Math.max((hint.count / maxCount) * 100, 8)}%`;

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

function TopicProgressList() {
  return (
    <div className="mt-5 space-y-5">
      {mockAnalysis.topicProgress.map((topic) => (
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

function ImprovementList() {
  return (
    <div className="space-y-3">
      {mockAnalysis.improvementPoints.map((point) => (
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

function TopicDetailList() {
  return (
    <div className="divide-y divide-[#E7DDC8] dark:divide-white/10">
      {mockAnalysis.topicProgress.map((topic) => (
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
              힌트 사용 패턴, 주제별 진행률, 최근 개선 포인트를 mock 데이터로
              정리했습니다.
            </p>
          </div>

          <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 dark:border-white/10 dark:bg-[#24211D]">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              최근 업데이트
            </p>
            <p className="mt-1 text-sm font-bold text-gray-950 dark:text-white">
              {formatDate(mockAnalysis.updatedAt)}
            </p>
          </div>
        </div>
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
                  {completionText()} 완료
                </Badge>
              </div>

              <TopicProgressList />
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
                  <ImprovementList />
                </TabsContent>
                <TabsContent value="topics">
                  <TopicDetailList />
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

              <HintUsageBars />
            </CardContent>
          </Card>

          <Card className="rounded-lg border border-[#E7DDC8] bg-[#2E2A22] py-0 text-white shadow-sm dark:border-white/10 dark:bg-[#24211D] dark:shadow-none">
            <CardContent className="p-5">
              <Target className="size-5 text-[#FECA43]" />
              <p className="mt-4 text-sm font-semibold text-[#FECA43]">
                다음 목표
              </p>
              <p className="mt-2 text-lg font-bold leading-snug break-keep">
                원인을 설명한 뒤 코드 흐름으로 다시 검증하기
              </p>
              <p className="mt-3 text-sm leading-relaxed text-white/70 break-keep">
                높은 우선순위 개선 포인트를 다음 채팅 세션의 질문 흐름에
                반영합니다.
              </p>
            </CardContent>
          </Card>
        </aside>
      </div>
    </section>
  );
}
