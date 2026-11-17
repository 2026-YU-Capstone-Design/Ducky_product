import { apiRequest } from "./client";
import type { LearningMaterial } from "@/types/material";

export interface DocumentSearchResult {
  documentId: string;
  chunkId: string;
  title: string;
  content: string;
  score: number;
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
