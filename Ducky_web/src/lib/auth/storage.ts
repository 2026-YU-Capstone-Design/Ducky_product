import type { User } from "@/types/user";

export const USER_STORAGE_KEY = "ducky_user";

export const defaultUser: User = {
  id: "",
  name: "",
  email: "",
  level: "beginner",
  learningStyle: {
    processing: "active",
    expression: "visual",
    understanding: "sequential",
  },
  onboarded: false,
  joinedAt: "",
  streakDays: 0,
  completedSessionCount: 0,
};

export function persistUser(user: User) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
}

export function clearPersistedUser() {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(USER_STORAGE_KEY);
}
