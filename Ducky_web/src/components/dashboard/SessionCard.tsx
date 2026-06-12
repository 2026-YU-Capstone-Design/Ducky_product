"use client";

import { CalendarDays, MessageCircle, Sparkles } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { Session } from "@/types/session";
import { cn } from "@/lib/utils";

interface SessionCardProps {
  isSelected?: boolean;
  onSelect: (session: Session) => void;
  session: Session;
}

function statusLabel(status: Session["status"]) {
  return status === "completed" ? "완료" : "진행 중";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

export function SessionCard({
  isSelected,
  onSelect,
  session,
}: SessionCardProps) {
  return (
    <Card
      className={cn(
        "rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none",
        isSelected && "border-[#FECA43] ring-2 ring-[#FECA43]/30",
      )}
    >
      <CardContent className="p-0">
        <button
          type="button"
          onClick={() => onSelect(session)}
          className="block w-full rounded-lg p-4 text-left outline-none transition-colors hover:bg-[#FFFDF7] focus-visible:ring-3 focus-visible:ring-[#FECA43]/50 dark:hover:bg-[#2A251D] sm:p-5"
          aria-label={`${session.title} 상세 보기`}
        >
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-[#B88700]">
                {session.topic}
              </p>
              <h3 className="mt-2 line-clamp-2 text-base font-bold leading-snug text-gray-950 break-keep dark:text-white">
                {session.title}
              </h3>
            </div>
            <Badge
              className={cn(
                "shrink-0",
                session.status === "completed"
                  ? "bg-[#EAF7ED] text-[#236B35]"
                  : "bg-[#FFF1BC] text-[#6B5200]",
              )}
            >
              {statusLabel(session.status)}
            </Badge>
          </div>

          <p className="mt-3 line-clamp-2 text-sm leading-relaxed text-gray-600 break-keep dark:text-gray-300">
            {session.summary}
          </p>

          <div className="mt-5 grid grid-cols-3 gap-2 text-sm">
            <span className="inline-flex min-w-0 items-center gap-1.5 text-gray-500 dark:text-gray-400">
              <Sparkles className="size-4 shrink-0 text-[#B88700]" />
              <span className="truncate">힌트 {session.hintCount}회</span>
            </span>
            <span className="inline-flex min-w-0 items-center gap-1.5 text-gray-500 dark:text-gray-400">
              <MessageCircle className="size-4 shrink-0 text-[#B88700]" />
              <span className="truncate">메시지 {session.messageCount}개</span>
            </span>
            <span className="inline-flex min-w-0 items-center gap-1.5 text-gray-500 dark:text-gray-400">
              <CalendarDays className="size-4 shrink-0 text-[#B88700]" />
              <span className="truncate">{formatDate(session.updatedAt)}</span>
            </span>
          </div>
        </button>
      </CardContent>
    </Card>
  );
}
