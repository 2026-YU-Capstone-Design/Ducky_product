const uint8_t LED_IDLE_PIN = 1;      // D1
const uint8_t LED_LISTENING_PIN = 2; // D2
const uint8_t LED_RESPONDING_PIN = 3; // D3
const uint8_t LED_OFFLINE_PIN = 4;   // D4
const uint8_t BUTTON_PIN = 8;        // D8

const unsigned long DEBOUNCE_MS = 50;

String serialBuffer = "";
int lastStableButtonState = HIGH;
int lastReading = HIGH;
unsigned long lastDebounceAt = 0;

void setLedState(const String& state) {
  digitalWrite(LED_IDLE_PIN, state == "IDLE" ? HIGH : LOW);
  digitalWrite(LED_LISTENING_PIN, state == "LISTENING" ? HIGH : LOW);
  digitalWrite(LED_RESPONDING_PIN, state == "RESPONDING" ? HIGH : LOW);
  digitalWrite(LED_OFFLINE_PIN, state == "OFFLINE" ? HIGH : LOW);
}

void handleSerialLine(const String& line) {
  if (!line.startsWith("LED:")) {
    return;
  }

  String state = line.substring(4);
  state.trim();
  setLedState(state);
}

void setup() {
  pinMode(LED_IDLE_PIN, OUTPUT);
  pinMode(LED_LISTENING_PIN, OUTPUT);
  pinMode(LED_RESPONDING_PIN, OUTPUT);
  pinMode(LED_OFFLINE_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  Serial.begin(115200);
  setLedState("IDLE");
}

void loop() {
  while (Serial.available() > 0) {
    const char c = static_cast<char>(Serial.read());
    if (c == '\n') {
      handleSerialLine(serialBuffer);
      serialBuffer = "";
    } else if (c != '\r') {
      serialBuffer += c;
    }
  }

  const int reading = digitalRead(BUTTON_PIN);
  if (reading != lastReading) {
    lastDebounceAt = millis();
  }

  if ((millis() - lastDebounceAt) > DEBOUNCE_MS) {
    if (reading != lastStableButtonState) {
      lastStableButtonState = reading;
      if (lastStableButtonState == LOW) {
        Serial.println("BTN:PRESS");
      }
    }
  }

  lastReading = reading;
}
