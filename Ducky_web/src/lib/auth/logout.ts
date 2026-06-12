import { clearAccessToken } from "@/lib/api/client";
import { clearPersistedUser } from "@/lib/auth/storage";

export function clearAuthSession() {
  clearAccessToken();
  clearPersistedUser();
}
