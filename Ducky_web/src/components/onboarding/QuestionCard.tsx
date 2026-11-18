import { OnboardingQuestion } from "@/types/onboarding";

interface Props {
  question: OnboardingQuestion;
  totalQuestions: number;
  currentIndex: number;
  onSelect: (axis: string, value: string) => void;
  isAnimating: boolean;
}

export function QuestionCard({
  question,
  totalQuestions,
  currentIndex,
  onSelect,
  isAnimating,
}: Props) {
  const progress = ((currentIndex + 1) / totalQuestions) * 100;
  const options = [
    { label: "A", text: question.optionA.text, value: question.optionA.value },
    { label: "B", text: question.optionB.text, value: question.optionB.value },
  ];

  return (
    <div
      className={`mx-auto flex min-h-[calc(100dvh-3rem)] w-full max-w-md flex-col px-5 pt-6 pb-8 transition-all duration-300 ease-out sm:max-w-xl sm:px-6 ${
        isAnimating ? "opacity-0 translate-x-4" : "opacity-100 translate-x-0"
      }`}
    >
      <div className="mt-2">
        <div className="mb-2.5 flex items-center justify-between">
          <p className="text-[11px] font-semibold uppercase tracking-[0.2em] text-[#876c45] dark:text-white/60">
            학습 성향 분석 중...
          </p>
          <p className="text-[11px] text-[#876c45] dark:text-white/60">
            <span className="font-bold text-gray-950 dark:text-white">
              {currentIndex + 1}
            </span>
            <span className="font-normal"> / {totalQuestions}</span>
          </p>
        </div>
        <div className="relative h-1 w-full overflow-hidden rounded-full bg-amber-100 dark:bg-white/10">
          <div
            className="absolute left-0 top-0 h-full rounded-full bg-amber-400 transition-all duration-500 ease-out"
            style={{ width: `${progress}%` }}
          />
        </div>
      </div>

      <div className="flex flex-1 flex-col justify-center">
        <span className="-ml-1.5 mb-6 inline-flex w-fit items-center rounded-full bg-amber-100 px-5 py-2 text-md font-bold uppercase tracking-[0.2em] text-amber-700 dark:bg-amber-900/40 dark:text-amber-300">
          Q{String(currentIndex + 1).padStart(2, "0")}
        </span>

        <h2 className="text-[1.6rem] font-bold leading-[1.45] text-gray-950 break-keep dark:text-white sm:text-3xl">
          {question.text}
        </h2>

        <div className="mt-7 flex items-center gap-2">
          <div className="h-[3px] w-16 rounded-full bg-amber-400" />
          <div className="h-[3px] w-3 rounded-full bg-amber-400/30" />
        </div>
      </div>

      <div className="flex flex-col gap-5 pb-10 sm:gap-5 sm:pb-10">
        {options.map((opt) => (
          <button
            key={opt.label}
            onClick={() => onSelect(question.axis, opt.value)}
            disabled={isAnimating}
            className="group relative w-full overflow-hidden rounded-2xl border border-amber-100/80 bg-white px-5 py-7 text-left shadow-[0_1px_2px_rgba(0,0,0,0.04)] transition-all duration-200 ease-out hover:border-amber-300 hover:shadow-[0_4px_16px_-4px_rgba(217,119,6,0.2)] hover:-translate-y-0.5 active:scale-[0.98] active:translate-y-0 disabled:opacity-50 dark:border-white/8 dark:bg-white/[0.06] dark:hover:border-amber-400/40 dark:hover:bg-white/[0.09]"
          >
            <div className="absolute inset-0 bg-gradient-to-r from-amber-50/60 via-amber-50/20 to-transparent opacity-0 transition-opacity duration-200 group-hover:opacity-100 dark:from-amber-400/5 dark:via-transparent" />

            <div className="relative flex items-center gap-4">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-amber-50 text-xs font-bold text-amber-400 transition-all duration-200 group-hover:bg-amber-400 group-hover:text-white group-hover:shadow-md group-hover:shadow-amber-400/30 dark:bg-white/10 dark:text-white/40 dark:group-hover:bg-amber-400 dark:group-hover:text-white">
                {opt.label}
              </span>
              <span className="min-w-0 flex-1 text-md font-medium leading-snug text-gray-700 break-keep dark:text-white/70">
                {opt.text}
              </span>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
