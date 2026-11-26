import { ChatPage } from "@/components/chat/ChatPage";
import { AppShell } from "@/components/layout/AppShell";

export default async function Page({ searchParams }: {
  searchParams: Promise<{ conversationId?: string | string[] }>;
}) {
  const { conversationId: requested } = await searchParams;
  const conversationId = Array.isArray(requested) ? requested[0] : requested;

  return (
    <AppShell>
      <ChatPage key={conversationId} conversationId={conversationId} />
    </AppShell>
  );
}
