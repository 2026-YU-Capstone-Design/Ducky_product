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
        src: "/icons/ducky-character-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/ducky-character-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any maskable",
      },
    ],
  };
}
