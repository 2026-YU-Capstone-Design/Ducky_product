"use client";

import type { ReactNode } from "react";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getMe } from "@/lib/api/auth";
import { clearAccessToken, getAccessToken } from "@/lib/api/client";
import { ensureDefaultRaspberryLinked } from "@/lib/api/devices";
import { clearPersistedUser, persistUser } from "@/lib/auth/storage";
import { BottomNav } from "./BottomNav";
import { Sidebar } from "./Sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    let cancelled = false;

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
        });

        if (!user.onboarded) {
          router.replace("/onboarding");
          return;
        }

        setIsReady(true);
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
    };
  }, [router]);

  if (!isReady) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-[#FAF8F5] px-5 text-sm font-semibold text-[#B88700] dark:bg-[#171512] dark:text-[#FECA43]">
        Ducky를 준비하는 중입니다.
      </div>
    );
  }

  return (
    <div className="min-h-dvh bg-[#FAF8F5] text-[#2E2A22] transition-colors dark:bg-[#171512] dark:text-white md:flex">
      <Sidebar />
      <main className="flex min-h-dvh min-w-0 flex-1 flex-col pb-24 md:pb-0">
        {children}
      </main>
      <BottomNav />
    </div>
  );
}
