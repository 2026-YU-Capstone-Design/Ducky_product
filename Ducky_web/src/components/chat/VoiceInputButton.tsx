"use client";

import Image from "next/image";
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

function DuckyVoiceMark({
  icon: Icon,
  status,
}: {
  icon: typeof Mic;
  status: VoiceInputStatus;
}) {
  return (
    <span
      aria-hidden="true"
      className="relative flex size-10 items-center justify-center"
    >
      <Image
        src="/icons/ducky-character.png"
        alt=""
        width={44}
        height={44}
        className="size-11 max-w-none scale-125 object-contain drop-shadow-sm"
      />
      <span className="absolute -bottom-0.5 -right-0.5 flex size-4 items-center justify-center rounded-full border border-white bg-white text-[#2E2A22] shadow-sm dark:border-[#2A251D]">
        <Icon
          className={cn(
            "size-2.5",
            status === "transcribing" && "animate-spin",
            status === "thinking" && "animate-pulse",
          )}
        />
      </span>
    </span>
  );
}

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
      aria-label={isActive ? "음성 입력 취소" : "Ducky와 대화하기"}
      title={statusLabel}
      className={cn(
        "size-11 rounded-full border border-[#E7DDC8] bg-white p-0 text-[#2E2A22] shadow-sm hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-white dark:shadow-none dark:hover:bg-[#2A251D]",
        status === "idle" &&
          "bg-[#FECA43] text-[#2E2A22] hover:bg-[#F5B522] focus-visible:ring-[#FECA43]/50 dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#F5B522]",
        status === "listening" &&
          "border-[#FECA43] bg-[#FFF1BC] text-[#6B5200] shadow-[0_0_0_4px_rgba(254,202,67,0.22)] dark:border-[#FECA43] dark:bg-[#2A251D] dark:text-[#FECA43]",
      )}
      size="icon-lg"
    >
      <DuckyVoiceMark icon={Icon} status={status} />
    </Button>
  );
}
