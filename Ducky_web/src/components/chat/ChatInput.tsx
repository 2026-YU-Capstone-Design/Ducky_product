"use client";

import { useRef, useState } from "react";
import { Paperclip, SendHorizontal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type { VoiceInputStatus } from "@/hooks/useVoiceInput";
import { VoiceInputButton } from "./VoiceInputButton";

interface ChatInputProps {
  disabled?: boolean;
  onChange: (value: string) => void;
  onSend: (payload: { text: string; files?: File[] }) => void;
  onVoiceCancel: () => void;
  onVoiceStart: () => void;
  value: string;
  voiceStatus: VoiceInputStatus;
  voiceStatusLabel: string;
}

const ACCEPTED_ATTACHMENT_TYPES = ".pdf,.txt,.md,.doc,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.csv,.json";

export function ChatInput({
  disabled,
  onChange,
  onSend,
  onVoiceCancel,
  onVoiceStart,
  value,
  voiceStatus,
  voiceStatusLabel,
}: ChatInputProps) {
  const [files, setFiles] = useState<File[]>([]);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const canSend = value.trim().length > 0 || files.length > 0;

  const send = () => {
    if (!canSend || disabled) return;

    onSend({
      text: value,
      files,
    });

    onChange("");
    setFiles([]);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const removeFile = (index: number) => {
    setFiles((prev) => prev.filter((_, itemIndex) => itemIndex !== index));
  };

  return (
    <form
      className="border-t border-[#E7DDC8] bg-[#FAF8F5] px-3 py-3 transition-colors dark:border-white/10 dark:bg-[#201D19] sm:px-4 lg:px-5"
      onSubmit={(event) => {
        event.preventDefault();
        send();
      }}
    >
      {files.length > 0 && (
        <div className="mb-3 flex flex-wrap gap-2">
          {files.map((file, index) => (
            <span
              key={`${file.name}-${index}`}
              className="inline-flex max-w-full items-center gap-2 rounded-lg border border-[#E7DDC8] bg-white px-2.5 py-1.5 text-xs text-gray-700 transition-colors dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300"
            >
              <span className="shrink-0 font-bold text-[#B88700] dark:text-[#FECA43]">
                세션 첨부
              </span>
              <span className="max-w-[10rem] truncate sm:max-w-[14rem]">
                {file.name}
              </span>
              <button
                type="button"
                onClick={() => removeFile(index)}
                className="rounded text-gray-500 hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:text-gray-400 dark:hover:text-white"
                aria-label={`${file.name} 제거`}
              >
                <X className="size-3.5" aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="flex items-end gap-2">
        <label
          className="inline-flex size-11 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-[#E7DDC8] bg-white text-gray-700 shadow-sm transition-colors hover:bg-[#FFF7E0] focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-[#FECA43] dark:border-white/10 dark:bg-[#24211D] dark:text-gray-300 dark:shadow-none dark:hover:bg-[#2A251D]"
          title="세션 자료 첨부"
        >
          <Paperclip className="size-5" aria-hidden="true" />
          <span className="sr-only">세션 자료 첨부</span>
          <input
            ref={fileInputRef}
            accept={ACCEPTED_ATTACHMENT_TYPES}
            type="file"
            className="sr-only"
            multiple
            onChange={(event) => {
              const selectedFiles = event.target.files;
              if (!selectedFiles) return;
              setFiles((prev) => [...prev, ...Array.from(selectedFiles)]);
            }}
          />
        </label>

        <Textarea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="질문하거나, 자료를 붙여 같이 물어보세요."
          className="max-h-32 min-h-11 resize-none border-[#E7DDC8] bg-white px-4 py-3 text-sm shadow-sm focus-visible:border-[#FECA43] focus-visible:ring-[#FECA43]/30 dark:border-white/10 dark:bg-[#1D1B18] dark:text-white dark:shadow-none dark:placeholder:text-gray-500"
          disabled={disabled}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
        />

        <Button
          type="submit"
          disabled={!canSend || disabled}
          size="icon-lg"
          title="전송"
          aria-label="메시지 전송"
          className="size-11 rounded-lg bg-[#2E2A22] text-white hover:bg-[#4A4438] dark:bg-[#FECA43] dark:text-[#2E2A22] dark:hover:bg-[#F5B522]"
        >
          <SendHorizontal className="size-5" aria-hidden="true" />
        </Button>

        <VoiceInputButton
          disabled={disabled}
          onCancel={onVoiceCancel}
          onStart={onVoiceStart}
          status={voiceStatus}
          statusLabel={voiceStatusLabel}
        />
      </div>
    </form>
  );
}
