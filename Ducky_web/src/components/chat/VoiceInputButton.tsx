"use client";

import { Brain, Loader2, Mic, Square } from "lucide-react";
import { Button } from "@/components/ui/button";
import type { VoiceInputStatus } from "@/hooks/useVoiceInput";
import { cn } from "@/lib/utils";

interface VoiceInputButtonProps {
  disabled?: boolean;
  onCancel: () => void;
  onStart: () => void;
  status: VoiceInputStatus;
  statusLabel: string;
}

const statusIcon = {
  idle: Mic,
  listening: Mic,
  transcribing: Loader2,
  thinking: Brain,
} satisfies Record<VoiceInputStatus, typeof Mic>;

export function VoiceInputButton({
  disabled,
  onCancel,
  onStart,
  status,
  statusLabel,
}: VoiceInputButtonProps) {
  const isActive = status !== "idle";
  const Icon = isActive && status === "listening" ? Square : statusIcon[status];

  return (
    <Button
      type="button"
      onClick={isActive ? onCancel : onStart}
      disabled={disabled && !isActive}
      aria-label={isActive ? "음성 입력 취소" : "음성 입력 시작"}
      title={statusLabel}
      className={cn(
        "size-11 rounded-full border border-[#E7DDC8] bg-white text-[#2E2A22] shadow-sm hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-white dark:shadow-none dark:hover:bg-[#2A251D]",
        status === "idle" &&
          "bg-[#FECA43] text-[#2E2A22] hover:bg-[#F5B522] focus-visible:ring-[#FECA43]/50 dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#F5B522]",
        status === "listening" &&
          "border-[#FECA43] bg-[#FFF1BC] text-[#6B5200] shadow-[0_0_0_4px_rgba(254,202,67,0.22)] dark:border-[#FECA43] dark:bg-[#2A251D] dark:text-[#FECA43]",
      )}
      size="icon-lg"
    >
      <Icon
        className={cn(
          "size-5",
          status === "transcribing" && "animate-spin",
          status === "thinking" && "animate-pulse",
        )}
        aria-hidden="true"
      />
    </Button>
  );
}
