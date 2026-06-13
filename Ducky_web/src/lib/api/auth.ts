import type { User } from "@/types/user";
import { apiRequest, setAccessToken } from "./client";

export interface BackendUser {
  id: string;
  name: string;
  email: string;
  loginId: string;
  level: string;
  learningStyle: {
    processing: string;
    expression: string;
    understanding: string;
  };
  onboarded: boolean;
  streakDays: number;
  completedSessionCount: number;
}

interface AuthResponse {
  accessToken: string;
  userInfo: BackendUser;
}

interface EmailAvailabilityResponse {
  available: boolean;
}

interface LoginPayload {
  email: string;
  password: string;
}

interface SignupPayload {
  name: string;
  email: string;
  password: string;
}

interface KakaoLoginPayload {
  code: string;
  redirectUri: string;
}

export interface UpdateLearningStylePayload {
  processing: User["learningStyle"]["processing"];
  expression: User["learningStyle"]["expression"];
  understanding: User["learningStyle"]["understanding"];
  onboarded?: boolean;
}

function asUser(user: BackendUser): User {
  return {
    id: user.id,
    name: user.name,
    email: user.email,
    level: user.level as User["level"],
    learningStyle: {
      processing: user.learningStyle
        .processing as User["learningStyle"]["processing"],
      expression: user.learningStyle
        .expression as User["learningStyle"]["expression"],
      understanding: user.learningStyle
        .understanding as User["learningStyle"]["understanding"],
    },
    onboarded: user.onboarded,
    joinedAt: new Date().toISOString(),
    streakDays: user.streakDays,
    completedSessionCount: user.completedSessionCount,
  };
}

async function saveAuthResponse(authResponse: AuthResponse) {
  setAccessToken(authResponse.accessToken);
  return asUser(authResponse.userInfo);
}

export async function login(payload: LoginPayload) {
  const authResponse = await apiRequest<AuthResponse>("/api/auth/login", {
    method: "POST",
    body: payload,
  });

  return saveAuthResponse(authResponse);
}

export async function signup(payload: SignupPayload) {
  const authResponse = await apiRequest<AuthResponse>("/api/auth/signup", {
    method: "POST",
    body: {
      ...payload,
      loginId: payload.email.trim().toLowerCase(),
    },
  });

  return saveAuthResponse(authResponse);
}

export async function loginWithKakao(payload: KakaoLoginPayload) {
  const authResponse = await apiRequest<AuthResponse>("/api/auth/oauth/kakao", {
    method: "POST",
    body: payload,
  });

  return saveAuthResponse(authResponse);
}

export async function checkEmailAvailability(email: string) {
  return apiRequest<EmailAvailabilityResponse>(
    `/api/auth/email-available?email=${encodeURIComponent(email.trim().toLowerCase())}`,
    {
      method: "GET",
    },
  );
}

export async function getMe() {
  const user = await apiRequest<BackendUser>("/api/users/me", {
    method: "GET",
    auth: true,
  });

  return asUser(user);
}

export async function updateLearningStyle(payload: UpdateLearningStylePayload) {
  const user = await apiRequest<BackendUser>("/api/users/me/learning-style", {
    method: "PATCH",
    auth: true,
    body: payload,
  });

  return asUser(user);
}
