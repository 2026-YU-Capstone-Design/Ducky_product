import type { Metadata } from "next";
import { ErrorStatePage } from "@/components/layout/ErrorStatePage";

export const metadata: Metadata = {
  title: "알 수 없는 문제 | Ducky",
};

export default function UnknownErrorPreviewPage() {
  return <ErrorStatePage kind="unknown" />;
}
