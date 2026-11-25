import Image from "next/image";
import { FileText } from "lucide-react";
import type { ChatMessage } from "@/types/chat";
import { cn } from "@/lib/utils";

interface ChatBubbleProps {
  message: ChatMessage;
}

export function ChatBubble({ message }: ChatBubbleProps) {
  const isUser = message.role === "user";
  const isHint = message.type === "hint";

  if (message.role === "system") {
    return (
      <div className="flex justify-center">
        <p className="max-w-[min(28rem,90%)] rounded-full bg-[#F0E0A8]/60 px-3 py-1.5 text-center text-xs font-semibold text-[#5C4413] break-keep transition-colors dark:bg-[#3A2F12] dark:text-[#E8C97A]">
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
        <div className="relative size-[30px] shrink-0 overflow-hidden rounded-full bg-[#FECA43]">
          <Image
            src="/images/splash7.png"
            alt="Ducky"
            width={30}
            height={30}
            className="size-full object-cover"
          />
        </div>
      )}

      <div
        className={cn(
          "flex max-w-[86%] flex-col gap-[3px] sm:max-w-[74%] xl:max-w-[64%]",
          isUser && "items-end",
        )}
      >
        {isHint && (
          <p className="px-1.5 text-xs font-bold text-[#946200] dark:text-[#FECA43]">
            힌트 {message.hintNumber ?? message.hintLevel}
          </p>
        )}

        <div
          className={cn(
            "rounded-[14px] px-[15px] py-[11px] text-[13.5px] leading-relaxed [overflow-wrap:anywhere] break-words whitespace-pre-wrap",
            isUser
              ? "rounded-br-[4px] border border-[#F0E0A8] bg-[#FFF9E8] text-[#3D2E0A] dark:border-white/10 dark:bg-[#2A2310] dark:text-[#F0E0C4]"
              : "rounded-bl-[4px] border border-[#F0E0A8] bg-white text-[#3D2E0A] dark:border-white/10 dark:bg-[#211C12] dark:text-[#F0E0C4]",
            isHint &&
              "border-2 border-[#FECA43] bg-[#FFE9A0] dark:border-[#FECA43] dark:bg-[#46350F]",
          )}
        >
          {message.content}

          {message.responseMode === "extractive" && (
            <p className="mt-3 text-xs text-gray-500">문서 발췌 모드 · 외부 AI 미사용</p>
          )}
          {!!message.sources?.length && (
            <details className="mt-3 border-t border-[#F0E0A8] pt-2 text-xs">
              <summary className="cursor-pointer font-semibold">참고 문서 {message.sources.length}건</summary>
              <ul className="mt-2 space-y-3">
                {message.sources.map((source) => (
                  <li key={`${source.documentId}-${source.chunkId}`}>
                    <p className="font-semibold">{source.title}{source.headingPath ? ` / ${source.headingPath}` : ""}</p>
                    <p className="mt-1 text-gray-500">
                      문서 {source.documentId} · {source.pageNumber != null && source.pageNumber > 0
                        ? `PDF ${source.pageNumber}쪽`
                        : "쪽 정보 없음"}
                    </p>
                    <p className="mt-1 whitespace-pre-wrap">{source.content}</p>
                  </li>
                ))}
              </ul>
            </details>
          )}

          {!!message.attachments?.length && (
            <div className="mt-3 space-y-2">
              {message.attachments.map((attachment) => (
                <div
                  key={attachment.id}
                  className={cn(
                    "flex min-w-0 items-center gap-2 rounded-lg px-3 py-2 text-xs",
                    isUser
                      ? "bg-[#F0E4C8] text-[#5C4413] dark:bg-[#3A2F12] dark:text-[#F0E0C4]"
                      : "bg-[#FFFCF3] text-[#5C4413] dark:bg-[#1F1A09] dark:text-[#F0E0C4]",
                  )}
                >
                  <FileText
                    className="size-4 shrink-0 text-[#946200] dark:text-[#E8C97A]"
                    aria-hidden="true"
                  />
                  <span className="min-w-0 flex-1 truncate">
                    {attachment.name}
                  </span>
                  <span className="shrink-0 text-[#8A6A28] dark:text-[#C4A85A]">
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
