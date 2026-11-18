"use client";

import { Dispatch, SetStateAction } from "react";
import { ChevronLeft } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * 회원가입 1단계 컴포넌트에 전달되는 props
 */
interface SignupStep1Props {
  /** 사용자가 입력한 이름 */
  name: string;
  /** 이름 상태를 업데이트하는 함수 */
  setName: Dispatch<SetStateAction<string>>;
  /** 회원가입 단계를 변경하는 함수 (1, 2, 3) */
  setSignupStep: Dispatch<SetStateAction<1 | 2 | 3>>;
  /** 이메일 모드 (options, login, signup)를 변경하는 함수 */
  setEmailMode: Dispatch<SetStateAction<"options" | "login" | "signup">>;
  /** 에러 메시지 상태를 업데이트하는 함수 */
  setError: Dispatch<SetStateAction<string>>;
}

/**
 * 회원가입 1단계: 이름 입력 화면 컴포넌트
 */
export function SignupStep1({
  name,
  setName,
  setSignupStep,
  setEmailMode,
  setError,
}: SignupStep1Props) {
  return (
    <div className="animate-fade-in flex w-full flex-col items-start space-y-8 pt-0 duration-300 sm:space-y-10 sm:pt-2">
      <button
        type="button"
        onClick={() => {
          setEmailMode("login");
          setError("");
        }}
        className="-ml-1.5 cursor-pointer"
        aria-label="뒤로가기"
      >
        <ChevronLeft className="h-7 w-7 text-[#333333]" />
      </button>

      <h2 className="break-keep select-none text-2xl font-extrabold leading-tight tracking-tight text-[#333333] sm:text-3xl">
        이름을 입력해주세요
      </h2>

      <div className="w-full space-y-2 pt-4">
        <label className="select-none text-xs font-bold text-zinc-400">
          이름 <span className="text-red-500">*</span>
        </label>

        <div className="flex w-full items-center gap-3 border-b border-zinc-300 py-2 transition-colors focus-within:border-[#FECA43]">
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="min-w-0 flex-1 bg-transparent text-lg font-bold text-[#333333] placeholder:text-zinc-300 focus:outline-none"
            placeholder="이름 입력"
            required
          />
          <div
            aria-hidden="true"
            className="h-9 w-[92px] shrink-0 rounded-lg opacity-0 pointer-events-none"
          />
        </div>
        <div className="min-h-[20px]" aria-hidden="true" />
      </div>

      <div className="w-full pt-10">
        <Button
          type="button"
          onClick={() => setSignupStep(2)}
          disabled={name.trim().length === 0}
          className={`h-auto min-h-14 w-full rounded-xl border-none px-4 py-3 text-base font-bold leading-tight whitespace-normal shadow-none transition-all active:scale-[0.99] cursor-pointer ${
            name.trim().length > 0
              ? "bg-[#FECA43] text-white hover:bg-[#FECA43]/90"
              : "bg-[#EFECE5] text-[#9E988E] hover:bg-[#EFECE5]"
          }`}
        >
          다음
        </Button>
      </div>
    </div>
  );
}
