export type LearningMaterialStatus =
  | "available"
  | "queued"
  | "review_required";

export type LearningMaterialKind =
  | "document"
  | "image"
  | "note"
  | "pdf"
  | "slide";

export type LearningMaterialSource = "chat" | "direct" | "iot";

export type LearningMaterialIndexingStatus =
  | "excluded"
  | "failed"
  | "indexed"
  | "pending";

export interface LearningMaterial {
  id: string;
  title: string;
  fileName: string;
  fileSize: number;
  kind: LearningMaterialKind;
  status: LearningMaterialStatus;
  source: LearningMaterialSource;
  ragEnabled: boolean;
  indexingStatus: LearningMaterialIndexingStatus;
  uploadedAt: string;
  lastUsedAt?: string;
}

export interface MaterialSourceSummary {
  id: LearningMaterialSource;
  title: string;
  description: string;
  policy: string;
  enabled: boolean;
  lastSyncedAt?: string;
}
