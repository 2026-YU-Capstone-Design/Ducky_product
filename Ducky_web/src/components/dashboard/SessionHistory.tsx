"use client";

import { useMemo, useState } from "react";
import {
  CalendarDays,
  CheckCircle2,
  Clock3,
  MessageCircle,
  Sparkles,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
import { mockSessions } from "@/data/mockSessions";
import type { ChatMessage } from "@/types/chat";
import type { Session, SessionStatus } from "@/types/session";
import { cn } from "@/lib/utils";
import { SessionCard } from "./SessionCard";

type SessionFilter = "all" | SessionStatus;

const filters: Array<{ label: string; value: SessionFilter }> = [
  { label: "전체", value: "all" },
  { label: "진행 중", value: "in_progress" },
  { label: "완료", value: "completed" },
];

function statusLabel(status: SessionStatus) {
  return status === "completed" ? "완료" : "진행 중";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function messageAuthor(message: ChatMessage) {
  if (message.role === "user") return "나";
  if (message.type === "hint") {
    const hintLabel = message.hintNumber ?? message.hintLevel;
    return hintLabel ? `힌트 ${hintLabel}` : "힌트";
  }
  return "Ducky";
}

function SessionMessage({ message }: { message: ChatMessage }) {
  const isUser = message.role === "user";
  const isHint = message.type === "hint";

  return (
    <article
      className={cn(
        "flex gap-3",
        isUser ? "justify-end" : "justify-start",
      )}
    >
      <div
        className={cn(
          "max-w-[88%] rounded-lg border px-4 py-3 text-sm leading-relaxed shadow-sm break-keep whitespace-pre-wrap transition-colors",
          isUser
            ? "border-[#FECA43] bg-[#FECA43] text-[#2E2A22]"
            : "border-[#E7DDC8] bg-white text-gray-800 dark:border-white/10 dark:bg-[#24211D] dark:text-gray-100 dark:shadow-none",
          isHint &&
            "border-[#FECA43] bg-[#FFF7E0] dark:border-[#FECA43]/70 dark:bg-[#2A251D] dark:text-gray-100",
        )}
      >
        <div className="mb-1 flex items-center justify-between gap-3">
          <p
            className={cn(
              "text-xs font-bold",
              isUser ? "text-[#6B5200]" : "text-gray-500 dark:text-gray-400",
              isHint && "text-[#B88700]",
            )}
          >
            {messageAuthor(message)}
          </p>
          <time className="shrink-0 text-xs text-gray-400 dark:text-gray-500">
            {formatDateTime(message.createdAt)}
          </time>
        </div>
        {message.content}
      </div>
    </article>
  );
}

function SessionDetail({ session }: { session: Session }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden p-5">
      <div className="grid shrink-0 gap-3 sm:grid-cols-3">
        <Card className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] py-0 dark:border-white/10 dark:bg-[#24211D]">
          <CardContent className="p-3">
            <p className="text-xs text-gray-500 dark:text-gray-400">상태</p>
            <p className="mt-1 font-bold text-gray-950 dark:text-white">
              {statusLabel(session.status)}
            </p>
          </CardContent>
        </Card>
        <Card className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] py-0 dark:border-white/10 dark:bg-[#24211D]">
          <CardContent className="p-3">
            <p className="text-xs text-gray-500 dark:text-gray-400">힌트</p>
            <p className="mt-1 font-bold text-gray-950 dark:text-white">
              {session.hintCount}회
            </p>
          </CardContent>
        </Card>
        <Card className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] py-0 dark:border-white/10 dark:bg-[#24211D]">
          <CardContent className="p-3">
            <p className="text-xs text-gray-500 dark:text-gray-400">메시지</p>
            <p className="mt-1 font-bold text-gray-950 dark:text-white">
              {session.messageCount}개
            </p>
          </CardContent>
        </Card>
      </div>

      <div className="mt-4 shrink-0 rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#24211D]">
        <p className="text-xs font-semibold text-[#B88700]">요약</p>
        <p className="mt-2 text-sm leading-relaxed text-gray-700 break-keep dark:text-gray-300">
          {session.summary}
        </p>
        <dl className="mt-4 grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-xs text-gray-500 dark:text-gray-400">시작</dt>
            <dd className="mt-1 font-medium text-gray-800 dark:text-gray-200">
              {formatDateTime(session.startedAt)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-gray-500 dark:text-gray-400">
              {session.completedAt ? "완료" : "최근 업데이트"}
            </dt>
            <dd className="mt-1 font-medium text-gray-800 dark:text-gray-200">
              {formatDateTime(session.completedAt ?? session.updatedAt)}
            </dd>
          </div>
        </dl>
      </div>

      <div className="mt-5 flex min-h-0 flex-1 flex-col">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-sm font-bold text-gray-950 dark:text-white">
            대화 내역
          </h3>
          <span className="shrink-0 text-xs font-medium text-gray-500 dark:text-gray-400">
            {session.messages.length}개
          </span>
        </div>
        <ScrollArea className="mt-3 min-h-[14rem] flex-1 rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] dark:border-white/10 dark:bg-[#1D1B18] sm:min-h-[20rem]">
          <div className="space-y-3 p-3 sm:p-4">
            {session.messages.map((message) => (
              <SessionMessage key={message.id} message={message} />
            ))}
          </div>
        </ScrollArea>
      </div>
    </div>
  );
}

export function SessionHistory() {
  const [filter, setFilter] = useState<SessionFilter>("all");
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);

  const sortedSessions = useMemo(
    () =>
      [...mockSessions].sort(
        (a, b) =>
          new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
      ),
    [],
  );

  const filteredSessions = useMemo(
    () =>
      filter === "all"
        ? sortedSessions
        : sortedSessions.filter((session) => session.status === filter),
    [filter, sortedSessions],
  );

  const completedCount = sortedSessions.filter(
    (session) => session.status === "completed",
  ).length;
  const inProgressCount = sortedSessions.filter(
    (session) => session.status === "in_progress",
  ).length;

  return (
    <section className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-5 py-8 sm:px-8 lg:px-10">
      <div className="border-b border-[#E7DDC8] pb-8 dark:border-white/10">
        <p className="text-sm font-semibold text-[#B88700]">학습 기록</p>
        <div className="mt-3 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div className="min-w-0">
            <h1 className="max-w-3xl text-3xl font-bold leading-tight tracking-tight text-gray-950 break-keep dark:text-white sm:text-4xl">
              지난 대화를 다시 확인하세요
            </h1>
            <p className="mt-4 max-w-3xl text-base leading-relaxed text-gray-600 break-keep dark:text-gray-300">
              완료되었거나 진행 중인 세션을 열어 질문 흐름, 힌트 사용, Ducky의
              피드백을 확인합니다.
            </p>
          </div>

          <div className="grid grid-cols-3 gap-2 sm:w-auto">
            <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 dark:border-white/10 dark:bg-[#24211D]">
              <p className="text-xs text-gray-500 dark:text-gray-400">전체</p>
              <p className="mt-1 text-xl font-bold text-gray-950 dark:text-white">
                {sortedSessions.length}
              </p>
            </div>
            <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 dark:border-white/10 dark:bg-[#24211D]">
              <p className="text-xs text-gray-500 dark:text-gray-400">진행</p>
              <p className="mt-1 text-xl font-bold text-gray-950 dark:text-white">
                {inProgressCount}
              </p>
            </div>
            <div className="rounded-lg border border-[#E7DDC8] bg-white px-4 py-3 dark:border-white/10 dark:bg-[#24211D]">
              <p className="text-xs text-gray-500 dark:text-gray-400">완료</p>
              <p className="mt-1 text-xl font-bold text-gray-950 dark:text-white">
                {completedCount}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-2" aria-label="세션 필터">
          {filters.map((item) => (
            <Button
              key={item.value}
              type="button"
              variant={filter === item.value ? "default" : "outline"}
              onClick={() => setFilter(item.value)}
              className={cn(
                "h-9 px-4",
                filter === item.value
                  ? "bg-[#FECA43] text-[#2E2A22] hover:bg-[#F5B522]"
                  : "border-[#E7DDC8] bg-white hover:bg-[#FFF7E0] dark:border-white/10 dark:bg-[#24211D] dark:text-gray-200 dark:hover:bg-[#2A251D]",
              )}
            >
              {item.label}
            </Button>
          ))}
        </div>

        <div className="flex flex-wrap gap-2 text-xs text-gray-500 dark:text-gray-400">
          <span className="inline-flex items-center gap-1.5">
            <Clock3 className="size-3.5 text-[#B88700]" />
            진행 중
          </span>
          <span className="inline-flex items-center gap-1.5">
            <CheckCircle2 className="size-3.5 text-[#236B35]" />
            완료됨
          </span>
        </div>
      </div>

      <div className="mt-5 grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {filteredSessions.map((session) => (
          <SessionCard
            key={session.id}
            isSelected={selectedSession?.id === session.id}
            onSelect={setSelectedSession}
            session={session}
          />
        ))}
      </div>

      {filteredSessions.length === 0 && (
        <div className="mt-5 rounded-lg border border-dashed border-[#E7DDC8] bg-white px-5 py-10 text-center dark:border-white/10 dark:bg-[#24211D]">
          <p className="font-bold text-gray-950 dark:text-white">
            표시할 세션이 없습니다
          </p>
          <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
            다른 필터를 선택하면 세션 기록을 다시 볼 수 있습니다.
          </p>
        </div>
      )}

      <Dialog
        open={selectedSession !== null}
        onOpenChange={(open) => {
          if (!open) setSelectedSession(null);
        }}
      >
        <DialogContent className="flex max-h-[92dvh] min-h-[min(42rem,92dvh)] gap-0 overflow-hidden rounded-lg p-0 dark:border-white/10 dark:bg-[#201D19] sm:max-w-4xl">
          {selectedSession && (
            <>
              <DialogHeader className="shrink-0 border-b border-[#E7DDC8] p-5 pr-12 dark:border-white/10">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge
                    className={cn(
                      selectedSession.status === "completed"
                        ? "bg-[#EAF7ED] text-[#236B35]"
                        : "bg-[#FFF1BC] text-[#6B5200]",
                    )}
                  >
                    {statusLabel(selectedSession.status)}
                  </Badge>
                  <span className="inline-flex items-center gap-1 text-xs font-semibold text-[#B88700]">
                    <Sparkles className="size-3.5" />
                    {selectedSession.topic}
                  </span>
                </div>
                <DialogTitle className="mt-2 text-xl font-bold leading-snug text-gray-950 break-keep dark:text-white">
                  {selectedSession.title}
                </DialogTitle>
                <DialogDescription className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-gray-500 dark:text-gray-400">
                  <span className="inline-flex items-center gap-1.5">
                    <CalendarDays className="size-4" />
                    {formatDateTime(selectedSession.updatedAt)}
                  </span>
                  <span className="inline-flex items-center gap-1.5">
                    <MessageCircle className="size-4" />
                    {selectedSession.messageCount}개 메시지
                  </span>
                </DialogDescription>
              </DialogHeader>

              <SessionDetail session={selectedSession} />
            </>
          )}
        </DialogContent>
      </Dialog>
    </section>
  );
}
