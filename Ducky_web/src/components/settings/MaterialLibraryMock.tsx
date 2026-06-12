"use client";

import {
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
} from "react";
import {
  CheckCircle2,
  Clock3,
  Cpu,
  Database,
  FileText,
  FolderOpen,
  MessageCircle,
  RotateCcw,
  Search,
  Trash2,
  UploadCloud,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { mockMaterials, mockMaterialSources } from "@/data/mockMaterials";
import { cn } from "@/lib/utils";
import type {
  LearningMaterial,
  LearningMaterialIndexingStatus,
  LearningMaterialKind,
  LearningMaterialSource,
  LearningMaterialStatus,
} from "@/types/material";

const ACCEPTED_FILE_TYPES = ".pdf,.txt,.md,.doc,.docx,.ppt,.pptx,.png,.jpg,.jpeg,.csv,.json";

const sourceLabels: Record<LearningMaterialSource, string> = {
  chat: "대화 첨부",
  direct: "직접 추가",
  iot: "IoT 이벤트",
};

const sourceIcons = {
  chat: MessageCircle,
  direct: UploadCloud,
  iot: Cpu,
} satisfies Record<LearningMaterialSource, typeof MessageCircle>;

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

function getMaterialKind(fileName: string): LearningMaterialKind {
  const extension = fileName.split(".").pop()?.toLowerCase();

  if (extension === "pdf") {
    return "pdf";
  }

  if (extension === "png" || extension === "jpg" || extension === "jpeg") {
    return "image";
  }

  if (extension === "ppt" || extension === "pptx") {
    return "slide";
  }

  if (extension === "txt" || extension === "md") {
    return "note";
  }

  return "document";
}

function getTitleFromFileName(fileName: string) {
  return fileName.replace(/\.[^/.]+$/, "").replace(/[-_]/g, " ");
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }

  return `${Math.max(Math.round(size / 1024), 1)} KB`;
}

function formatDate(value?: string) {
  if (!value) {
    return "연결 전";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
  }).format(new Date(value));
}

function createDirectMaterial(file: File): LearningMaterial {
  return {
    id: `direct-${file.lastModified}-${file.name}`,
    title: getTitleFromFileName(file.name),
    fileName: file.name,
    fileSize: file.size,
    kind: getMaterialKind(file.name),
    status: "queued",
    source: "direct",
    ragEnabled: false,
    indexingStatus: "excluded",
    uploadedAt: new Date().toISOString(),
  };
}

function MaterialStatusIcon({ status }: { status: LearningMaterialStatus }) {
  if (status === "available") {
    return <CheckCircle2 className="size-4 text-[#236B35]" aria-hidden="true" />;
  }

  return <Clock3 className="size-4 text-[#B88700]" aria-hidden="true" />;
}

