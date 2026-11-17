import Image from "next/image";
import { FileText } from "lucide-react";
import type { ChatMessage } from "@/types/chat";
import { cn } from "@/lib/utils";

interface ChatBubbleProps {
  message: ChatMessage;
}

function messageLabel(message: ChatMessage) {
  if (message.role === "user") return "나";
  if (message.type === "hint") {
    return `힌트 ${message.hintNumber ?? message.hintLevel}`;
  }
  return "Ducky";
}

export function ChatBubble({ message }: ChatBubbleProps) {
  const isUser = message.role === "user";
  const isHint = message.type === "hint";

  if (message.role === "system") {
    return (
      <div className="flex justify-center">
        <p className="max-w-[min(28rem,90%)] rounded-lg bg-[#ECE7DC] px-3 py-2 text-center text-xs font-medium text-gray-600 break-keep transition-colors dark:bg-[#2A251D] dark:text-gray-300">
          {message.content}
        </p>
      </div>
    );
  }

  return (
    <div
      className={cn(
        "flex w-full items-end gap-2",
        isUser ? "justify-end" : "justify-start",
      )}
    >
      {!isUser && (
        <div className="relative size-8 shrink-0 overflow-hidden rounded-full border border-[#E7DDC8] bg-white transition-colors dark:border-white/10 dark:bg-[#24211D]">
          <Image
            src="/images/splash7.png"
            alt="Ducky"
            width={32}
            height={32}
            className="size-full object-cover"
          />
        </div>
      )}

      <div
        className={cn(
          "flex max-w-[86%] flex-col gap-1 sm:max-w-[74%] xl:max-w-[64%]",
          isUser && "items-end",
        )}
      >
        <p
          className={cn(
            "px-1 text-xs font-semibold",
            isUser ? "text-[#6B5200]" : "text-gray-500 dark:text-gray-400",
            isHint && "text-[#B88700]",
          )}
        >
          {messageLabel(message)}
        </p>

        <div
          className={cn(
            "rounded-lg px-4 py-3 text-sm leading-relaxed shadow-sm break-keep break-words whitespace-pre-wrap",
            isUser
              ? "bg-[#FECA43] text-[#2E2A22]"
              : "border border-[#E7DDC8] bg-white text-gray-800 dark:border-white/10 dark:bg-[#24211D] dark:text-gray-100 dark:shadow-none",
            isHint &&
              "border-[#FECA43] bg-[#FFF7E0] dark:border-[#FECA43]/70 dark:bg-[#2A251D] dark:text-gray-100",
          )}
        >
          {message.content}

          {!!message.attachments?.length && (
            <div className="mt-3 space-y-2">
              {message.attachments.map((attachment) => (
                <div
                  key={attachment.id}
                  className={cn(
                    "flex min-w-0 items-center gap-2 rounded-lg px-3 py-2 text-xs",
                    isUser
                      ? "bg-white/50"
                      : "bg-[#FAF8F5] dark:bg-[#1D1B18]",
                  )}
                >
                  <FileText className="size-4 shrink-0" aria-hidden="true" />
                  <span className="min-w-0 flex-1 truncate">
                    {attachment.name}
                  </span>
                  <span className="shrink-0 text-gray-500 dark:text-gray-400">
                    {attachment.sizeLabel}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
