import { Home, RefreshCw, WifiOff } from "lucide-react";

export default function OfflinePage() {
  return (
    <main className="flex min-h-dvh items-center justify-center bg-[#FAF8F5] px-5 py-10 text-[#2E2A22] dark:bg-[#171512] dark:text-white">
      <section className="w-full max-w-xl">
        <div className="flex size-14 items-center justify-center rounded-lg bg-[#FFF1BC] text-[#B88700] dark:bg-[#2A251D] dark:text-[#FECA43]">
          <WifiOff className="size-7" aria-hidden="true" />
        </div>

        <p className="mt-6 text-sm font-semibold text-[#B88700] dark:text-[#FECA43]">
          Offline
        </p>
        <h1 className="mt-3 text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
          연결이 돌아오면 이어서 학습할 수 있어요.
        </h1>
        <p className="mt-4 text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300">
          네트워크가 끊겨 현재 화면을 불러오지 못했습니다. 이미 열어본
          정적 화면과 기본 학습 자료는 브라우저 캐시에 남아 있을 수 있습니다.
        </p>

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <a
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-[#FECA43] px-4 text-sm font-bold text-[#2E2A22] transition-colors hover:bg-[#F5B522] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43]"
            href="/dashboard"
          >
            <Home className="size-4" aria-hidden="true" />
            대시보드로 이동
          </a>
          <a
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg border border-[#E7DDC8] bg-white px-4 text-sm font-bold text-gray-800 transition-colors hover:bg-[#FFF7E0] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:border-white/10 dark:bg-[#24211D] dark:text-white dark:hover:bg-[#2A251D]"
            href=""
          >
            <RefreshCw className="size-4" aria-hidden="true" />
            다시 시도
          </a>
        </div>
      </section>
    </main>
  );
}
