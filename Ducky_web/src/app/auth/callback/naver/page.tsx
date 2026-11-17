"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { loginWithNaver } from "@/lib/api/auth";
import { ensureDefaultRaspberryLinked } from "@/lib/api/devices";
import { getNaverRedirectUri, NAVER_OAUTH_STATE_KEY } from "@/lib/auth/naver";
import { defaultUser, USER_STORAGE_KEY } from "@/lib/auth/storage";
import { useLocalStorage } from "@/hooks/useLocalStorage";

function NaverCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [, setUser] = useLocalStorage(USER_STORAGE_KEY, defaultUser);
  const [message, setMessage] = useState("네이버 로그인을 처리하는 중입니다.");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function completeLogin() {
      const naverError = searchParams.get("error");
      if (naverError) {
        setFailed(true);
        setMessage("네이버 로그인이 취소되었거나 실패했습니다.");
        return;
      }

      const code = searchParams.get("code");
      const state = searchParams.get("state");
      const savedState = window.sessionStorage.getItem(NAVER_OAUTH_STATE_KEY);
      window.sessionStorage.removeItem(NAVER_OAUTH_STATE_KEY);

      if (!code || !state) {
        setFailed(true);
        setMessage("네이버 인증 정보가 없습니다.");
        return;
      }

      if (savedState && state !== savedState) {
        setFailed(true);
        setMessage("네이버 로그인 요청 정보가 일치하지 않습니다.");
        return;
      }

      try {
        const user = await loginWithNaver({
          code,
          state,
          redirectUri: getNaverRedirectUri(),
        });
        if (cancelled) {
          return;
        }

        setUser(user);
        try {
          await ensureDefaultRaspberryLinked(user.id);
        } catch (deviceLinkError) {
          console.warn("Default Raspberry link failed", deviceLinkError);
        }

        router.replace(user.onboarded ? "/dashboard" : "/onboarding");
      } catch (error) {
        console.error("Naver login failed", error);
        if (!cancelled) {
          setFailed(true);
          setMessage("네이버 로그인 처리에 실패했습니다.");
        }
      }
    }

    void completeLogin();

    return () => {
      cancelled = true;
    };
  }, [router, searchParams, setUser]);

  return (
    <main className="flex min-h-dvh items-center justify-center bg-[#FAF8F5] px-6 text-center">
      <div className="flex max-w-sm flex-col items-center gap-4">
        {!failed && (
          <Loader2 className="size-8 animate-spin text-[#03C75A]" aria-hidden="true" />
        )}
        <h1 className="text-xl font-bold text-gray-950">
          {failed ? "로그인 실패" : "Ducky 로그인"}
        </h1>
        <p className="text-sm leading-relaxed text-gray-600 break-keep">{message}</p>
        {failed && (
          <button
            className="mt-2 rounded-lg bg-[#03C75A] px-4 py-2 text-sm font-bold text-white"
            onClick={() => router.replace("/")}
            type="button"
          >
            로그인으로 돌아가기
          </button>
        )}
      </div>
    </main>
  );
}

export default function NaverCallbackPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-dvh items-center justify-center bg-[#FAF8F5]">
          <Loader2 className="size-8 animate-spin text-[#03C75A]" aria-hidden="true" />
        </main>
      }
    >
      <NaverCallbackContent />
    </Suspense>
  );
}
