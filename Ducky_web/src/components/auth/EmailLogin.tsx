"use client";

import { Dispatch, SetStateAction, FormEvent } from "react";
import { ChevronLeft, AlertCircle, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

/**
 * 이메일 로그인 화면 컴포넌트에 전달되는 props
 */
interface EmailLoginProps {
  /** 사용자가 입력한 이메일 */
  email: string;
  /** 이메일 상태를 업데이트하는 함수 */
  setEmail: Dispatch<SetStateAction<string>>;
  /** 사용자가 입력한 비밀번호 */
  password: string;
  /** 비밀번호 상태를 업데이트하는 함수 */
  setPassword: Dispatch<SetStateAction<string>>;
  /** 로그인 에러 메시지 */
  error: string;
  /** 로그인 요청 중 여부 (로딩 상태) */
  isLoading: boolean;
  /** 이메일 모드 (options, login, signup)를 변경하는 함수 */
  setEmailMode: Dispatch<SetStateAction<"options" | "login" | "signup">>;
  /** 에러 메시지 상태를 업데이트하는 함수 */
  setError: Dispatch<SetStateAction<string>>;
  /** 이메일 로그인 폼 제출 핸들러 */
  handleEmailLoginSubmit: (e: FormEvent) => void;
  /** 회원가입 단계를 변경하는 함수 (1, 2, 3) */
  setSignupStep: Dispatch<SetStateAction<1 | 2 | 3>>;
}

/**
 * 이메일 로그인 양식 컴포넌트
 */
export function EmailLogin({
  email,
  setEmail,
  password,
  setPassword,
  error,
  isLoading,
  setEmailMode,
  setError,
  handleEmailLoginSubmit,
  setSignupStep,
}: EmailLoginProps) {
  return (
    <div className="animate-fade-in flex w-full flex-col items-start space-y-8 pt-0 duration-300 sm:space-y-10 sm:pt-2">
      <button
        type="button"
        onClick={() => {
          setEmailMode("options");
          setError("");
        }}
        className="-ml-1.5 cursor-pointer"
        aria-label="뒤로가기"
      >
        <ChevronLeft className="h-7 w-7 text-[#333333]" />
      </button>

      <h2 className="break-keep select-none text-2xl font-extrabold leading-tight tracking-tight text-[#333333] sm:text-3xl">
        이메일로 로그인
      </h2>

      <form onSubmit={handleEmailLoginSubmit} className="w-full">
        <div className="space-y-4">
          <div className="min-h-[58px]">
            {error && (
              <div className="flex items-start gap-2 rounded-xl border border-red-100 bg-red-50 p-3.5 text-sm text-red-600">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span className="min-w-0 break-words">{error}</span>
              </div>
            )}
          </div>

          <Input
            type="email"
            placeholder="이메일 입력"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="h-auto min-h-13 w-full rounded-xl border-none bg-[#EFECE5] px-4 py-3 text-base font-medium text-[#333333] shadow-none transition-all placeholder-[#9E988E] focus:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
            required
          />

          <Input
            type="password"
            placeholder="비밀번호 입력"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="h-auto min-h-13 w-full rounded-xl border-none bg-[#EFECE5] px-4 py-3 text-base font-medium text-[#333333] shadow-none transition-all placeholder-[#9E988E] focus:outline-none focus-visible:ring-0 focus-visible:ring-offset-0"
            required
          />

          <div className="pt-2">
            <Button
              type="submit"
              disabled={isLoading}
              className="h-auto min-h-13 w-full rounded-xl border-none bg-[#FECA43] px-4 py-3 text-base font-bold leading-tight text-white whitespace-normal shadow-none transition-all hover:bg-[#FECA43]/90 active:scale-[0.99] cursor-pointer"
            >
              {isLoading ? (
                <Loader2 className="h-5 w-5 animate-spin" />
              ) : (
                "로그인"
              )}
            </Button>
          </div>
        </div>
        <div className="flex w-full items-center justify-center text-sm text-zinc-500 pt-4">
          <button
            type="button"
            onClick={() => {
              setEmailMode("signup");
              setSignupStep(1);
              setError("");
            }}
            className="cursor-pointer border-b border-[#FECA43] font-semibold text-[#FECA43]"
          >
            회원가입
          </button>
        </div>
      </form>
    </div>
  );
}
