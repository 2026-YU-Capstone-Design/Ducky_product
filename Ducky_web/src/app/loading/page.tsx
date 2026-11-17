import type { Metadata } from "next";
import { LoadingPage } from "@/components/layout/LoadingPage";

export const metadata: Metadata = {
  title: "생각하는 중 | Ducky",
};

export default function LoadingPreviewPage() {
  return <LoadingPage />;
}
