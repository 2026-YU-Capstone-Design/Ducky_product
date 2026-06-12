import type { User } from "@/types/user";

export const mockUser: User = {
  id: "user-ducky-001",
  name: "덕키",
  email: "ducky@example.com",
  level: "beginner",
  learningStyle: {
    processing: "active",
    expression: "visual",
    understanding: "sequential",
  },
  onboarded: true,
  joinedAt: "2026-05-01T09:00:00+09:00",
  streakDays: 4,
  completedSessionCount: 8,
};
