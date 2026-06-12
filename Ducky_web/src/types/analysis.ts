import type { LearningStyle } from "./user";

export interface TopicProgress {
  topic: string;
  completedSessions: number;
  totalSessions: number;
  progress: number;
  lastPracticedAt: string;
}

export interface HintUsage {
  level: 1 | 2 | 3;
  label: string;
  count: number;
}

export interface ImprovementPoint {
  id: string;
  title: string;
  description: string;
  priority: "low" | "medium" | "high";
}

export interface AnalysisSummary {
  userId: string;
  updatedAt: string;
  learningStyle: LearningStyle;
  totalSessions: number;
  completedSessions: number;
  currentStreakDays: number;
  averageHintCount: number;
  topicProgress: TopicProgress[];
  hintUsage: HintUsage[];
  improvementPoints: ImprovementPoint[];
}
