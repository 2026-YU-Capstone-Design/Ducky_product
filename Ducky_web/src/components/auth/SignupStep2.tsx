"use client";

import { ChangeEvent, Dispatch, SetStateAction } from "react";
import { ChevronLeft, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * 회원가입 2단계 컴포넌트에 전달되는 props
 */
interface SignupStep2Props {
  /** 사용자가 입력한 이메일 */
  email: string;
  /** 이메일 관련 에러 메시지 */
  emailError: string;
  /** 이메일 중복 확인 성공 메시지 */
  emailSuccess: string;
  /** 이메일 중복 확인 완료 여부 */
  isEmailChecked: boolean;
  /** 이메일 중복 확인 요청 중 여부 (로딩 상태) */
  isCheckingEmail: boolean;
  /** 이메일 입력 변경 핸들러 */
  handleEmailChange: (e: ChangeEvent<HTMLInputElement>) => void;
  /** 이메일 중복 확인 버튼 클릭 핸들러 */
  handleDuplicateCheck: () => void | Promise<void>;
  /** 회원가입 단계를 변경하는 함수 (1, 2, 3) */
  setSignupStep: Dispatch<SetStateAction<1 | 2 | 3>>;
}

/**
 * 회원가입 2단계: 이메일 입력 및 중복 확인 화면 컴포넌트
 */
export function SignupStep2({
  email,
  emailError,
  emailSuccess,
  isEmailChecked,
  isCheckingEmail,
  handleEmailChange,
  handleDuplicateCheck,
  setSignupStep,
}: SignupStep2Props) {
  return (
    <div className="animate-fade-in flex w-full flex-col items-start space-y-8 pt-0 duration-300 sm:space-y-10 sm:pt-2">
      <button
        type="button"
        onClick={() => setSignupStep(1)}
        className="-ml-1.5 cursor-pointer"
        aria-label="뒤로가기"
      >
        <ChevronLeft className="h-7 w-7 text-[#333333]" />
      </button>

      <h2 className="break-keep select-none text-2xl font-extrabold leading-tight tracking-tight text-[#333333] sm:text-3xl">
        이메일을 입력해주세요
      </h2>

      <div className="w-full space-y-2 pt-4">
        <label className="select-none text-xs font-bold text-zinc-400">
          이메일 <span className="text-red-500">*</span>
        </label>

        <div className="flex w-full items-center gap-3 border-b border-zinc-300 py-2 transition-colors focus-within:border-[#FECA43]">
          <input
            type="email"
            value={email}
            onChange={handleEmailChange}
            className="min-w-0 flex-1 bg-transparent text-lg font-bold text-[#333333] placeholder:text-zinc-300 focus:outline-none"
            placeholder="이메일 입력"
            required
          />

          <Button
            type="button"
            disabled={
              !email || !!emailError || isEmailChecked || isCheckingEmail
            }
            onClick={() => void handleDuplicateCheck()}
            className={`h-9 w-[88px] shrink-0 rounded-lg cursor-pointer border px-3 text-sm font-semibold shadow-none transition-colors ${
              isEmailChecked || !email || !!emailError
                ? "border-zinc-200 bg-zinc-100 text-zinc-400 hover:bg-zinc-100"
                : "border-[#FECA43] bg-white text-[#FECA43] hover:bg-[#FECA43]/10"
            }`}
          >
            {isCheckingEmail ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              "중복확인"
            )}
          </Button>
        </div>

        <div className="min-h-[20px] pt-1">
          {emailError ? (
            <p className="text-xs font-bold text-red-500">{emailError}</p>
          ) : emailSuccess ? (
            <p className="text-xs font-bold text-emerald-500">{emailSuccess}</p>
          ) : null}
        </div>
      </div>

      <div className="w-full pt-10">
        <Button
          type="button"
          onClick={() => setSignupStep(3)}
          disabled={!isEmailChecked}
          className={`h-auto min-h-14 w-full cursor-pointer rounded-xl border-none px-4 py-3 text-base font-bold leading-tight whitespace-normal shadow-none transition-all active:scale-[0.99] ${
            isEmailChecked
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
