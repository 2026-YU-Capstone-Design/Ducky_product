import type { Metadata } from "next";
import { NotFoundPage } from "@/components/layout/NotFoundPage";

export const metadata: Metadata = {
  title: "페이지를 찾을 수 없어요 | Ducky",
};

export default function NotFound() {
  return <NotFoundPage />;
}
