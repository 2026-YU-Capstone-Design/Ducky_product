export type LearningLevel = "beginner" | "intermediate" | "advanced";

export type ProcessingStyle = "active" | "reflective";
export type ExpressionStyle = "visual" | "verbal";
export type UnderstandingStyle = "sequential" | "global";

export interface LearningStyle {
  processing: ProcessingStyle;
  expression: ExpressionStyle;
  understanding: UnderstandingStyle;
}

export interface User {
  id: string;
  name: string;
  email: string;
  level: LearningLevel;
  learningStyle: LearningStyle;
  onboarded: boolean;
  joinedAt: string;
  streakDays: number;
  completedSessionCount: number;
}
