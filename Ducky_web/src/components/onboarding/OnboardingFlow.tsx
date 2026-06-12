"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { onboardingQuestions } from "@/data/onboardingQuestions";
import { QuestionCard } from "./QuestionCard";
import { useLocalStorage } from "@/hooks/useLocalStorage";
import { Comfortaa } from "next/font/google";

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

  const [user, setUser] = useLocalStorage("ducky_user", {
    id: "",
    name: "",
    level: "beginner",
    learningStyle: {
      processing: "active",
      expression: "visual",
      understanding: "sequential"
    },
    onboarded: false
  });

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
        calculateAndSaveResult(newAnswers);
      }
    }, 300); // Wait for fade out animation
  };

  const calculateAndSaveResult = (finalAnswers: Record<string, number>) => {
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

    // Update user context
    if (user) {
      setUser({
        ...user,
        learningStyle: {
          processing: processingStyle === "Mixed" ? "active" : processingStyle.toLowerCase(),
          expression: representationStyle === "Mixed" ? "visual" : representationStyle.toLowerCase(),
          understanding: structureStyle === "Mixed" ? "sequential" : structureStyle.toLowerCase()
        },
        onboarded: true
      });
    }

    setShowResult(true);

    // Auto redirect to dashboard after showing results
    setTimeout(() => {
      router.push("/dashboard");
    }, 3500);
  };

  if (showResult) {
    return (
      <div className={`flex min-h-dvh w-full flex-col items-center justify-center bg-[#FECA43] px-4 py-8 text-white transition-all duration-700 sm:px-6 ${comfortaa.className}`}>
        <div className="flex w-full max-w-lg flex-col items-center animate-fade-in">
          <h1 className="text-4xl font-bold mb-8 tracking-tight">분석 완료!</h1>

          <div className="w-full max-w-md space-y-5 rounded-2xl border border-white/30 bg-white/20 p-5 text-center backdrop-blur-sm animate-slide-up sm:space-y-6 sm:p-8">
            <div>
              <p className="text-sm font-medium text-white/80 mb-1">정보 처리</p>
              <p className="text-2xl font-bold">{resultData?.processingStyle}</p>
            </div>
            <div className="h-px w-full bg-white/20"></div>
            <div>
              <p className="text-sm font-medium text-white/80 mb-1">이해 구조</p>
              <p className="text-2xl font-bold">{resultData?.structureStyle}</p>
            </div>
            <div className="h-px w-full bg-white/20"></div>
            <div>
              <p className="text-sm font-medium text-white/80 mb-1">정보 표현</p>
              <p className="text-2xl font-bold">{resultData?.representationStyle}</p>
            </div>
          </div>

          <p className="mt-10 text-center font-medium text-white/90 break-keep animate-pulse">
            당신에게 딱 맞는 학습을 준비하고 있어요...
          </p>
        </div>
      </div>
    );
  }

  if (step === "intro") {
    return (
      <div className="flex min-h-dvh w-full flex-col items-center bg-[#FAF8F5] px-4 sm:px-6">
        {/* 모바일 폭 컨테이너 - 데스크탑에서도 모바일처럼 좁게 표시 */}
        <div className="flex min-h-dvh w-full max-w-md flex-col py-8 sm:max-w-xl lg:max-w-2xl">

          {/* 상단 영역: Ducky 로고 */}
          <div className="flex flex-col items-center pb-6 pt-6 sm:pt-12">
            <h1 className={`text-[clamp(3rem,11vw,4.5rem)] font-bold text-[#FECA43] ${comfortaa.className} select-none tracking-tight`}>
              Ducky
            </h1>
          </div>

          {/* 중앙 콘텐츠 */}
          <div className="flex flex-1 flex-col items-center px-0 pt-6 sm:px-8 sm:pt-12">
            <h2 className="mb-6 text-center text-2xl font-bold text-gray-900 break-keep sm:mb-8 sm:text-3xl">
              학습 유형 검사
            </h2>

            <p className="mb-6 max-w-lg text-center text-[15px] leading-relaxed text-gray-700 break-keep sm:text-base">
              Ducky는 더 적절한 학습 방식을 제공하기 위해<br />
              학습 유형 검사를 근거로 유형을 구분해요.
            </p>

            <p className="max-w-lg text-center text-[15px] leading-relaxed text-gray-700 break-keep sm:text-base">
              학습 유형 검사는 총 {onboardingQuestions.length}문항으로,<br />
              자신에게 가장 적합하다고 생각되는 것만 체크하면 돼요.
            </p>
          </div>

          {/* 하단 버튼 영역 */}
          <div className="flex w-full flex-col items-center px-0 pb-2 sm:px-6 sm:pb-8">
            <p className="mb-6 text-center text-xs leading-relaxed text-gray-400 break-keep">
              ※ Ducky가 제공하는 진단 검사만으로는 실제 학습 유형을 알 수 없어요.
            </p>

            <button
              onClick={() => setStep("questions")}
              className="mb-5 min-h-14 w-full rounded-2xl bg-[#FECA43] px-4 py-3 text-xl font-bold leading-tight text-white shadow-sm transition-all hover:bg-[#F5B522] active:scale-[0.98]"
            >
              진단하기
            </button>

            <button
              onClick={() => {
                // 건너뛰기 시 기본 임의 유형 부여
                if (user) {
                  setUser({
                    ...user,
                    learningStyle: {
                      processing: "active",
                      expression: "visual",
                      understanding: "global"
                    },
                    onboarded: true
                  });
                }
                router.push("/dashboard");
              }}
              className="text-gray-500 text-base font-medium underline underline-offset-2 hover:text-gray-700 transition-colors"
            >
              건너뛰기
            </button>
          </div>

        </div>
      </div>
    );
  }

  const currentQuestion = onboardingQuestions[currentIndex];

  return (
    <div className="flex min-h-dvh w-full flex-col items-center justify-center overflow-y-auto bg-[#FAF8F5] px-4 py-6 sm:px-6 lg:px-8">
      <QuestionCard
        question={currentQuestion}
        totalQuestions={onboardingQuestions.length}
        currentIndex={currentIndex}
        onSelect={handleSelect}
        isAnimating={isAnimating}
      />
    </div>
  );
}
