"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";
import {
  AlertCircle,
  CheckCircle2,
  Clock3,
  Database,
  FileText,
  FolderOpen,
  Loader2,
  RefreshCw,
  Search,
  Trash2,
  UploadCloud,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import {
  deleteDocument,
  listDocuments,
  uploadDocument,
} from "@/lib/api/documents";
import { cn } from "@/lib/utils";
import type {
  LearningMaterial,
  LearningMaterialIndexingStatus,
  LearningMaterialKind,
  LearningMaterialSource,
  LearningMaterialStatus,
} from "@/types/material";

const ACCEPTED_FILE_TYPES =
  ".pdf,.txt,.md,.doc,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.csv,.json";

const sourceLabels: Record<LearningMaterialSource, string> = {
  chat: "대화 첨부",
  direct: "직접 추가",
  iot: "IoT 이벤트",
};

const statusLabels: Record<LearningMaterialStatus, string> = {
  available: "보관 중",
  queued: "등록 대기",
  review_required: "검토 필요",
};

const statusStyles: Record<LearningMaterialStatus, string> = {
  available:
    "border-[#BFD8C4] bg-[#EAF7ED] text-[#236B35] dark:border-[#BFD8C4]/40 dark:bg-[#1D2A22] dark:text-[#BFD8C4]",
  queued:
    "border-[#C9D7E2] bg-[#EAF4F8] text-[#245C7A] dark:border-[#C9D7E2]/40 dark:bg-[#1D252A] dark:text-[#B9D9E8]",
  review_required:
    "border-[#FECA43] bg-[#FFF7E0] text-[#6B5200] dark:border-[#FECA43]/60 dark:bg-[#2A251D] dark:text-[#FECA43]",
};

const indexingLabels: Record<LearningMaterialIndexingStatus, string> = {
  excluded: "RAG 제외",
  failed: "색인 실패",
  indexed: "색인 완료",
  pending: "색인 대기",
};

const indexingStyles: Record<LearningMaterialIndexingStatus, string> = {
  excluded:
    "border-[#E1E5EA] bg-white text-gray-500 dark:border-white/10 dark:bg-[#24211D] dark:text-gray-400",
  failed:
    "border-[#F2B8A2] bg-[#FFF0EA] text-[#8A3B20] dark:border-[#F2B8A2]/50 dark:bg-[#2A211D] dark:text-[#F2B8A2]",
  indexed:
    "border-[#BFD8C4] bg-[#EAF7ED] text-[#236B35] dark:border-[#BFD8C4]/40 dark:bg-[#1D2A22] dark:text-[#BFD8C4]",
  pending:
    "border-[#FECA43] bg-[#FFF7E0] text-[#6B5200] dark:border-[#FECA43]/60 dark:bg-[#2A251D] dark:text-[#FECA43]",
};

const kindLabels: Record<LearningMaterialKind, string> = {
  document: "문서",
  image: "이미지",
  note: "노트",
  pdf: "PDF",
  slide: "슬라이드",
};

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }

  return `${Math.max(Math.round(size / 1024), 1)} KB`;
}

