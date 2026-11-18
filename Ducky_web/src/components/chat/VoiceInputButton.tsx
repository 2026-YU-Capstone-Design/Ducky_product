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
    <div className="relative shrink-0">
      {status === "listening" && (
        <span
          aria-hidden="true"
          className="absolute inset-0 motion-safe:animate-ping rounded-full bg-[#FECA43]/50"
        />
      )}
      <Button
        type="button"
        onClick={isActive ? onCancel : onStart}
        disabled={disabled && !isActive}
        aria-label={isActive ? "음성 입력 취소" : "Ducky와 대화하기"}
        title={statusLabel}
        className={cn(
          "relative size-9 rounded-full border border-[#F0E0A8] bg-white text-[#3D2E0A] hover:bg-[#FFF6DC] dark:border-[#5C4413]/40 dark:bg-[#2A2310] dark:text-[#F0E0C4] dark:hover:bg-[#3A2F12]",
          status === "listening" &&
            "border-[#FECA43] bg-[#FFE9A0] text-[#5C4413] dark:border-[#FECA43] dark:bg-[#46350F] dark:text-[#FECA43]",
        )}
        size="icon"
      >
        <Icon
          className={cn(
            "size-4",
            status === "transcribing" && "animate-spin",
            status === "thinking" && "animate-pulse",
          )}
          aria-hidden="true"
        />
      </Button>
    </div>
  );
}
