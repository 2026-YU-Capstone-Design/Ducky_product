import type { Metadata } from "next";
import { ErrorStatePage } from "@/components/layout/ErrorStatePage";

export const metadata: Metadata = {
  title: "인터넷 연결 문제 | Ducky",
};

export default function NetworkErrorPreviewPage() {
  return <ErrorStatePage kind="network" />;
}
