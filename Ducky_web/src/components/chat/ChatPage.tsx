"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AlertCircle, Bot, Lightbulb, Loader2, Mic, RefreshCw } from "lucide-react";
import { Comfortaa } from "next/font/google";
import Image from "next/image";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useChatSession } from "@/hooks/useChatSession";
import { useVoiceInput } from "@/hooks/useVoiceInput";
import {
  DEFAULT_RASPBERRY_SERIAL,
  getLatestDeviceCommand,
  listDevices,
  startRaspberryRecording,
  type DeviceResponse,
} from "@/lib/api/devices";
import { ChatBubble } from "./ChatBubble";
import { ChatInput } from "./ChatInput";
import { HintPanel } from "./HintPanel";

const comfortaa = Comfortaa({
  subsets: ["latin"],
  weight: ["700"],
});

function getRaspberryErrorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message;
  }

  return "라즈베리 명령을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}

export function ChatPage() {
  const [draft, setDraft] = useState("");
  const [isHintOpen, setIsHintOpen] = useState(false);
  const [raspberryDeviceId, setRaspberryDeviceId] = useState<string | null>(null);
  const [raspberryDeviceStatus, setRaspberryDeviceStatus] = useState<string | null>(null);
  const [raspberryError, setRaspberryError] = useState<string | null>(null);
  const [isRaspberryCommandPending, setIsRaspberryCommandPending] = useState(false);
  const [isStartingRaspberry, setIsStartingRaspberry] = useState(false);
  const lastRaspberryCommandStatusRef = useRef<string | null>(null);
  const previousRaspberryStatusRef = useRef<string | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const chat = useChatSession();
  const {
    activeSession,
    completeSession,
    error,
    hintCount,
    hintHistory,
    isCompleted,
    isLoadingSession,
    isReady,
    isThinking,
    messages,
    refreshMessages,
    requestHint,
    retry,
    sendMessage,
  } = chat;

  const handleTranscript = useCallback((text: string) => {
    setDraft((current) => (current.trim() ? `${current}\n${text}` : text));
  }, []);

  const voice = useVoiceInput({
    onTranscript: handleTranscript,
  });
  const isRaspberryBusy =
    isStartingRaspberry ||
    isRaspberryCommandPending ||
    raspberryDeviceStatus === "RECORDING" ||
    raspberryDeviceStatus === "TRANSCRIBING" ||
    raspberryDeviceStatus === "THINKING" ||
    raspberryDeviceStatus === "SPEAKING" ||
    raspberryDeviceStatus === "LOGGING";
  const raspberryStatusLabel = !raspberryDeviceId
    ? "라즈베리 없음"
    : raspberryDeviceStatus === "OFFLINE"
      ? "오프라인"
      : raspberryDeviceStatus === "RECORDING" || raspberryDeviceStatus === "TRANSCRIBING"
        ? "듣는 중"
        : raspberryDeviceStatus === "THINKING" ||
            raspberryDeviceStatus === "SPEAKING" ||
            raspberryDeviceStatus === "LOGGING"
          ? "응답 중"
          : "대기 중";
  const raspberryButtonLabel = !raspberryDeviceId
    ? "라즈베리 없음"
    : isRaspberryBusy
      ? raspberryStatusLabel
      : "덕키와 대화하기";
  const isRaspberryButtonDisabled =
    !raspberryDeviceId ||
    isRaspberryBusy ||
    isThinking ||
    isCompleted ||
    isLoadingSession ||
    !isReady;

  const handleRaspberryTalk = useCallback(async () => {
    if (!raspberryDeviceId || !activeSession.id || isRaspberryButtonDisabled) {
      return;
    }

    setRaspberryError(null);
    setIsStartingRaspberry(true);

    try {
      await startRaspberryRecording(raspberryDeviceId, activeSession.id);
      setIsRaspberryCommandPending(true);
    } catch (startError) {
      setRaspberryError(getRaspberryErrorMessage(startError));
      setIsStartingRaspberry(false);
      setIsRaspberryCommandPending(false);
    }
  }, [activeSession.id, isRaspberryButtonDisabled, raspberryDeviceId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, isThinking]);

  useEffect(() => {
    if (!isReady) {
      return;
    }

    let cancelled = false;

    async function loadRaspberryDevice() {
      try {
        const devices = await listDevices();
        if (cancelled) return;

        const raspberry = devices.find(
          (device) => device.serialNumber === DEFAULT_RASPBERRY_SERIAL,
        );
        setRaspberryDeviceId(raspberry?.id ?? null);
        setRaspberryDeviceStatus(raspberry?.status ?? null);

        if (!raspberry) {
          return;
        }
      } catch (deviceError) {
        if (!cancelled) {
          setRaspberryError(getRaspberryErrorMessage(deviceError));
        }
      }
    }

    void loadRaspberryDevice();

    return () => {
      cancelled = true;
    };
  }, [isReady]);

  useEffect(() => {
    if (!raspberryDeviceId) {
      return;
    }

    const timer = window.setInterval(() => {
      void listDevices()
        .then((devices) => {
          const raspberry = devices.find(
            (device: DeviceResponse) => device.id === raspberryDeviceId,
          );
          setRaspberryDeviceStatus(raspberry?.status ?? null);
          setRaspberryError(null);
        })
        .catch((pollError) => {
          setRaspberryError(getRaspberryErrorMessage(pollError));
        });
    }, 1500);

    return () => window.clearInterval(timer);
  }, [raspberryDeviceId]);

  useEffect(() => {
    if (!raspberryDeviceId || !activeSession.id) {
      return;
    }

    const pollCommand = () => {
      void getLatestDeviceCommand(raspberryDeviceId)
        .then(async (command) => {
          if (!command) {
            return;
          }

          const isActive =
            command.status === "PENDING" || command.status === "CLAIMED";
          setIsRaspberryCommandPending(isActive);

          if (isActive) {
            setIsStartingRaspberry(false);
          }

          if (
            command.status === "COMPLETED" &&
            lastRaspberryCommandStatusRef.current !== "COMPLETED"
          ) {
            await refreshMessages();
            setIsStartingRaspberry(false);
            setIsRaspberryCommandPending(false);
          }

          if (command.status === "FAILED") {
            setRaspberryError(
              command.errorMessage ??
                "라즈베리 녹음 명령을 처리하지 못했습니다.",
            );
            setIsStartingRaspberry(false);
            setIsRaspberryCommandPending(false);
          }

          lastRaspberryCommandStatusRef.current = command.status;
        })
        .catch((pollError) => {
          setRaspberryError(getRaspberryErrorMessage(pollError));
          setIsStartingRaspberry(false);
          setIsRaspberryCommandPending(false);
        });
    };

    pollCommand();
    const timer = window.setInterval(pollCommand, 1500);

    return () => window.clearInterval(timer);
  }, [activeSession.id, raspberryDeviceId, refreshMessages]);

  useEffect(() => {
    const previousStatus = previousRaspberryStatusRef.current;
    if (
      previousStatus &&
      previousStatus !== "IDLE" &&
      raspberryDeviceStatus === "IDLE"
    ) {
      void refreshMessages();
      setIsStartingRaspberry(false);
      setIsRaspberryCommandPending(false);
    }

    previousRaspberryStatusRef.current = raspberryDeviceStatus;
  }, [raspberryDeviceStatus, refreshMessages]);

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
    />
  );

  return (
    <div className="flex h-[calc(100dvh-5.75rem)] min-h-0 w-full bg-[#FAF8F5] transition-colors dark:bg-[#171512] md:h-dvh">
      <div className="flex min-h-0 w-full gap-4 p-0 sm:p-4 lg:p-6">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#FAF8F5] transition-colors dark:bg-[#171512] sm:rounded-lg sm:border sm:border-[#E7DDC8] sm:bg-white sm:shadow-sm sm:dark:border-white/10 sm:dark:bg-[#201D19] sm:dark:shadow-none">
          <header className="flex shrink-0 items-center justify-between gap-3 border-b border-[#E7DDC8] bg-[#FAF8F5] px-4 py-3 transition-colors dark:border-white/10 dark:bg-[#201D19] sm:bg-white lg:px-5">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex size-10 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-[#FECA43]">
                <Image
                  src="/icons/ducky-character.png"
                  alt=""
                  width={40}
                  height={40}
                  className="size-10 scale-[1.15] object-contain"
                  aria-hidden="true"
                />
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

            <div className="flex shrink-0 items-center gap-2">
              <Button
                type="button"
                variant="outline"
                title={raspberryButtonLabel}
                aria-label={raspberryButtonLabel}
                disabled={isRaspberryButtonDisabled}
                onClick={() => void handleRaspberryTalk()}
                className="h-9 gap-2 border-[#E7DDC8] bg-white px-2.5 font-bold text-[#4A4438] hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-gray-200 dark:hover:bg-[#2A251D]"
              >
                {isRaspberryBusy ? (
                  <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                ) : (
                  <Mic className="size-4" aria-hidden="true" />
                )}
                <span className="hidden sm:inline">{raspberryButtonLabel}</span>
              </Button>

              <Button
                type="button"
                onClick={() => setIsHintOpen(true)}
                className="h-9 gap-2 bg-[#FECA43] px-3 font-bold text-[#2E2A22] hover:bg-[#F5B522] lg:hidden"
              >
                <Lightbulb className="size-4" aria-hidden="true" />
                힌트
              </Button>
            </div>
          </header>

          <ScrollArea className="min-h-0 flex-1">
            <div className="space-y-4 px-3 py-5 sm:px-5 lg:px-8">
              <div className="mx-auto max-w-3xl rounded-lg border border-[#E7DDC8] bg-[#FFF7E0] px-4 py-3 text-sm leading-relaxed text-[#4A4438] break-keep transition-colors dark:border-[#6B5A32] dark:bg-[#2A251D] dark:text-gray-300">
                <p className="font-bold text-[#6B5200] dark:text-[#FECA43]">
                  오늘의 대화 목표
                </p>
                <p className="mt-1">
                  Ducky는 정답·원인·개념을 강의하지 않습니다. 맥락을 이해한 뒤
                  질문으로 끌고 가며, 스스로 깨달을 때까지 말하게 합니다.
                  필요할 때만 힌트를 사용하세요.
                </p>
              </div>

              {isLoadingSession && (
                <div className="mx-auto flex max-w-3xl items-center gap-3 rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 text-sm text-gray-600 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300 dark:shadow-none">
                  <Loader2 className="size-4 shrink-0 animate-spin text-[#B88700]" />
                  채팅 세션을 불러오는 중입니다.
                </div>
              )}

              {error && (
                <div className="mx-auto flex max-w-3xl flex-col gap-3 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700 sm:flex-row sm:items-center sm:justify-between dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-200">
                  <div className="flex min-w-0 items-start gap-2">
                    <AlertCircle className="mt-0.5 size-4 shrink-0" />
                    <p className="min-w-0 break-keep">{error}</p>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    className="h-9 shrink-0 gap-2 border-red-200 bg-white text-red-700 hover:bg-red-100 dark:border-red-500/30 dark:bg-transparent dark:text-red-200 dark:hover:bg-red-500/10"
                    disabled={isLoadingSession}
                    onClick={() => void retry()}
                  >
                    <RefreshCw className="size-4" />
                    다시 시도
                  </Button>
                </div>
              )}

              {raspberryError && (
                <div className="mx-auto flex max-w-3xl items-start gap-2 rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-200">
                  <AlertCircle className="mt-0.5 size-4 shrink-0" />
                  <p className="min-w-0 break-keep">{raspberryError}</p>
                </div>
              )}

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
            disabled={isThinking || isCompleted || isLoadingSession || !isReady}
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
            />
          </div>
        </SheetContent>
      </Sheet>
    </div>
  );
}
