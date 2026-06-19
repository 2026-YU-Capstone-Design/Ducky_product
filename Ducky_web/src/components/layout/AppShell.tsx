"use client";

import Image from "next/image";
import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getMe } from "@/lib/api/auth";
import { clearAccessToken, getAccessToken } from "@/lib/api/client";
import { ensureDefaultRaspberryLinked } from "@/lib/api/devices";
import { clearPersistedUser, persistUser } from "@/lib/auth/storage";
import { BottomNav } from "./BottomNav";
import { Sidebar } from "./Sidebar";

const MIN_APP_SPLASH_MS = 1100;

function AppLaunchSplash() {
  return (
    <div className="flex h-dvh w-full flex-col items-center justify-center overflow-hidden bg-[#FAF8F5] px-6 text-center text-[#FECA43] transition-colors dark:bg-[#171512]">
      <div className="relative flex size-40 items-center justify-center sm:size-48">
        <div className="absolute inset-4 rounded-full bg-[#FECA43]/20 blur-2xl dark:bg-[#FECA43]/10" />
        <Image
          src="/icons/ducky-character.png"
          alt="Ducky"
          width={160}
          height={160}
          priority
          className="relative z-10 size-32 animate-pulse object-contain sm:size-40"
        />
      </div>
      <p className="mt-5 text-4xl font-bold tracking-tight sm:text-5xl">
        Ducky
      </p>
    </div>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);
  const [deviceLinkWarning, setDeviceLinkWarning] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let readyTimer: number | undefined;
    const startedAt = Date.now();

    function showReadyAfterSplash() {
      const elapsed = Date.now() - startedAt;
      const remaining = Math.max(0, MIN_APP_SPLASH_MS - elapsed);

      readyTimer = window.setTimeout(() => {
        if (!cancelled) {
          setIsReady(true);
        }
      }, remaining);
    }

    async function verifyUser() {
      if (!getAccessToken()) {
        clearPersistedUser();
        router.replace("/");
        return;
      }

      try {
        const user = await getMe();
        if (cancelled) {
          return;
        }

        persistUser(user);
        void ensureDefaultRaspberryLinked(user.id).catch((deviceLinkError) => {
          console.warn("Default Raspberry link failed", deviceLinkError);
          if (!cancelled) {
            const message =
              deviceLinkError instanceof Error
                ? deviceLinkError.message
                : "라즈베리 디바이스 연결에 실패했습니다.";
            setDeviceLinkWarning(
              `IoT/RAG 연동을 위해 라즈베리 디바이스 연결이 필요합니다. (${message})`,
            );
          }
        });

        if (!user.onboarded) {
          router.replace("/onboarding");
          return;
        }

        showReadyAfterSplash();
      } catch {
        if (cancelled) {
          return;
        }

        clearAccessToken();
        clearPersistedUser();
        router.replace("/");
      }
    }

    void verifyUser();

    return () => {
      cancelled = true;
      if (readyTimer) {
        window.clearTimeout(readyTimer);
      }
    };
  }, [router]);

  if (!isReady) {
    return <AppLaunchSplash />;
  }

  return (
    <div className="relative flex h-dvh overflow-hidden bg-[#FAF8F5] text-[#2E2A22] transition-colors dark:bg-[#171512] dark:text-white">
      <Sidebar />
      <main className="flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-y-auto overscroll-contain pb-[calc(4.5rem+env(safe-area-inset-bottom))] md:pb-0">
        {deviceLinkWarning ? (
          <div className="border-b border-[#FECA43]/60 bg-[#FFF7E0] px-4 py-3 text-sm text-[#6B5200] dark:border-[#FECA43]/40 dark:bg-[#2A251D] dark:text-[#FECA43]">
            {deviceLinkWarning}
          </div>
        ) : null}
        {children}
      </main>
      <BottomNav />
    </div>
  );
}
