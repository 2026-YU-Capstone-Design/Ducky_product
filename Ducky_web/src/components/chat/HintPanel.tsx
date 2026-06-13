"use client";

import { Check, Lightbulb, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { HintRecord } from "@/types/chat";

interface HintPanelProps {
  activeTitle: string;
  activeTopic: string;
  className?: string;
  hintCount: number;
  hintHistory: HintRecord[];
  isCompleted: boolean;
  isThinking: boolean;
  onComplete: () => void;
  onRequestHint: () => void;
  showHeader?: boolean;
}

export function HintPanel({
  activeTitle,
  activeTopic,
  className,
  hintCount,
  hintHistory,
  isCompleted,
  isThinking,
  onComplete,
  onRequestHint,
  showHeader = true,
}: HintPanelProps) {
  return (
    <div
      className={cn(
        "flex h-full min-h-0 flex-col rounded-lg border border-[#E7DDC8] bg-white text-[#2E2A22] shadow-sm transition-colors dark:border-white/10 dark:bg-[#201D19] dark:text-white dark:shadow-none",
        className,
      )}
    >
      {showHeader && (
        <div className="border-b border-[#E7DDC8] px-5 py-4 dark:border-white/10">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold text-[#B88700]">Ducky</p>
              <h2 className="mt-1 text-lg font-bold">힌트 패널</h2>
            </div>
            <Badge className="bg-[#FFF1BC] text-[#6B5200] dark:bg-[#2A251D] dark:text-[#FECA43]">
              힌트 {hintCount}개
            </Badge>
          </div>
        </div>
      )}

      <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-5 py-5">
        <section>
          <div className="flex items-start gap-3 rounded-lg bg-[#FAF8F5] p-4 transition-colors dark:bg-[#24211D]">
            <Sparkles className="mt-0.5 size-4 shrink-0 text-[#B88700]" />
            <div className="min-w-0">
              <p className="text-xs font-semibold text-[#B88700]">
                {activeTopic}
              </p>
              <p className="mt-1 text-sm font-bold leading-snug break-keep">
                {activeTitle}
              </p>
              <p className="mt-2 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-400">
                현재 대화 흐름을 기준으로, 막힌 지점에 필요한 단서만
                짧게 이어갑니다.
              </p>
            </div>
          </div>
        </section>

        <section>
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-bold">누적 힌트</h3>
            <span className="text-xs font-semibold text-gray-500 dark:text-gray-400">
              힌트 {hintCount}개
            </span>
          </div>

          <Button
            type="button"
            onClick={onRequestHint}
            disabled={isCompleted || isThinking}
            className="mt-3 h-10 w-full gap-2 bg-[#FECA43] font-bold text-[#2E2A22] hover:bg-[#F5B522]"
          >
            <Lightbulb className="size-4" aria-hidden="true" />
            {isCompleted ? "세션 완료" : "힌트 더 받기"}
          </Button>

          <Button
            type="button"
            onClick={onComplete}
            disabled={isCompleted || isThinking}
            variant="outline"
            className="mt-2 h-10 w-full gap-2 border-[#E7DDC8] bg-white font-bold text-[#2E2A22] hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-white dark:hover:bg-[#2A251D]"
          >
            <Check className="size-4" aria-hidden="true" />
            답을 알겠어요
          </Button>

          <div className="mt-4 space-y-3">
            {hintHistory.length === 0 ? (
              <p className="rounded-lg border border-dashed border-[#E7DDC8] px-3 py-4 text-sm leading-relaxed text-gray-500 break-keep dark:border-white/10 dark:text-gray-400">
                막히는 지점에서 힌트를 계속 요청할 수 있습니다. 답을 스스로
                설명할 수 있을 때 &quot;답을 알겠어요&quot;로 마무리합니다.
              </p>
            ) : (
              hintHistory.map((hint) => (
                <article
                  key={hint.id}
                  className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-3 transition-colors dark:border-white/10 dark:bg-[#24211D]"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-xs font-semibold text-[#B88700]">
                      힌트 {hint.number}
                    </p>
                  </div>
                  <p className="mt-2 text-sm leading-relaxed text-gray-700 break-keep dark:text-gray-300">
                    {hint.content}
                  </p>
                </article>
              ))
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