function formatDate(value?: string) {
  if (!value) {
    return "기록 없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function MaterialStatusIcon({ status }: { status: LearningMaterialStatus }) {
  if (status === "available") {
    return <CheckCircle2 className="size-4 text-[#236B35]" aria-hidden="true" />;
  }

  return <Clock3 className="size-4 text-[#B88700]" aria-hidden="true" />;
}

export function MaterialLibrary() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [materials, setMaterials] = useState<LearningMaterial[]>([]);
  const [isDragging, setIsDragging] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [deletingIds, setDeletingIds] = useState<Set<string>>(new Set());
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const indexedCount = useMemo(
    () =>
      materials.filter((material) => material.indexingStatus === "indexed")
        .length,
    [materials],
  );
  const ragEnabledCount = useMemo(
    () => materials.filter((material) => material.ragEnabled).length,
    [materials],
  );
  const totalFileSize = useMemo(
    () => materials.reduce((sum, material) => sum + material.fileSize, 0),
    [materials],
  );

  const loadMaterials = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage(null);

    try {
      setMaterials(await listDocuments());
    } catch (error) {
      setErrorMessage(
        error instanceof Error
          ? error.message
          : "자료 목록을 불러오지 못했습니다.",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    let isMounted = true;

    async function loadInitialMaterials() {
      try {
        const nextMaterials = await listDocuments();
        if (isMounted) {
          setMaterials(nextMaterials);
        }
      } catch (error) {
        if (isMounted) {
          setErrorMessage(
            error instanceof Error
              ? error.message
              : "자료 목록을 불러오지 못했습니다.",
          );
        }
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    void loadInitialMaterials();

    return () => {
      isMounted = false;
    };
  }, []);

  async function addFiles(files: FileList | File[]) {
    const nextFiles = Array.from(files);

    if (nextFiles.length === 0) {
      return;
    }

    setIsUploading(true);
    setErrorMessage(null);

    try {
      const uploadedMaterials: LearningMaterial[] = [];
      for (const file of nextFiles) {
        uploadedMaterials.push(await uploadDocument(file));
      }

      setMaterials((currentMaterials) => [
        ...uploadedMaterials,
        ...currentMaterials.filter(
          (material) =>
            !uploadedMaterials.some(
              (uploadedMaterial) => uploadedMaterial.id === material.id,
            ),
        ),
      ]);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "자료 업로드에 실패했습니다.",
      );
    } finally {
      setIsUploading(false);
    }
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (event.target.files) {
      void addFiles(event.target.files);
    }

    event.target.value = "";
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    void addFiles(event.dataTransfer.files);
  }

  async function removeMaterial(id: string) {
    setDeletingIds((currentIds) => new Set(currentIds).add(id));
    setErrorMessage(null);

    try {
      await deleteDocument(id);
      setMaterials((currentMaterials) =>
        currentMaterials.filter((material) => material.id !== id),
      );
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "자료 삭제에 실패했습니다.",
      );
    } finally {
      setDeletingIds((currentIds) => {
        const nextIds = new Set(currentIds);
        nextIds.delete(id);
        return nextIds;
      });
    }
  }

  return (
    <Card className="rounded-lg border border-[#E7DDC8] bg-white py-0 shadow-sm transition-colors dark:border-white/10 dark:bg-[#24211D] dark:shadow-none lg:col-span-2">
      <CardContent className="p-5">
        <div className="flex flex-col gap-4 border-b border-[#E7DDC8] pb-5 dark:border-white/10 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <FolderOpen className="size-5 text-[#B88700]" aria-hidden="true" />
              <h2 className="text-lg font-bold tracking-tight text-gray-950 dark:text-white">
                자료 보관함
              </h2>
            </div>
            <p className="mt-3 max-w-2xl text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
              업로드한 자료는 색인 후 채팅 답변의 참고 문맥으로 사용됩니다.
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            <Button
              className="min-h-10 border-[#E7DDC8] dark:border-white/10"
              disabled={isLoading}
              onClick={() => void loadMaterials()}
              type="button"
              variant="outline"
            >
              {isLoading ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <RefreshCw className="size-4" aria-hidden="true" />
              )}
              새로고침
            </Button>
            <Button
              className="min-h-10 bg-[#FECA43] px-4 font-bold text-[#2E2A22] hover:bg-[#F5B522]"
              disabled={isUploading}
              onClick={() => fileInputRef.current?.click()}
              type="button"
            >
              {isUploading ? (
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              ) : (
                <UploadCloud className="size-4" aria-hidden="true" />
              )}
              파일 선택
            </Button>
          </div>
        </div>

        <div className="mt-5 grid gap-3 sm:grid-cols-3">
          <div className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#1D1B18]">
            <Database className="size-5 text-[#245C7A]" aria-hidden="true" />
            <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
              전체 자료
            </p>
            <p className="mt-1 text-2xl font-bold text-gray-950 dark:text-white">
              {materials.length}
            </p>
          </div>
          <div className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#1D1B18]">
            <Search className="size-5 text-[#236B35]" aria-hidden="true" />
            <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
              색인 완료
            </p>
            <p className="mt-1 text-2xl font-bold text-gray-950 dark:text-white">
              {indexedCount}
            </p>
          </div>
          <div className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#1D1B18]">
            <FileText className="size-5 text-[#B88700]" aria-hidden="true" />
            <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
              저장 용량
            </p>
            <p className="mt-1 text-2xl font-bold text-gray-950 dark:text-white">
              {formatFileSize(totalFileSize)}
            </p>
          </div>
        </div>

        {errorMessage && (
          <div className="mt-5 flex items-start gap-2 rounded-lg border border-[#F2B8A2] bg-[#FFF0EA] p-3 text-sm text-[#8A3B20] dark:border-[#F2B8A2]/50 dark:bg-[#2A211D] dark:text-[#F2B8A2]">
            <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <p className="min-w-0 break-keep">{errorMessage}</p>
          </div>
        )}

        <div
          className={cn(
            "mt-5 rounded-lg border border-dashed border-[#D9CBAE] bg-[#FAF8F5] p-5 transition-colors dark:border-white/15 dark:bg-[#1D1B18]",
            isDragging &&
              "border-[#FECA43] bg-[#FFF7E0] dark:border-[#FECA43] dark:bg-[#2A251D]",
          )}
          onDragEnter={() => setIsDragging(true)}
          onDragLeave={() => setIsDragging(false)}
          onDragOver={(event) => event.preventDefault()}
          onDrop={handleDrop}
        >
          <input
            ref={fileInputRef}
            accept={ACCEPTED_FILE_TYPES}
            className="sr-only"
            multiple
            onChange={handleFileChange}
            type="file"
          />
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex min-w-0 items-start gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white text-[#B88700] dark:bg-[#24211D] dark:text-[#FECA43]">
                <UploadCloud className="size-5" aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <h3 className="text-sm font-bold text-gray-950 dark:text-white">
                  직접 자료 추가
                </h3>
                <p className="mt-1 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                  txt, md, csv, json 파일은 바로 텍스트 chunk로 저장됩니다.
                </p>
              </div>
            </div>
            <Badge
              variant="outline"
              className="w-fit shrink-0 border-[#FECA43] bg-[#FFF7E0] text-[#6B5200] dark:border-[#FECA43]/60 dark:bg-[#2A251D] dark:text-[#FECA43]"
            >
              RAG 포함 {ragEnabledCount}
            </Badge>
          </div>
        </div>

        <div className="mt-5 divide-y divide-[#E7DDC8] overflow-hidden rounded-lg border border-[#E7DDC8] dark:divide-white/10 dark:border-white/10">
          {isLoading ? (
            <div className="flex min-h-32 items-center justify-center gap-2 bg-white p-4 text-sm text-gray-500 dark:bg-[#24211D] dark:text-gray-400">
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              자료를 불러오는 중
            </div>
          ) : materials.length === 0 ? (
            <div className="flex min-h-32 items-center justify-center bg-white p-4 text-center text-sm text-gray-500 break-keep dark:bg-[#24211D] dark:text-gray-400">
              아직 업로드된 자료가 없습니다.
            </div>
          ) : (
            materials.map((material) => (
              <article
                key={material.id}
                className="grid min-w-0 gap-4 bg-white p-4 transition-colors dark:bg-[#24211D] lg:grid-cols-[minmax(0,1fr)_13rem_2.5rem] lg:items-center"
              >
                <div className="flex min-w-0 items-start gap-3">
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-[#EAF4F8] text-[#245C7A] dark:bg-[#1D252A] dark:text-[#B9D9E8]">
                    <FileText className="size-5" aria-hidden="true" />
                  </span>

                  <div className="min-w-0 flex-1">
                    <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-center">
                      <h3 className="truncate text-sm font-bold text-gray-950 dark:text-white">
                        {material.title}
                      </h3>
                      <Badge
                        variant="outline"
                        className={cn(
                          "shrink-0",
                          statusStyles[material.status],
                        )}
                      >
                        <MaterialStatusIcon status={material.status} />
                        {statusLabels[material.status]}
                      </Badge>
                    </div>
                    <p className="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                      {material.fileName} / {kindLabels[material.kind]} /{" "}
                      {formatFileSize(material.fileSize)} /{" "}
                      {sourceLabels[material.source]} /{" "}
                      {formatDate(material.uploadedAt)}
                    </p>
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-3 lg:justify-end">
                  <Badge
                    variant="outline"
                    className={cn(
                      "shrink-0",
                      indexingStyles[material.indexingStatus],
                    )}
                  >
                    {indexingLabels[material.indexingStatus]}
                  </Badge>
                  <label
                    className="flex items-center gap-2 text-xs font-bold text-gray-600 dark:text-gray-300"
                    htmlFor={`rag-${material.id}`}
                  >
                    RAG
                    <Switch
                      checked={material.ragEnabled}
                      className="data-checked:bg-[#FECA43] data-unchecked:bg-[#E3E7ED]"
                      disabled
                      id={`rag-${material.id}`}
                    />
                  </label>
                </div>

                <button
                  aria-label={`${material.title} 삭제`}
                  className="flex size-9 shrink-0 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-[#FAF8F5] hover:text-gray-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] disabled:cursor-not-allowed disabled:opacity-50 dark:hover:bg-white/10 dark:hover:text-white"
                  disabled={deletingIds.has(material.id)}
                  onClick={() => void removeMaterial(material.id)}
                  type="button"
                >
                  {deletingIds.has(material.id) ? (
                    <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                  ) : (
                    <Trash2 className="size-4" aria-hidden="true" />
                  )}
                </button>
              </article>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
}
