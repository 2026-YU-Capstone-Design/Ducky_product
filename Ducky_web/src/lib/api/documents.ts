import { apiRequest } from "./client";
import type { LearningMaterial } from "@/types/material";

export interface DocumentSearchResult {
  documentId: string;
  chunkId: string;
  title: string;
  content: string;
  score: number;
  blockId?: string | null;
  headingPath?: string | null;
  pageNumber?: number | null;
  blockIds?: string[];
  contextChunkIds?: string[];
}

export interface DocumentSearchResponse {
  results: DocumentSearchResult[];
}

export function listDocuments() {
  return apiRequest<LearningMaterial[]>("/api/documents", {
    method: "GET",
    auth: true,
  });
}

export function getDocument(documentId: string) {
  return apiRequest<LearningMaterial>(`/api/documents/${documentId}`, { method: "GET", auth: true });
}

export function isDocumentProcessing(status: string) {
  return status === "pending" || status === "processing" || status === "processed";
}

export function isDocumentSearchable(document: LearningMaterial) {
  return document.indexingStatus === "indexed" && document.ragEnabled;
}

export async function waitForDocument(document: LearningMaterial, signal?: AbortSignal) {
  const deadline = Date.now() + 330_000;
  let current = document;
  while (isDocumentProcessing(current.indexingStatus)) {
    signal?.throwIfAborted();
    if (Date.now() > deadline) throw new Error("문서 처리 시간이 길어지고 있습니다. 자료실에서 상태를 확인해 주세요.");
    await new Promise((resolve) => setTimeout(resolve, 1000));
    signal?.throwIfAborted();
    current = await getDocument(current.id);
  }
  if (current.indexingStatus !== "indexed") {
    throw new Error(current.processingError === "OCR_REQUIRED"
      ? "첨부 PDF에 이미지로 된 페이지가 있어 OCR 처리가 필요합니다."
      : `첨부 문서 처리에 실패했습니다: ${current.processingError ?? current.indexingStatus}`);
  }
  if (!current.ragEnabled) throw new Error("첨부 문서가 검색 대상에서 제외되어 있습니다. 자료실에서 확인해 주세요.");
  return current;
}

export function uploadDocument(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return apiRequest<LearningMaterial>("/api/documents", {
    method: "POST",
    auth: true,
    body: formData,
  });
}

export function deleteDocument(documentId: string) {
  return apiRequest<void>(`/api/documents/${documentId}`, {
    method: "DELETE",
    auth: true,
  });
}

export function retryDocument(documentId: string) {
  return apiRequest<LearningMaterial>(`/api/documents/${documentId}/retry`, {
    method: "POST", auth: true,
  });
}

export function searchDocuments(query: string, limit = 5) {
  return apiRequest<DocumentSearchResponse>("/api/documents/search", {
    method: "POST",
    auth: true,
    body: {
      query,
      limit,
    },
  });
}
