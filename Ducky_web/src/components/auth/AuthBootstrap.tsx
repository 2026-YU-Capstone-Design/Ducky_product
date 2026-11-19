"use client";

import { useEffect } from "react";
import { clearAccessToken, getAccessToken } from "@/lib/api/client";
import { getMe } from "@/lib/api/auth";
import { clearPersistedUser, persistUser } from "@/lib/auth/storage";

export function AuthBootstrap() {
  useEffect(() => {
    let cancelled = false;

    async function syncUser() {
      const token = getAccessToken();
      if (!token) {
        return;
      }

      try {
        const user = await getMe();
        if (!cancelled && token === getAccessToken()) {
          persistUser(user);
        }
      } catch {
        if (!cancelled && token === getAccessToken()) {
          clearAccessToken();
          clearPersistedUser();
        }
      }
    }

    void syncUser();

    return () => {
      cancelled = true;
    };
  }, []);

  return null;
}
