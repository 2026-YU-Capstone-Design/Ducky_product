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

export async function ensureDefaultRaspberryLinked(userId: string) {
  const device = await apiRequest<DeviceResponse>("/api/devices", {
    method: "POST",
    body: {
      serial_number: DEFAULT_RASPBERRY_SERIAL,
      firmware_version: "raspberry-local",
    },
  });

  await apiRequest(`/api/devices/${device.id}/link`, {
    method: "POST",
    body: {
      user_id: userId,
      role: "OWNER",
    },
  });

  return device;
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
    },
  );
}
