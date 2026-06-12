import { SessionHistory } from "@/components/dashboard/SessionHistory";
import { AppShell } from "@/components/layout/AppShell";

export default function SessionsPage() {
  return (
    <AppShell>
      <SessionHistory />
    </AppShell>
  );
}
