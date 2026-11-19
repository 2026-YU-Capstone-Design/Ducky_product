"use client";

import { useSyncExternalStore } from "react";
import { ACCESS_TOKEN_STORAGE_KEY, AUTH_CHANGE_EVENT, getAccessToken } from "@/lib/api/client";

function subscribe(onChange: () => void) {
  function onStorage(event: StorageEvent) {
    if (event.key === ACCESS_TOKEN_STORAGE_KEY || event.key === null) onChange();
  }
  window.addEventListener("storage", onStorage);
  window.addEventListener(AUTH_CHANGE_EVENT, onChange);
  return () => {
    window.removeEventListener("storage", onStorage);
    window.removeEventListener(AUTH_CHANGE_EVENT, onChange);
  };
}

// Remount private UI when authentication changes; never persist document/chat data.
export function useAuthScope() {
  return useSyncExternalStore(subscribe, getAccessToken, () => null);
}
