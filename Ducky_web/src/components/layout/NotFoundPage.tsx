"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState } from "react";
import { Home, MessageCircle, SearchX } from "lucide-react";
import { AppShell } from "./AppShell";

const splashImages = [1, 2, 3, 4, 5, 6, 7];

function DuckyCharacter({
  currentSplash,
  className = "",
}: {
  currentSplash: number;
  className?: string;
}) {
  return (
    <div
      aria-label="페이지를 찾는 Ducky 캐릭터"
      className={`relative mx-auto h-64 w-full max-w-[20rem] items-center justify-center sm:h-72 lg:mx-0 lg:h-80 lg:max-w-none ${className}`}
    >
      <div className="absolute left-1/2 top-8 size-48 -translate-x-1/2 rounded-full bg-[#FECA43]/15 blur-3xl dark:bg-[#FECA43]/10" />
      <div className="absolute bottom-8 left-1/2 h-8 w-44 -translate-x-1/2 rounded-[999px] bg-[#2E2A22]/10 blur-md dark:bg-black/40" />

      <div className="absolute right-3 top-4 z-20 rounded-lg border border-[#E7DDC8] bg-white/90 px-3 py-2 text-sm font-extrabold tracking-[0.18em] text-[#B88700] shadow-sm backdrop-blur dark:border-white/10 dark:bg-[#24211D]/90 dark:text-[#FECA43]">
        404
      </div>

      <div className="relative h-56 w-56 transition-transform duration-500 hover:scale-105 sm:h-64 sm:w-64 lg:h-72 lg:w-72">
        {splashImages.map((num) => (
          <Image
            key={num}
            src={`/images/splash${num}.png`}
            alt={num === currentSplash ? "Ducky 캐릭터" : ""}
            fill
            priority={num === 1}
            sizes="(min-width: 1024px) 288px, 256px"
            className={`object-contain transition-all duration-1000 ease-in-out ${
              currentSplash === num
                ? "z-10 scale-100 opacity-100"
                : "z-0 scale-95 opacity-0"
            }`}
          />
        ))}
      </div>
    </div>
  );
}

export function NotFoundPage() {
  const [currentSplash, setCurrentSplash] = useState(1);

  useEffect(() => {
    const interval = window.setInterval(() => {
      setCurrentSplash((prev) => (prev % splashImages.length) + 1);
    }, 2400);

    return () => window.clearInterval(interval);
  }, []);

  return (
    <AppShell>
      <section className="mx-auto flex w-full max-w-6xl flex-1 flex-col justify-center px-5 py-10 sm:px-8 lg:px-10">
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_minmax(17rem,22rem)] lg:items-center lg:gap-16">
          <div className="min-w-0">
            <div className="mb-6 flex size-16 items-center justify-center rounded-lg bg-[#FFF1BC] text-[#B88700] dark:bg-[#2A251D] dark:text-[#FECA43]">
              <SearchX className="size-8" aria-hidden="true" />
            </div>

            <p className="text-[clamp(3.75rem,18vw,7rem)] font-black leading-none tracking-[0.08em] text-[#B88700]">
              404
            </p>
            <DuckyCharacter
              currentSplash={currentSplash}
              className="mt-7 flex lg:hidden"
            />
            <h1 className="mt-3 text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
              찾는 페이지가 없어요
            </h1>
            <p className="mt-4 max-w-2xl text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300 sm:text-lg">
              주소가 바뀌었거나, 아직 만들어지지 않은 페이지일 수 있어요.
              Ducky가 다시 길을 찾을 수 있게 시작 화면으로 돌아가 볼게요.
            </p>

            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/dashboard"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-lg bg-[#FECA43] px-5 text-sm font-bold text-[#2E2A22] shadow-sm transition-colors hover:bg-[#F5B522] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43]"
              >
                <Home className="size-4" aria-hidden="true" />
                홈으로 가기
              </Link>
              <Link
                href="/chat"
                className="inline-flex min-h-12 items-center justify-center gap-2 rounded-lg border border-[#E7DDC8] bg-white px-5 text-sm font-bold text-[#2E2A22] shadow-sm transition-colors hover:border-[#FECA43] hover:bg-[#FFF7E0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:border-white/10 dark:bg-[#24211D] dark:text-white dark:hover:bg-white/10"
              >
                <MessageCircle className="size-4" aria-hidden="true" />
                대화로 가기
              </Link>
            </div>
          </div>

          <DuckyCharacter
            currentSplash={currentSplash}
            className="hidden lg:flex"
          />
        </div>
      </section>
    </AppShell>
  );
}
