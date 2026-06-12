"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Bot, Lightbulb } from "lucide-react";
import { Comfortaa } from "next/font/google";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useMockChat } from "@/hooks/useMockChat";
import { useVoiceInput } from "@/hooks/useVoiceInput";
import { ChatBubble } from "./ChatBubble";
import { ChatInput } from "./ChatInput";
import { HintPanel } from "./HintPanel";

const comfortaa = Comfortaa({
  subsets: ["latin"],
  weight: ["700"],
});

export function ChatPage() {
  const [draft, setDraft] = useState("");
  const [isHintOpen, setIsHintOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const {
    activeSession,
    completeSession,
    hintCount,
    hintHistory,
    isCompleted,
    isThinking,
    messages,
    requestHint,
    sendMessage,
    stageIndex,
  } = useMockChat();

  const handleTranscript = useCallback((text: string) => {
    setDraft((current) => (current.trim() ? `${current}\n${text}` : text));
  }, []);

  const voice = useVoiceInput({
    onTranscript: handleTranscript,
  });

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isThinking]);

  const hintPanel = (
    <HintPanel
      activeTitle={activeSession.title}
      activeTopic={activeSession.topic}
      hintCount={hintCount}
      hintHistory={hintHistory}
      isCompleted={isCompleted}
      isThinking={isThinking}
      onComplete={completeSession}
      onRequestHint={requestHint}
      stageIndex={stageIndex}
    />
  );

  return (
    <div className="flex h-[calc(100dvh-5.75rem)] min-h-0 w-full bg-[#FAF8F5] transition-colors dark:bg-[#171512] md:h-dvh">
      <div className="flex min-h-0 w-full gap-4 p-0 sm:p-4 lg:p-6">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#FAF8F5] transition-colors dark:bg-[#171512] sm:rounded-lg sm:border sm:border-[#E7DDC8] sm:bg-white sm:shadow-sm sm:dark:border-white/10 sm:dark:bg-[#201D19] sm:dark:shadow-none">
          <header className="flex shrink-0 items-center justify-between gap-3 border-b border-[#E7DDC8] bg-[#FAF8F5] px-4 py-3 transition-colors dark:border-white/10 dark:bg-[#201D19] sm:bg-white lg:px-5">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-[#FECA43] text-[#2E2A22]">
                <Bot className="size-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h1
                  className={`truncate text-xl font-bold text-[#FECA43] ${comfortaa.className}`}
                >
                  Ducky
                </h1>
                <p className="truncate text-xs text-gray-500 dark:text-gray-400">
                  {activeSession.topic} 질문 훈련
                </p>
              </div>
            </div>

            <Button
              type="button"
              onClick={() => setIsHintOpen(true)}
              className="h-9 gap-2 bg-[#FECA43] px-3 font-bold text-[#2E2A22] hover:bg-[#F5B522] lg:hidden"
            >
              <Lightbulb className="size-4" aria-hidden="true" />
              힌트
            </Button>
          </header>

          <ScrollArea className="min-h-0 flex-1">
            <div className="space-y-4 px-3 py-5 sm:px-5 lg:px-8">
              <div className="mx-auto max-w-3xl rounded-lg border border-[#E7DDC8] bg-[#FFF7E0] px-4 py-3 text-sm leading-relaxed text-[#4A4438] break-keep transition-colors dark:border-[#6B5A32] dark:bg-[#2A251D] dark:text-gray-300">
                <p className="font-bold text-[#6B5200] dark:text-[#FECA43]">
                  오늘의 대화 목표
                </p>
                <p className="mt-1">
                  정답을 바로 받기보다, 개념을 자신의 말로 설명하도록 질문과
                  힌트를 필요한 만큼 사용합니다.
                </p>
              </div>

              {messages.map((message) => (
                <ChatBubble key={message.id} message={message} />
              ))}

              {isThinking && (
                <div className="flex items-end gap-2">
                  <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-[#E7DDC8] bg-white text-[#B88700] transition-colors dark:border-white/10 dark:bg-[#24211D]">
                    <Bot className="size-4" aria-hidden="true" />
                  </div>
                  <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300 dark:shadow-none">
                    Ducky가 다음 질문을 고르는 중
                    <span className="ml-1 inline-flex w-6 animate-pulse">
                      ...
                    </span>
                  </div>
                </div>
              )}

              <div ref={bottomRef} />
            </div>
          </ScrollArea>

          <ChatInput
            disabled={isThinking || isCompleted}
            onChange={setDraft}
            onSend={sendMessage}
            onVoiceCancel={voice.cancel}
            onVoiceStart={voice.start}
            value={draft}
            voiceStatus={voice.status}
            voiceStatusLabel={voice.statusLabel}
          />
        </section>

        <aside className="hidden min-h-0 w-88 shrink-0 lg:block">
          {hintPanel}
        </aside>
      </div>

      <Sheet open={isHintOpen} onOpenChange={setIsHintOpen}>
        <SheetContent
          side="bottom"
          className="max-h-[84dvh] rounded-t-lg bg-[#FAF8F5] p-0 dark:bg-[#171512]"
        >
          <SheetHeader className="border-b border-[#E7DDC8] bg-white p-4 pr-12 dark:border-white/10 dark:bg-[#201D19]">
            <SheetTitle className="text-lg font-bold dark:text-white">
              힌트 패널
            </SheetTitle>
            <SheetDescription className="dark:text-gray-400">
              막히는 지점에서 단서를 계속 요청하고, 알겠으면 완료하세요.
            </SheetDescription>
          </SheetHeader>
          <div className="max-h-[calc(84dvh-5rem)] overflow-y-auto p-4">
            <HintPanel
              activeTitle={activeSession.title}
              activeTopic={activeSession.topic}
              className="border-0 shadow-none"
              hintCount={hintCount}
              hintHistory={hintHistory}
              isCompleted={isCompleted}
              isThinking={isThinking}
              onComplete={completeSession}
              onRequestHint={requestHint}
              showHeader={false}
              stageIndex={stageIndex}
            />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
