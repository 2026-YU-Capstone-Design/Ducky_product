import type { DeviceCommandResponse } from "./devices";

export function isRecordingCommandActive(command: DeviceCommandResponse | null) {
  return command?.status === "PENDING" || command?.status === "CLAIMED";
}

// Poll requests can finish after a newer start response or a later poll. Never
// let an old receipt hide an active command or move a completed command backwards.
export function reconcileRecordingCommand(
  current: DeviceCommandResponse | null,
  incoming: DeviceCommandResponse | null,
): DeviceCommandResponse | null {
  if (!current) return incoming;
  if (!incoming || BigInt(incoming.id) < BigInt(current.id)) return current;
  if (incoming.id === current.id) {
    if (!isRecordingCommandActive(current)) return current;
    if (current.status === "CLAIMED" && incoming.status === "PENDING") return current;
  }
  return incoming;
}

export function shouldRefreshRecordingMessages(
  previous: DeviceCommandResponse | null,
  current: DeviceCommandResponse | null,
  conversationId: string,
) {
  if (!current || String(current.conversationId) !== conversationId) return false;
  if (current.status === "CLAIMED") return true;
  return (current.status === "COMPLETED" || current.status === "FAILED") &&
    (previous?.id !== current.id || previous.status !== current.status);
}
