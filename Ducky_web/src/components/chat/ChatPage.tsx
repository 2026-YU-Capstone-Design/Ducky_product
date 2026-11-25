"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AlertCircle, Lightbulb, Loader2, Mic, RefreshCw } from "lucide-react";
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
  type DeviceCommandResponse,
  type DeviceResponse,
} from "@/lib/api/devices";
import {
  isRecordingCommandActive,
  reconcileRecordingCommand,
  shouldRefreshRecordingMessages,
} from "@/lib/api/recording-command";
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
  const lastRaspberryCommandRef = useRef<DeviceCommandResponse | null>(null);
  const startingRaspberryRef = useRef(false);
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
    pendingMessage,
    processingMessage,
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

  const applyRaspberryCommand = useCallback(async (incoming: DeviceCommandResponse | null) => {
    const previous = lastRaspberryCommandRef.current?.deviceId === raspberryDeviceId
      ? lastRaspberryCommandRef.current : null;
    const command = reconcileRecordingCommand(previous, incoming);
    lastRaspberryCommandRef.current = command;
    setIsRaspberryCommandPending(isRecordingCommandActive(command));
    if (command?.status === "FAILED") {
      setRaspberryError(command.errorMessage ?? "라즈베리 녹음 명령을 처리하지 못했습니다.");
    } else {
      setRaspberryError(null);
    }
    if (shouldRefreshRecordingMessages(previous, command, activeSession.id)) {
      await refreshMessages();
    }
  }, [activeSession.id, raspberryDeviceId, refreshMessages]);

  const handleRaspberryTalk = useCallback(async () => {
    if (!raspberryDeviceId || !activeSession.id || isRaspberryButtonDisabled || startingRaspberryRef.current) {
      return;
    }

    setRaspberryError(null);
    startingRaspberryRef.current = true;
    setIsStartingRaspberry(true);

    try {
      await applyRaspberryCommand(await startRaspberryRecording(raspberryDeviceId, activeSession.id));
    } catch (startError) {
      setRaspberryError(getRaspberryErrorMessage(startError));
    } finally {
      startingRaspberryRef.current = false;
      setIsStartingRaspberry(false);
    }
  }, [activeSession.id, applyRaspberryCommand, isRaspberryButtonDisabled, raspberryDeviceId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages, pendingMessage, isThinking]);

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
        ) ?? devices[0];
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

    let cancelled = false;
    let polling = false;
    const pollCommand = async () => {
      if (polling || startingRaspberryRef.current) return;
      polling = true;
      try {
        const command = await getLatestDeviceCommand(raspberryDeviceId);
        if (!cancelled && !startingRaspberryRef.current) await applyRaspberryCommand(command);
      } catch (pollError) {
        if (!cancelled) setRaspberryError(getRaspberryErrorMessage(pollError));
      } finally {
        polling = false;
      }
    };

    void pollCommand();
    const timer = window.setInterval(() => void pollCommand(), 1500);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [activeSession.id, applyRaspberryCommand, raspberryDeviceId]);

  useEffect(() => {
    const previousStatus = previousRaspberryStatusRef.current;
    if (
      previousStatus &&
      previousStatus !== "IDLE" &&
      raspberryDeviceStatus === "IDLE"
    ) {
      void refreshMessages();
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
    <div className="flex h-full min-h-0 w-full bg-[#FFFCF3] transition-colors dark:bg-[#16130A] md:h-dvh">
      <div className="flex min-h-0 w-full gap-4 p-0 sm:p-4 lg:p-6">
        <section className="flex min-w-0 flex-1 flex-col overflow-hidden bg-[#FFFCF3] transition-colors dark:bg-[#16130A] sm:rounded-2xl sm:border sm:border-[#F0E0A8] sm:shadow-sm sm:dark:border-[#5C4413]/40 sm:dark:shadow-none">
          <header className="flex shrink-0 items-center justify-between gap-3 border-b border-[#F0E0A8] bg-white px-5 py-4 transition-colors dark:border-[#5C4413]/40 dark:bg-[#221C0D]">
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
                className="h-9 gap-2 rounded-full border-[#F0E0A8] bg-white px-2.5 font-bold text-[#4A4438] hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-gray-200 dark:hover:bg-[#2A251D]"
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
                className="h-9 gap-2 rounded-full bg-[#FECA43] px-3 font-bold text-[#2E2A22] hover:bg-[#F5B522] lg:hidden"
              >
                <Lightbulb className="size-4" aria-hidden="true" />
                힌트
              </Button>
            </div>
          </header>

          <ScrollArea className="min-h-0 flex-1">
            <div className="space-y-4 px-3 py-5 sm:px-5 lg:px-8">
              <div className="mx-auto max-w-3xl rounded-2xl border border-[#F0E0A8] bg-[#FFF6DC] px-4 py-3 text-sm leading-relaxed text-[#5C4413] break-keep transition-colors dark:border-[#5C4413]/40 dark:bg-[#2A2310] dark:text-[#E8C97A]">
                <p className="font-bold text-[#3D2E0A] dark:text-[#FECA43]">
                  오늘의 대화 목표
                </p>
                <p className="mt-1">
                  궁금한 내용을 질문하거나 학습 자료를 첨부하세요. 문서에 근거해
                  답하고, 스스로 풀어 보고 싶을 때는 힌트를 요청할 수 있습니다.
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
              {pendingMessage && <ChatBubble message={pendingMessage} />}

              {isThinking && (
                <div className="flex items-end gap-2">
                  <div className="relative size-[30px] shrink-0 overflow-hidden rounded-full bg-[#FECA43]">
                    <Image
                      src="/icons/ducky-character.png"
                      alt="Ducky"
                      width={30}
                      height={30}
                      className="size-full object-cover"
                    />
                  </div>
                  <div className="rounded-[14px] rounded-bl-[4px] border border-[#F0E0A8] bg-[#FFF6DC] px-[15px] py-[11px] transition-colors dark:border-[#5C4413]/40 dark:bg-[#2A2310]">
                    <span role="status" className="inline-flex gap-1">
                      <span className={processingMessage ? "text-xs" : "sr-only"}>
                        {processingMessage ?? "Ducky가 답변을 준비하고 있습니다"}
                      </span>
                      <span className="size-1.5 animate-bounce rounded-full bg-[#C4A85A] [animation-delay:-0.3s]" />
                      <span className="size-1.5 animate-bounce rounded-full bg-[#C4A85A] [animation-delay:-0.15s]" />
                      <span className="size-1.5 animate-bounce rounded-full bg-[#C4A85A]" />
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
          className="max-h-[84dvh] rounded-t-2xl bg-[#FFFCF3] p-0 dark:bg-[#16130A]"
        >
          <SheetHeader className="border-b border-[#F0E0A8] bg-white p-4 pr-12 dark:border-[#5C4413]/40 dark:bg-[#221C0D]">
            <SheetTitle className="text-lg font-bold text-[#3D2E0A] dark:text-[#F0E0C4]">
              힌트 패널
            </SheetTitle>
            <SheetDescription className="text-[#8A6A28] dark:text-[#C4A85A]">
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
