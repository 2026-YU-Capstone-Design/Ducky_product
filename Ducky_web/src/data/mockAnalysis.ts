import type { AnalysisSummary } from "@/types/analysis";
import { mockUser } from "./mockUser";

export const mockAnalysis: AnalysisSummary = {
  userId: mockUser.id,
  updatedAt: "2026-05-30T09:00:00+09:00",
  learningStyle: mockUser.learningStyle,
  totalSessions: 12,
  completedSessions: 8,
  currentStreakDays: mockUser.streakDays,
  averageHintCount: 2,
  topicProgress: [
    {
      topic: "React Hooks",
      completedSessions: 4,
      totalSessions: 5,
      progress: 80,
      lastPracticedAt: "2026-05-18T10:18:00+09:00",
    },
    {
      topic: "Next.js",
      completedSessions: 2,
      totalSessions: 4,
      progress: 50,
      lastPracticedAt: "2026-05-29T21:24:00+09:00",
    },
    {
      topic: "TypeScript",
      completedSessions: 2,
      totalSessions: 3,
      progress: 67,
      lastPracticedAt: "2026-05-24T15:05:00+09:00",
    },
  ],
  hintUsage: [
    { level: 1, label: "가벼운 방향 제시", count: 5 },
    { level: 2, label: "핵심 개념 힌트", count: 4 },
    { level: 3, label: "구체적인 예시", count: 2 },
  ],
  improvementPoints: [
    {
      id: "improvement-dependency-array",
      title: "원인 설명을 코드 흐름과 함께 말하기",
      description: "문제를 해결한 뒤 어떤 상태 변화가 다시 렌더링을 만들었는지 한 문장으로 정리해보세요.",
      priority: "high",
    },
    {
      id: "improvement-route-terms",
      title: "라우팅 용어 구분하기",
      description: "page, layout, segment처럼 자주 쓰는 용어를 예시 URL과 함께 연결하면 기억하기 쉽습니다.",
      priority: "medium",
    },
  ],
};