export function MaterialLibraryMock() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [materials, setMaterials] = useState<LearningMaterial[]>(mockMaterials);
  const [isDragging, setIsDragging] = useState(false);

  const queuedCount = useMemo(
    () => materials.filter((material) => material.status === "queued").length,
    [materials],
  );
  const ragEnabledCount = useMemo(
    () => materials.filter((material) => material.ragEnabled).length,
    [materials],
  );
  const excludedCount = materials.length - ragEnabledCount;

  function addFiles(files: FileList | File[]) {
    const nextMaterials = Array.from(files).map(createDirectMaterial);

    if (nextMaterials.length === 0) {
      return;
    }

    setMaterials((currentMaterials) => [
      ...nextMaterials,
      ...currentMaterials.filter(
        (material) =>
          !nextMaterials.some((nextMaterial) => nextMaterial.id === material.id),
      ),
    ]);
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (event.target.files) {
      addFiles(event.target.files);
    }

    event.target.value = "";
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setIsDragging(false);
    addFiles(event.dataTransfer.files);
  }

  function registerQueuedMaterials() {
    setMaterials((currentMaterials) =>
      currentMaterials.map((material) =>
        material.status === "queued"
          ? {
              ...material,
              status: "available",
              indexingStatus: material.ragEnabled ? "pending" : "excluded",
            }
          : material,
      ),
    );
  }

  function removeMaterial(id: string) {
    setMaterials((currentMaterials) =>
      currentMaterials.filter((material) => material.id !== id),
    );
  }

  function resetMaterials() {
    setMaterials(mockMaterials);
  }

  function toggleRagTarget(id: string, ragEnabled: boolean) {
    setMaterials((currentMaterials) =>
      currentMaterials.map((material) =>
        material.id === id
          ? {
              ...material,
              ragEnabled,
              indexingStatus: ragEnabled
                ? material.status === "available"
                  ? "pending"
                  : "excluded"
                : "excluded",
            }
          : material,
      ),
    );
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
              대화 첨부, 직접 추가 자료, IoT 이벤트 요약을 한 곳에서 관리합니다.
            </p>
          </div>

          <Badge
            variant="outline"
            className="w-fit shrink-0 border-[#FECA43] bg-[#FFF7E0] text-[#6B5200] dark:border-[#FECA43]/60 dark:bg-[#2A251D] dark:text-[#FECA43]"
          >
            RAG 대상 선별
          </Badge>
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
              RAG 대상
            </p>
            <p className="mt-1 text-2xl font-bold text-gray-950 dark:text-white">
              {ragEnabledCount}
            </p>
          </div>
          <div className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#1D1B18]">
            <Clock3 className="size-5 text-[#B88700]" aria-hidden="true" />
            <p className="mt-3 text-sm text-gray-500 dark:text-gray-400">
              RAG 제외
            </p>
            <p className="mt-1 text-2xl font-bold text-gray-950 dark:text-white">
              {excludedCount}
            </p>
          </div>
        </div>

        <div className="mt-5 grid gap-4 xl:grid-cols-[minmax(0,1fr)_18rem]">
          <div className="grid gap-3 lg:grid-cols-3">
            {mockMaterialSources.map((source) => {
              const Icon = sourceIcons[source.id];
              const count = materials.filter(
                (material) => material.source === source.id,
              ).length;

              return (
                <article
                  key={source.id}
                  className="rounded-lg border border-[#E7DDC8] bg-[#FAF8F5] p-4 dark:border-white/10 dark:bg-[#1D1B18]"
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="flex size-10 items-center justify-center rounded-lg bg-white text-[#B88700] dark:bg-[#24211D] dark:text-[#FECA43]">
                      <Icon className="size-5" aria-hidden="true" />
                    </span>
                    <Badge
                      variant="outline"
                      className="border-[#BFD8C4] bg-[#EAF7ED] text-[#236B35] dark:border-[#BFD8C4]/40 dark:bg-[#1D2A22] dark:text-[#BFD8C4]"
                    >
                      {count}개
                    </Badge>
                  </div>
                  <h3 className="mt-4 text-sm font-bold text-gray-950 dark:text-white">
                    {source.title}
                  </h3>
                  <p className="mt-2 min-h-10 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
                    {source.description}
                  </p>
                  <p className="mt-3 rounded-lg bg-white px-3 py-2 text-xs leading-relaxed text-gray-600 break-keep dark:bg-[#24211D] dark:text-gray-300">
                    {source.policy}
                  </p>
                  <p className="mt-3 text-xs text-gray-400 dark:text-gray-500">
                    최근 동기화 {formatDate(source.lastSyncedAt)}
                  </p>
                </article>
              );
            })}
          </div>

          <div
            className={cn(
              "rounded-lg border border-dashed border-[#D9CBAE] bg-[#FAF8F5] p-4 transition-colors dark:border-white/15 dark:bg-[#1D1B18]",
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
            <UploadCloud className="size-5 text-[#B88700]" aria-hidden="true" />
            <h3 className="mt-3 text-sm font-bold text-gray-950 dark:text-white">
              직접 자료 추가
            </h3>
            <p className="mt-2 text-sm leading-relaxed text-gray-500 break-keep dark:text-gray-400">
              보관함에서 관리할 장기 자료만 추가합니다.
            </p>
            <div className="mt-4 grid gap-2">
              <Button
                className="min-h-10 bg-[#FECA43] px-4 font-bold text-[#2E2A22] hover:bg-[#F5B522]"
                onClick={() => fileInputRef.current?.click()}
                type="button"
              >
                <UploadCloud className="size-4" aria-hidden="true" />
                파일 선택
              </Button>
              <Button
                className="min-h-10 border-[#E7DDC8] px-4 dark:border-white/10"
                disabled={queuedCount === 0}
                onClick={registerQueuedMaterials}
                type="button"
                variant="outline"
              >
                <CheckCircle2 className="size-4" aria-hidden="true" />
                보관함 등록
              </Button>
              <Button
                className="min-h-10 border-[#E7DDC8] dark:border-white/10"
                onClick={resetMaterials}
                type="button"
                variant="outline"
              >
                <RotateCcw className="size-4" aria-hidden="true" />
                mock 초기화
              </Button>
            </div>
          </div>
        </div>

        <div className="mt-5 divide-y divide-[#E7DDC8] overflow-hidden rounded-lg border border-[#E7DDC8] dark:divide-white/10 dark:border-white/10">
          {materials.map((material) => (
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
                      className={cn("shrink-0", statusStyles[material.status])}
                    >
                      <MaterialStatusIcon status={material.status} />
                      {statusLabels[material.status]}
                    </Badge>
                  </div>
                  <p className="mt-1 truncate text-xs text-gray-500 dark:text-gray-400">
                    {material.fileName} · {kindLabels[material.kind]} ·{" "}
                    {formatFileSize(material.fileSize)} ·{" "}
                    {sourceLabels[material.source]}
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
                  RAG 대상
                  <Switch
                    checked={material.ragEnabled}
                    className="data-checked:bg-[#FECA43] data-unchecked:bg-[#E3E7ED]"
                    id={`rag-${material.id}`}
                    onCheckedChange={(checked) =>
                      toggleRagTarget(material.id, checked)
                    }
                  />
                </label>
              </div>

              <button
                aria-label={`${material.title} 삭제`}
                className="flex size-9 shrink-0 items-center justify-center rounded-lg text-gray-400 transition-colors hover:bg-[#FAF8F5] hover:text-gray-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#FECA43] dark:hover:bg-white/10 dark:hover:text-white"
                onClick={() => removeMaterial(material.id)}
                type="button"
              >
                <Trash2 className="size-4" aria-hidden="true" />
              </button>
            </article>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
