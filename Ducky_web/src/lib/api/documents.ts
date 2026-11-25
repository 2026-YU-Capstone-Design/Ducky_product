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

export function documentProcessingErrorMessage(code: string) {
  switch (code) {
    case "PDF_VISION_REQUIRED":
      return "이 PDF에 이미지 분석이 필요한 페이지가 있습니다. 이미지 분석을 사용할 수 없어 색인을 완료하지 못했습니다.";
    case "PDF_VISION_UNAVAILABLE":
      return "PDF 이미지 분석에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
    case "PDF_VISION_LIMIT":
      return "PDF 이미지 분석 한도를 넘었습니다. 페이지를 나눠서 다시 올려 주세요.";
    case "PDF_VISION_UNREADABLE":
      return "PDF 일부 페이지를 읽지 못해 색인을 완료하지 못했습니다. 더 선명한 원본을 올려 주세요.";
    case "OCR_REQUIRED":
      return "이 파일을 처리할 때 OCR을 실행할 수 없었습니다. 텍스트를 선택할 수 있는 PDF를 올려 주세요.";
    case "OCR_RESOURCE_LIMIT":
      return "스캔 페이지의 처리 한도를 초과했습니다. PDF를 여러 파일로 나눠 올려 주세요.";
    case "OCR_NO_TEXT":
      return "스캔 페이지에서 읽을 수 있는 글자를 찾지 못했습니다. 해상도가 높은 원본이나 텍스트가 있는 PDF를 올려 주세요.";
    case "OCR_FAILED":
      return "이미지에서 글자를 읽는 중에 문제가 생겼습니다. 다시 시도하거나 다른 PDF를 올려 주세요.";
    default:
      return `문서 처리에 실패했습니다: ${code}`;
  }
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
    throw new Error(documentProcessingErrorMessage(current.processingError ?? current.indexingStatus));
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
