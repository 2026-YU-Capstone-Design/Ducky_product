"use client";

import Image from "next/image";
import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";
import { AppShell } from "./AppShell";

const splashImages = [1, 2, 3, 4, 5, 6, 7];

export function LoadingPage() {
  const [currentSplash, setCurrentSplash] = useState(1);

  useEffect(() => {
    const interval = window.setInterval(() => {
      setCurrentSplash((prev) => (prev % splashImages.length) + 1);
    }, 1800);

    return () => window.clearInterval(interval);
  }, []);

  return (
    <AppShell>
      <section
        role="status"
        aria-live="polite"
        className="mx-auto flex w-full max-w-4xl flex-1 flex-col items-center justify-center px-5 py-10 text-center sm:px-8 lg:px-10"
      >
        <div className="relative flex h-64 w-full max-w-[18rem] items-center justify-center sm:h-72 sm:max-w-[20rem]">
          <div className="absolute left-1/2 top-8 size-48 -translate-x-1/2 rounded-full bg-[#FECA43]/15 blur-3xl dark:bg-[#FECA43]/10" />
          <div className="absolute bottom-8 left-1/2 h-8 w-44 -translate-x-1/2 rounded-[999px] bg-[#2E2A22]/10 blur-md dark:bg-black/40" />

          <div className="relative h-56 w-56 transition-transform duration-500 sm:h-64 sm:w-64">
            {splashImages.map((num) => (
              <Image
                key={num}
                src={`/images/splash${num}.png`}
                alt={num === currentSplash ? "Ducky 캐릭터" : ""}
                fill
                priority={num === 1}
                sizes="256px"
                className={`object-contain transition-all duration-1000 ease-in-out ${
                  currentSplash === num
                    ? "z-10 scale-100 opacity-100"
                    : "z-0 scale-95 opacity-0"
                }`}
              />
            ))}
          </div>
        </div>

        <div className="mt-5 flex items-center justify-center gap-2 text-[#B88700]">
          <LoaderCircle className="size-5 animate-spin" aria-hidden="true" />
          <p className="text-sm font-bold tracking-[0.18em]">THINKING</p>
        </div>

        <h1 className="mt-4 text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
          생각하는 중
        </h1>
        <p className="mt-3 max-w-xl text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300 sm:text-lg">
          Ducky가 차분히 생각을 정리하고 있어요.
        </p>

        <div className="mt-8 h-2 w-full max-w-xs overflow-hidden rounded-full bg-[#E7DDC8] dark:bg-white/10">
          <div className="h-full w-1/2 animate-[loading-slide_1.4s_ease-in-out_infinite] rounded-full bg-[#FECA43]" />
        </div>
      </section>
    </AppShell>
  );
}
