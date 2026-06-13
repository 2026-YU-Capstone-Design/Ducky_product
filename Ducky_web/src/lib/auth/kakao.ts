export const KAKAO_OAUTH_STATE_KEY = "ducky_kakao_oauth_state";

export function getKakaoRedirectUri() {
  if (typeof window === "undefined") {
    return "/auth/callback/kakao";
  }

  return `${window.location.origin}/auth/callback/kakao`;
}

export function createOAuthState() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function buildKakaoAuthorizeUrl(state: string) {
  const restApiKey = process.env.NEXT_PUBLIC_KAKAO_REST_API_KEY;
  if (!restApiKey) {
    throw new Error("카카오 REST API 키가 설정되지 않았습니다.");
  }

  const params = new URLSearchParams({
    response_type: "code",
    client_id: restApiKey,
    redirect_uri: getKakaoRedirectUri(),
    scope: "profile_nickname",
    state,
  });

  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
}
