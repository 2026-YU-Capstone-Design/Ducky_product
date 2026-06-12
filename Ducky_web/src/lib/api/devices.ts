import { apiRequest } from "./client";

const DEFAULT_RASPBERRY_SERIAL = "raspberry-duck-001";

interface DeviceResponse {
  id: string;
  serialNumber: string;
  firmwareVersion: string | null;
  status: string;
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
}
