"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

export type VoiceInputStatus =
  | "idle"
  | "listening"
  | "transcribing"
  | "thinking";

type SpeechRecognitionAlternative = {
  transcript: string;
};

type SpeechRecognitionResultLike = {
  0: SpeechRecognitionAlternative;
  isFinal: boolean;
};

type SpeechRecognitionEventLike = {
  resultIndex: number;
  results: {
    length: number;
    [index: number]: SpeechRecognitionResultLike;
  };
};

type SpeechRecognitionErrorEventLike = {
  error?: string;
};

type BrowserSpeechRecognition = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  onend: (() => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onstart: (() => void) | null;
  abort: () => void;
  start: () => void;
};

type SpeechRecognitionConstructor = new () => BrowserSpeechRecognition;

type SpeechRecognitionWindow = Window & {
  SpeechRecognition?: SpeechRecognitionConstructor;
  webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

const statusLabels: Record<VoiceInputStatus, string> = {
  idle: "음성 입력",
  listening: "듣는 중",
  transcribing: "텍스트 변환 중",
  thinking: "문장 정리 중",
};

function getSpeechRecognitionConstructor() {
  if (typeof window === "undefined") {
    return null;
  }

  const speechWindow = window as SpeechRecognitionWindow;
  return (
    speechWindow.SpeechRecognition ??
    speechWindow.webkitSpeechRecognition ??
    null
  );
}

function getSpeechRecognitionErrorMessage(error?: string) {
  if (error === "not-allowed" || error === "service-not-allowed") {
    return "마이크 권한이 필요합니다.";
  }

  if (error === "no-speech") {
    return "인식된 음성이 없습니다.";
  }

  return "음성 인식에 실패했습니다.";
}

export function useVoiceInput({
  onTranscript,
}: {
  onTranscript: (text: string) => void;
}) {
  const recognitionRef = useRef<BrowserSpeechRecognition | null>(null);
  const shouldEmitTranscriptRef = useRef(false);
  const [status, setStatus] = useState<VoiceInputStatus>("idle");
  const [message, setMessage] = useState("");

  const cancel = useCallback(() => {
    shouldEmitTranscriptRef.current = false;
    recognitionRef.current?.abort();
    recognitionRef.current = null;
    setStatus("idle");
  }, []);

  const start = useCallback(() => {
    if (status !== "idle") return;

    const Recognition = getSpeechRecognitionConstructor();
    if (!Recognition) {
      setMessage("이 브라우저는 음성 인식을 지원하지 않습니다.");
      return;
    }

    let transcript = "";
    const recognition = new Recognition();
    recognitionRef.current = recognition;
    shouldEmitTranscriptRef.current = true;
    setMessage("");

    recognition.lang = "ko-KR";
    recognition.continuous = false;
    recognition.interimResults = true;

    recognition.onstart = () => {
      setStatus("listening");
    };

    recognition.onresult = (event) => {
      setStatus("transcribing");

      let interimTranscript = "";
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        const text = result[0]?.transcript ?? "";

        if (result.isFinal) {
          transcript += text;
        } else {
          interimTranscript += text;
        }
      }

      if (interimTranscript.trim()) {
        setMessage(interimTranscript.trim());
      }
    };

    recognition.onerror = (event) => {
      shouldEmitTranscriptRef.current = false;
      setMessage(getSpeechRecognitionErrorMessage(event.error));
      setStatus("idle");
    };

    recognition.onend = () => {
      const nextTranscript = transcript.trim();

      if (shouldEmitTranscriptRef.current && nextTranscript) {
        onTranscript(nextTranscript);
      }

      shouldEmitTranscriptRef.current = false;
      recognitionRef.current = null;
      setStatus("idle");
    };

    try {
      recognition.start();
    } catch {
      shouldEmitTranscriptRef.current = false;
      recognitionRef.current = null;
      setMessage("음성 인식을 시작하지 못했습니다.");
      setStatus("idle");
    }
  }, [onTranscript, status]);

  useEffect(() => {
    return () => {
      shouldEmitTranscriptRef.current = false;
      recognitionRef.current?.abort();
    };
  }, []);

  const statusLabel = useMemo(() => {
    if (message) {
      return message;
    }

    return statusLabels[status];
  }, [message, status]);

  return {
    cancel,
    isActive: status !== "idle",
    start,
    status,
    statusLabel,
  };
}
