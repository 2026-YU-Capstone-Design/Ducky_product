"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { onboardingQuestions } from "@/data/onboardingQuestions";
import { QuestionCard } from "./QuestionCard";
import { useLocalStorage } from "@/hooks/useLocalStorage";
import { ArrowRight, Clock, Layers, ListChecks } from "lucide-react";
import { DuckyMark } from "@/components/layout/DuckyMark";
import { DuckyWordmark } from "@/components/layout/DuckyWordmark";
import { Comfortaa } from "next/font/google";
import { updateLearningStyle } from "@/lib/api/auth";
import { getAccessToken } from "@/lib/api/client";
import { defaultUser, persistUser, USER_STORAGE_KEY } from "@/lib/auth/storage";
import type { LearningStyle } from "@/types/user";

const comfortaa = Comfortaa({
  subsets: ["latin"],
  weight: ["700"],
  variable: "--font-comfortaa",
});

type LearningStyleResult = {
  processingStyle: string;
  structureStyle: string;
  representationStyle: string;
};

export function OnboardingFlow() {
  const router = useRouter();
  const [step, setStep] = useState<"intro" | "questions">("intro");
  const [currentIndex, setCurrentIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, number>>({
    active: 0,
    reflective: 0,
    sequential: 0,
    global: 0,
    visual: 0,
    verbal: 0,
  });
  const [isAnimating, setIsAnimating] = useState(false);
  const [showResult, setShowResult] = useState(false);
  const [resultData, setResultData] = useState<LearningStyleResult | null>(null);
  const [saveError, setSaveError] = useState("");

  const [user, setUser] = useLocalStorage(USER_STORAGE_KEY, defaultUser);

  const saveLearningStyle = async (learningStyle: LearningStyle) => {
    if (!getAccessToken()) {
      router.replace("/");
      return false;
    }

    setSaveError("");

    try {
      const savedUser = await updateLearningStyle({
        ...learningStyle,
        onboarded: true,
      });
      setUser(savedUser);
      persistUser(savedUser);
      return true;
    } catch (error) {
      console.error("Learning style save failed", error);
      setUser(user);
      setSaveError("학습 유형 저장에 실패했습니다. 잠시 후 다시 시도해 주세요.");
      return false;
    }
  };

  const handleSelect = (axis: string, value: string) => {
    if (isAnimating) return;

    const newAnswers = {
      ...answers,
      [value]: answers[value] + 1
    };
    setAnswers(newAnswers);

    setIsAnimating(true);

    setTimeout(() => {
      if (currentIndex < onboardingQuestions.length - 1) {
        setCurrentIndex(prev => prev + 1);
        setIsAnimating(false);
      } else {
        void calculateAndSaveResult(newAnswers);
      }
    }, 300); // Wait for fade out animation
  };

  const calculateAndSaveResult = async (finalAnswers: Record<string, number>) => {
    const getStyle = (typeA: string, typeB: string, titleA: string, titleB: string) => {
      const scoreA = finalAnswers[typeA];
      const scoreB = finalAnswers[typeB];
      const diff = Math.abs(scoreA - scoreB);

      if (diff === 0) return "Mixed";
      return scoreA > scoreB ? titleA : titleB;
    };

    const processingStyle = getStyle("active", "reflective", "Active", "Reflective");
    const structureStyle = getStyle("sequential", "global", "Sequential", "Global");
    const representationStyle = getStyle("visual", "verbal", "Visual", "Verbal");

    const result = {
      processingStyle,
      structureStyle,
      representationStyle
    };

    setResultData(result);
    setShowResult(true);

    // Update user context
    const saved = await saveLearningStyle({
      processing: (
        processingStyle === "Mixed" ? "active" : processingStyle.toLowerCase()
      ) as LearningStyle["processing"],
      expression: (
        representationStyle === "Mixed"
          ? "visual"
          : representationStyle.toLowerCase()
      ) as LearningStyle["expression"],
      understanding: (
        structureStyle === "Mixed" ? "sequential" : structureStyle.toLowerCase()
      ) as LearningStyle["understanding"],
    });

    if (!saved) {
      setIsAnimating(false);
      return;
    }

    // Auto redirect to dashboard after showing results
    setTimeout(() => {
      router.push("/dashboard");
    }, 3500);
  };

  if (showResult && resultData) {
    const items = [
      { label: "정보 처리", value: resultData.processingStyle },
      { label: "이해 구조", value: resultData.structureStyle },
      { label: "정보 표현", value: resultData.representationStyle },
    ];

    return (
      <div
        className={`relative flex min-h-dvh w-full flex-col items-center justify-center overflow-hidden bg-[#FFFCF3] px-6 transition-colors dark:bg-[#2E2A22] ${comfortaa.className}`}
      >
        <div className="pointer-events-none absolute -top-40 -right-40 h-96 w-96 rounded-full bg-amber-200/30 dark:bg-[#FECA43]/10" />
        <div className="pointer-events-none absolute -bottom-28 -left-28 h-72 w-72 rounded-full bg-amber-100/40 dark:bg-[#FECA43]/5" />
        <div className="pointer-events-none absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[600px] w-[600px] rounded-full bg-amber-50/60 dark:bg-[#FECA43]/[0.03]" />

        <div className="relative z-10 w-full max-w-sm">
          <p className="mb-2 text-center text-[11px] font-bold uppercase tracking-[0.3em] text-amber-400/70 dark:text-white/60">
            Analysis Complete
          </p>
          <h1 className="mb-2 text-center text-[2.75rem] font-bold leading-tight tracking-tight text-gray-950 dark:text-white">
            분석 완료
            <span className="text-amber-400 dark:text-[#FECA43]">.</span>
          </h1>
          <p className="mb-10 text-center text-[13px] font-medium text-[#806437] dark:text-white/40">
            당신에게 맞는 학습 방식을 찾았어요
          </p>

          <div className="w-full overflow-hidden rounded-2xl border border-amber-200/60 bg-amber-100 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_8px_24px_-8px_rgba(217,119,6,0.12)] dark:border-white/10 dark:bg-white/[0.07]">
            {items.map((item, i) => (
              <div key={item.label}>
                <div className="flex items-center justify-between px-6 py-[1.2rem]">
                  <div className="flex items-center gap-2.5">
                    <div className="h-3.5 w-[3px] rounded-full bg-amber-400/60 dark:bg-[#FECA43]/50" />
                    <p className="text-[11px] font-semibold uppercase tracking-widest text-amber-700/50 dark:text-white/35">
                      {item.label}
                    </p>
                  </div>
                  <p className="text-[15px] font-black tracking-tight text-gray-950 dark:text-white">
                    {item.value}
                  </p>
                </div>
                {i < items.length - 1 && (
                  <div className="mx-6 h-px bg-amber-200/50 dark:bg-white/[0.08]" />
                )}
              </div>
            ))}
          </div>

          {saveError ? (
            <p role="alert" className="mt-6 text-center text-sm font-semibold text-red-700 dark:text-red-300">{saveError}</p>
          ) : (
          <div className="mt-10 flex flex-col items-center gap-3">
            <div className="flex gap-1.5">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="h-1.5 w-1.5 animate-bounce rounded-full bg-amber-400 dark:bg-[#FECA43]/60"
                  style={{ animationDelay: `${i * 160}ms` }}
                />
              ))}
            </div>
            <p className="text-[13px] font-medium text-[#806437] dark:text-white/60">
              맞춤 학습 환경을 준비하고 있어요
            </p>
          </div>
          )}
        </div>
      </div>
    );
  }

  // ─── INTRO ─────────────────────────────────────────────────────────────────
  if (step === "intro") {
    return (
      <div className="relative flex min-h-dvh w-full flex-col items-center justify-center overflow-hidden bg-[#FFFCF3] px-6 py-16 transition-colors dark:bg-[#16130A]">
        <div className="pointer-events-none absolute -right-40 -top-40 h-[500px] w-[500px] rounded-full bg-amber-100/60 dark:bg-amber-900/10" />
        <div className="pointer-events-none absolute -bottom-32 -left-20 h-80 w-80 rounded-full bg-[#fef3c7]/60 dark:bg-amber-900/5" />

        <div className="relative z-10 flex w-full max-w-sm flex-col items-center">
          <div className="mb-10 flex items-center gap-2.5">
            <DuckyMark size={30} className="shrink-0" />
            <DuckyWordmark />
          </div>

          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-2.5 py-1 mb-3 text-[10px] font-bold tracking-[0.12em] text-amber-700 uppercase dark:bg-amber-900/40 dark:text-amber-300">
            <span className="size-1.5 rounded-full bg-amber-400 animate-pulse" />
            Learning Style Test
          </span>

          <h1
            className={`mt-3 text-center text-[30px] font-black leading-[1.15] tracking-[-1px] text-gray-950 dark:text-white sm:text-[38px] ${comfortaa.className}`}
          >
            학습 유형 검사<span className="text-amber-400">.</span>
          </h1>

          <p className="mt-4 text-center text-[13px] font-medium leading-relaxed text-[#806437] dark:text-amber-400/70">
            Ducky는 더 적절한 학습 방식을 제공하기 위해
            <br />
            학습 유형 검사를 통해 유형을 파악해요.
          </p>

          <div className="mt-5 flex w-full items-center justify-between gap-4">
            <div className="flex-1 border-t border-amber-200/60 dark:border-amber-800/40" />
            <div className="flex-1 border-t border-amber-200/60 dark:border-amber-800/40" />
          </div>

          <div className="mt-6 grid w-full grid-cols-3 gap-3">
            {[
              {
                label: "문항 수",
                value: `${onboardingQuestions.length}개`,
                icon: ListChecks,
              },
              { label: "소요 시간", value: "약 3분", icon: Clock },
              { label: "분석 항목", value: "3가지", icon: Layers },
            ].map(({ label, value, icon: Icon }) => (
              <div
                key={label}
                className="relative overflow-hidden rounded-2xl border border-amber-200/40 bg-[#fef3c7] p-4 dark:border-white/8 dark:bg-[#1E1B16]"
              >
                <Icon
                  className="absolute right-3 top-3 size-3.5 text-amber-700/20 dark:text-amber-200/15"
                  strokeWidth={2}
                />
                <div className="pt-5">
                  <p className="text-[22px] font-black leading-none tracking-tight text-amber-900 dark:text-white tabular-nums">
                    {value}
                  </p>
                  <p className="mt-1.5 text-[10px] font-semibold text-amber-800/50 dark:text-gray-400">
                    {label}
                  </p>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-8 w-full space-y-3">
            <button
              onClick={() => setStep("questions")}
              className="flex w-full items-center justify-center gap-2 rounded-2xl bg-[#FECA43] py-4 text-[15px] font-black tracking-tight text-[#2E2A22] transition-all duration-150 hover:bg-[#f0b800] hover:-translate-y-[1px] active:scale-[0.98] cursor-pointer dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#f0b800]"
            >
              진단하기
              <ArrowRight className="size-4" strokeWidth={2.5} />
            </button>
            <button
              onClick={async () => {
                // 건너뛰기 시 기본 임의 유형 부여
                const saved = await saveLearningStyle({
                  processing: "active",
                  expression: "visual",
                  understanding: "global",
                });
                if (saved) {
                  router.push("/dashboard");
                }
              }}
              className="w-full py-2.5 text-[13px] font-medium text-[#876c45] transition-colors hover:text-gray-600 cursor-pointer dark:text-amber-400/40 dark:hover:text-amber-400/70"
            >
              건너뛰기
            </button>
          </div>

          {saveError && <p role="alert" className="mt-4 text-sm text-red-700 dark:text-red-300">{saveError}</p>}
          <p className="mt-5 text-center text-[11px] leading-relaxed text-[#826845] break-keep dark:text-amber-200/70">
            ※ Ducky의 진단 검사만으로는 실제 학습 유형을 알 수 없어요.
          </p>
        </div>
      </div>
    );
  }

  // ─── QUESTIONS ─────────────────────────────────────────────────────────────
  return (
    <div className="flex min-h-dvh w-full flex-col items-center justify-center overflow-y-auto bg-[#FFFCF3] px-4 py-6 transition-colors dark:bg-[#16130A] sm:px-6">
      <QuestionCard
        question={onboardingQuestions[currentIndex]}
        totalQuestions={onboardingQuestions.length}
        currentIndex={currentIndex}
        onSelect={handleSelect}
        isAnimating={isAnimating}
      />
    </div>
  );
}
