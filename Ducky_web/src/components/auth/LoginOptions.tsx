"use client";

import { Dispatch, SetStateAction } from "react";
import Image from "next/image";
import { Mail, MessageCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * 로그인 옵션 선택 화면 컴포넌트에 전달되는 props
 */
interface LoginOptionsProps {
  /** 현재 스플래시 이미지 인덱스 (1~7) */
  currentSplash: number;
  /** Comfortaa 폰트 클래스 이름 */
  comfortaaClassName: string;
  /** 로그인 처리 중 여부 (로딩 상태) */
  isLoading: boolean;
  /** 이메일 모드 (options, login, signup)를 변경하는 함수 */
  setEmailMode: Dispatch<SetStateAction<"options" | "login" | "signup">>;
  /** 소셜 로그인 버튼 클릭 핸들러 (kakao, naver) */
  handleSocialLogin: (platform: "kakao" | "naver") => void;
}

/**
 * 로그인 옵션 (이메일, 카카오, 네이버) 선택 화면 컴포넌트
 */
export function LoginOptions({
  currentSplash,
  comfortaaClassName,
  isLoading,
  setEmailMode,
  handleSocialLogin,
}: LoginOptionsProps) {
  return (
    <div className="flex w-full flex-col gap-10 sm:gap-12 lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(20rem,24rem)] lg:items-center lg:gap-16">
      <div className="flex flex-col items-center space-y-5 sm:space-y-6 lg:items-start lg:space-y-7">
        <div
          className="relative flex h-36 w-36 items-center justify-center transition-all duration-500 hover:scale-105 sm:h-44 sm:w-44 lg:h-64 lg:w-64"
          aria-label="Ducky 캐릭터"
        >
          {[1, 2, 3, 4, 5, 6, 7].map((num) => (
            <Image
              key={num}
              src={`/images/splash${num}.png`}
              alt=""
              fill
              sizes="(max-width: 640px) 9rem, (max-width: 1024px) 11rem, 16rem"
              className={`absolute inset-0 h-full w-full object-contain transition-all duration-1000 ease-in-out ${
                currentSplash === num
                  ? "opacity-100 scale-100 z-10"
                  : "opacity-0 scale-95 z-0 pointer-events-none"
              }`}
              aria-hidden="true"
            />
          ))}
        </div>
        <h1
          className={`text-[clamp(3.25rem,13vw,5.75rem)] font-bold tracking-tight text-[#FECA43] ${comfortaaClassName} select-none`}
        >
          Ducky
        </h1>
      </div>

      <div className="mx-auto w-full max-w-md space-y-3.5 lg:mx-0">
        <Button
          onClick={() => setEmailMode("login")}
          disabled={isLoading}
          className="h-auto min-h-13 w-full whitespace-normal rounded-xl bg-[#EFECE5] px-4 py-3 text-center text-base font-bold leading-tight text-[#333333] shadow-none transition-transform hover:bg-[#E5E1D8] active:scale-[0.99] disabled:opacity-50"
        >
          <Mail className="h-5 w-5 text-[#333333]" />
          이메일로 로그인
        </Button>
        <Button
          onClick={() => handleSocialLogin("kakao")}
          disabled={isLoading}
          className="h-auto min-h-13 w-full whitespace-normal rounded-xl bg-[#FFE600] px-4 py-3 text-center text-base font-bold leading-tight text-[#191919] shadow-none transition-transform hover:bg-[#F2DA00] active:scale-[0.99] disabled:opacity-50"
        >
          {isLoading ? (
            <Loader2 className="h-5 w-5 animate-spin" />
          ) : (
            <MessageCircle className="h-5 w-5 fill-[#191919] text-[#191919]" />
          )}
          카카오로 로그인
        </Button>
        <Button
          onClick={() => handleSocialLogin("naver")}
          disabled={isLoading}
          className="h-auto min-h-13 w-full whitespace-normal rounded-xl bg-[#03C75A] px-4 py-3 text-center text-base font-bold leading-tight text-white shadow-none transition-transform hover:bg-[#02B350] active:scale-[0.99] disabled:opacity-50"
        >
          {isLoading ? (
            <Loader2 className="h-5 w-5 animate-spin" />
          ) : (
            <span className="font-extrabold text-lg mr-1 select-none">N</span>
          )}
          네이버로 로그인
        </Button>
      </div>
    </div>
  );
}
