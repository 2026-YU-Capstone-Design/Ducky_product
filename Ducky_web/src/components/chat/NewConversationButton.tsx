"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { Loader2, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { startConversation } from "@/lib/api/chat";

export function NewConversationButton({ disabled }: { disabled?: boolean }) {
  const router = useRouter();
  const requestRef = useRef<AbortController | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const [isNavigating, startTransition] = useTransition();
  const [hasError, setHasError] = useState(false);
  const isPending = isCreating || isNavigating;

  useEffect(() => () => requestRef.current?.abort(), []);

  async function createConversation() {
    if (disabled || requestRef.current || isNavigating) return;

    const request = new AbortController();
    requestRef.current = request;
    setIsCreating(true);
    setHasError(false);

    try {
      const { id } = await startConversation({
        title: "새 대화",
        topic: "러버덕 질문 훈련",
      }, request.signal);
      if (!request.signal.aborted) {
        startTransition(() => router.push(`/chat?conversationId=${encodeURIComponent(id)}`));
      }
    } catch {
      if (!request.signal.aborted) setHasError(true);
    } finally {
      requestRef.current = null;
      if (!request.signal.aborted) setIsCreating(false);
    }
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <Button
        type="button"
        disabled={disabled || isPending}
        aria-busy={isPending}
        onClick={() => void createConversation()}
        className="h-9 gap-1.5 rounded-full bg-[#FECA43] px-2.5 font-bold text-[#2E2A22] hover:bg-[#F5B522] sm:px-3"
      >
        {isPending ? (
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
        ) : (
          <Plus className="size-4" aria-hidden="true" />
        )}
        새 대화
      </Button>
      {hasError && (
        <p role="alert" className="max-w-28 text-xs text-red-700 break-keep sm:max-w-48 dark:text-red-200">
          새 대화를 만들지 못했어요. 다시 시도해 주세요.
        </p>
      )}
    </div>
  );
}
