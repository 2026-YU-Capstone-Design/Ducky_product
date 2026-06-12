import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Ducky",
    short_name: "Ducky",
    description: "AI 러버덕 학습 도우미",
    start_url: "/dashboard",
    scope: "/",
    display: "standalone",
    background_color: "#FAF8F5",
    theme_color: "#FECA43",
    icons: [
      {
        src: "/favicon.ico",
        sizes: "any",
        type: "image/x-icon",
      },
      {
        src: "/icons/ducky-icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
      {
        src: "/icons/ducky-icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "maskable",
      },
    ],
  };
}
