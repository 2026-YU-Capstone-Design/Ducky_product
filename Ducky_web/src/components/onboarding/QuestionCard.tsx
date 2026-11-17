import { OnboardingQuestion } from "@/types/onboarding";

interface Props {
  question: OnboardingQuestion;
  totalQuestions: number;
  currentIndex: number;
  onSelect: (axis: string, value: string) => void;
  isAnimating: boolean;
}

export function QuestionCard({ question, totalQuestions, currentIndex, onSelect, isAnimating }: Props) {
  const progress = ((currentIndex + 1) / totalQuestions) * 100;

  return (
    <div className={`mx-auto flex min-h-[calc(100dvh-3rem)] w-full max-w-md flex-col p-4 transition-all duration-300 sm:max-w-xl sm:p-6 lg:max-w-2xl lg:p-8 ${isAnimating ? "opacity-0 -translate-x-4" : "opacity-100 translate-x-0"}`}>
      {/* Progress */}
      <div className="mb-8 mt-0 w-full sm:mb-10 sm:mt-4">
        <div className="mb-3 flex items-center justify-between gap-4">
          <span className="text-sm font-medium text-gray-400">학습 성향 분석 중...</span>
          <span className="text-sm font-bold text-[#FECA43]">{currentIndex + 1} / {totalQuestions}</span>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-2">
          <div 
            className="bg-[#FECA43] h-2 rounded-full transition-all duration-500 ease-out" 
            style={{ width: `${progress}%` }}
          ></div>
        </div>
      </div>

      {/* Question */}
      <div className="mb-8 flex flex-1 flex-col items-center justify-center sm:mb-12">
        <h2 className="text-center text-xl font-bold leading-relaxed text-gray-800 break-keep sm:text-2xl lg:text-3xl">
          {question.text}
        </h2>
      </div>

      {/* Options */}
      <div className="mt-auto flex flex-col gap-3 pb-2 sm:gap-4 sm:pb-8">
        <button
          onClick={() => onSelect(question.axis, question.optionA.value)}
          disabled={isAnimating}
          className="group w-full rounded-xl border-2 border-transparent bg-white p-4 text-left shadow-sm transition-all duration-200 hover:border-[#FECA43] hover:shadow-md active:scale-95 disabled:opacity-50 sm:rounded-2xl sm:p-6"
        >
          <div className="flex items-start sm:items-center">
            <div className="mr-3 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gray-100 font-bold text-gray-500 transition-colors group-hover:bg-[#FECA43] group-hover:text-white sm:mr-4">A</div>
            <span className="min-w-0 text-base font-medium text-gray-700 break-keep sm:text-lg">{question.optionA.text}</span>
          </div>
        </button>

        <button
          onClick={() => onSelect(question.axis, question.optionB.value)}
          disabled={isAnimating}
          className="group w-full rounded-xl border-2 border-transparent bg-white p-4 text-left shadow-sm transition-all duration-200 hover:border-[#FECA43] hover:shadow-md active:scale-95 disabled:opacity-50 sm:rounded-2xl sm:p-6"
        >
          <div className="flex items-start sm:items-center">
            <div className="mr-3 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gray-100 font-bold text-gray-500 transition-colors group-hover:bg-[#FECA43] group-hover:text-white sm:mr-4">B</div>
            <span className="min-w-0 text-base font-medium text-gray-700 break-keep sm:text-lg">{question.optionB.text}</span>
          </div>
        </button>
      </div>
    </div>
  );
}
