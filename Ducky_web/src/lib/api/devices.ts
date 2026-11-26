import { apiRequest } from "./client";

export const DEFAULT_RASPBERRY_SERIAL = "raspberry-duck-001";

export interface DeviceResponse {
  id: string;
  serialNumber: string;
  firmwareVersion: string | null;
  status: string;
}

export type DeviceCommandStatus = "PENDING" | "CLAIMED" | "COMPLETED" | "FAILED";

export interface DeviceCommandResponse {
  id: string;
  deviceId: string;
  deviceSerial: string;
  conversationId: number | null;
  commandType: "START_RECORDING";
  status: DeviceCommandStatus;
  createdAt: string | null;
  claimedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}

export function listDevices() {
  return apiRequest<DeviceResponse[]>("/api/devices", {
    method: "GET",
    auth: true,
  });
}

export function startRaspberryRecording(
  deviceId: string,
  conversationId: string,
) {
  return apiRequest<DeviceCommandResponse>(
    `/api/devices/${deviceId}/commands/start-recording`,
    {
      method: "POST",
      auth: true,
      signal: AbortSignal.timeout(12000),
      body: {
        conversationId: Number(conversationId),
      },
    },
  );
}

export function getLatestDeviceCommand(deviceId: string) {
  return apiRequest<DeviceCommandResponse | null>(
    `/api/devices/${deviceId}/commands/latest`,
    {
      method: "GET",
      auth: true,
      signal: AbortSignal.timeout(8000),
    },
  );
}
