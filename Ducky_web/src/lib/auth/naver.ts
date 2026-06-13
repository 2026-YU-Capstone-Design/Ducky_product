export const NAVER_OAUTH_STATE_KEY = "ducky_naver_oauth_state";

export function getNaverRedirectUri() {
  if (typeof window === "undefined") {
    return "/auth/callback/naver";
  }

  return `${window.location.origin}/auth/callback/naver`;
}

export function createNaverOAuthState() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }

  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function buildNaverAuthorizeUrl(state: string) {
  const clientId = process.env.NEXT_PUBLIC_NAVER_CLIENT_ID;
  if (!clientId) {
    throw new Error("네이버 Client ID가 설정되지 않았습니다.");
  }

  const params = new URLSearchParams({
    response_type: "code",
    client_id: clientId,
    redirect_uri: getNaverRedirectUri(),
    state,
  });

  return `https://nid.naver.com/oauth2.0/authorize?${params.toString()}`;
}
