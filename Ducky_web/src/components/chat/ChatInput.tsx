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

const ACCEPTED_ATTACHMENT_TYPES =
  ".pdf,.docx,.pptx,.txt,.md,.csv,.json";

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
      className="border-t border-[#F0E0A8] bg-white px-3 py-3 transition-colors dark:border-[#5C4413]/40 dark:bg-[#221C0D] sm:px-4 lg:px-5"
      onSubmit={(event) => {
        event.preventDefault();
        send();
      }}
    >
      {files.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-2 px-1">
          {files.map((file, index) => (
            <span
              key={`${file.name}-${index}`}
              className="inline-flex max-w-full items-center gap-2 rounded-full border border-[#F0E0A8] bg-[#FFF6DC] px-2.5 py-1 text-xs text-[#5C4413] transition-colors dark:border-[#5C4413]/40 dark:bg-[#2A2310] dark:text-[#E8C97A]"
            >
              <span className="max-w-[10rem] truncate sm:max-w-[14rem]">
                {file.name}
              </span>
              <button
                type="button"
                onClick={() => removeFile(index)}
                className="rounded-full text-[#8A6A28] hover:text-[#3D2E0A] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:text-[#C4A85A] dark:hover:text-white"
                aria-label={`${file.name} 제거`}
              >
                <X className="size-3.5" aria-hidden="true" />
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="flex items-center gap-2 rounded-[26px] border border-[#F0E0A8] bg-[#FFFCF3] py-1.5 pr-1.5 pl-3.5 transition-colors focus-within:border-[#FECA43] dark:border-[#5C4413]/40 dark:bg-[#1F1A09]">
        <label
          className="inline-flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-full text-[#8A6A28] transition-colors hover:bg-[#F0E0A8]/50 hover:text-[#3D2E0A] focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-[#FECA43] dark:text-[#C4A85A] dark:hover:bg-white/5 dark:hover:text-white"
          title="세션 자료 첨부"
        >
          <Paperclip className="size-4" aria-hidden="true" />
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
          className="min-w-0 max-h-32 min-h-9 flex-1 resize-none border-0 bg-transparent px-0 py-1.5 text-sm text-[#3D2E0A] shadow-none placeholder:text-[#A8895C] focus-visible:ring-0 dark:text-[#F0E0C4] dark:placeholder:text-[#6B5526]"
          disabled={disabled}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              send();
            }
          }}
        />

        <VoiceInputButton
          disabled={disabled}
          onCancel={onVoiceCancel}
          onStart={onVoiceStart}
          status={voiceStatus}
          statusLabel={voiceStatusLabel}
        />

        <Button
          type="submit"
          disabled={!canSend || disabled}
          size="icon"
          title="전송"
          aria-label="메시지 전송"
          className="size-9 shrink-0 rounded-full bg-[#3D2E0A] text-[#FFEFC4] hover:bg-[#5C4413] disabled:bg-[#E5DCC3] disabled:text-[#A8895C] dark:bg-[#FECA43] dark:text-[#3D2E0A] dark:hover:bg-[#F5B522]"
        >
          <SendHorizontal className="size-4" aria-hidden="true" />
        </Button>
      </div>
    </form>
  );
}
