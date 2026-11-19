#include <Arduino.h>
#include <WiFi.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <freertos/queue.h>
#include <LD2451.hpp>

using namespace esphome::ld245x;

const char* WIFI_SSID = "SUA_REDE";
const char* WIFI_PASS = "SUA_SENHA";
const int RADAR_RX = 16;
const int RADAR_TX = 17;
const unsigned long CONFIG_TIMEOUT_MS = 30000;

HardwareSerial radarSerial(2);
LD2451 radar;
AsyncWebServer server(80);
AsyncWebSocket ws("/ws");

struct Cmd { char json[192]; };
QueueHandle_t cmdQueue;
volatile bool inConfig = false;
unsigned long lastConfigMs = 0;

void sendAck(const char* name, bool ok, const char* value = nullptr) {
  JsonDocument doc;
  doc["ack"] = name;
  doc["ok"] = ok;
  if (value) doc["value"] = value;
  String out;
  serializeJson(doc, out);
  ws.textAll(out);
}

bool requireConfig(const char* name) {
  if (!inConfig) {
    sendAck(name, false, "fora_de_configuracao");
    return false;
  }
  return true;
}

void handleCommand(const char* json) {
  JsonDocument doc;
  if (deserializeJson(doc, json)) {
    sendAck("parse", false);
    return;
  }
  const char* cmd = doc["cmd"] | "";
  lastConfigMs = millis();

  if (!strcmp(cmd, "enter")) {
    bool ok = radar.beginConfigurationSession();
    inConfig = true;
    sendAck("enter", ok);

  } else if (!strcmp(cmd, "exit")) {
    if (!inConfig) {
      sendAck("exit", true, "ja_fora");
      return;
    }
    bool ok = radar.endConfigurationSession();
    inConfig = false;
    sendAck("exit", ok);

  } else if (!strcmp(cmd, "firmware")) {
    if (!requireConfig("firmware")) return;
    bool ok = radar.queryFirmwareVersion();
    sendAck("firmware", ok, radar.getFirmwareString());

  } else if (!strcmp(cmd, "mac")) {
    if (!requireConfig("mac")) return;
    bool ok = radar.queryMacAddress();
    sendAck("mac", ok, radar.getMacAddressString());

  } else if (!strcmp(cmd, "bluetooth")) {
    if (!requireConfig("bluetooth")) return;
    bool on = doc["on"] | false;
    sendAck("bluetooth", radar.setBluetoothEnabled(on));

  } else if (!strcmp(cmd, "reboot")) {
    if (!requireConfig("reboot")) return;
    bool ok = radar.rebootModule();
    inConfig = false;  // o radar reinicia e sai do modo de configuração
    sendAck("reboot", ok);

  } else if (!strcmp(cmd, "factory_reset")) {
    if (!requireConfig("factory_reset")) return;
    bool ok = radar.restoreFactorySetting();
    inConfig = false;
    sendAck("factory_reset", ok);

  } else {
    sendAck("unknown", false, cmd);
  }
}

void pushCmd(const char* json) {
  Cmd c;
  strlcpy(c.json, json, sizeof(c.json));
  xQueueSend(cmdQueue, &c, 0);
}

void onWsEvent(AsyncWebSocket*, AsyncWebSocketClient*, AwsEventType type,
               void* arg, uint8_t* data, size_t len) {
  if (type == WS_EVT_DISCONNECT) {
    // Se o cliente cair no meio da configuração, sai do modo de configuração.
    pushCmd("{\"cmd\":\"exit\"}");
    return;
  }
  if (type != WS_EVT_DATA) return;

  AwsFrameInfo* info = (AwsFrameInfo*)arg;
  if (info->opcode != WS_TEXT || !info->final || info->index != 0 || info->len != len) return;
  if (len >= sizeof(Cmd::json)) return;

  char tmp[sizeof(Cmd::json)];
  memcpy(tmp, data, len);
  tmp[len] = '\0';
  pushCmd(tmp);
}

void setup() {
  Serial.begin(115200);

  WiFi.begin(WIFI_SSID, WIFI_PASS);
  while (WiFi.status() != WL_CONNECTED) delay(300);
  Serial.println(WiFi.localIP());

  radarSerial.begin(LD2451_SERIAL_SPEED, SERIAL_8N1, RADAR_RX, RADAR_TX);
  radarSerial.setTimeout(1000);
  radar.begin(radarSerial);

  cmdQueue = xQueueCreate(8, sizeof(Cmd));
  ws.onEvent(onWsEvent);
  server.addHandler(&ws);
  server.begin();
}

void loop() {
  ws.cleanupClients();

  Cmd c;
  while (xQueueReceive(cmdQueue, &c, 0) == pdTRUE) {
    handleCommand(c.json);
  }

  if (inConfig && millis() - lastConfigMs > CONFIG_TIMEOUT_MS) {
    radar.endConfigurationSession();
    inConfig = false;
  }

  if (!inConfig && radar.update() && ws.count() > 0) {
    String json = "{\"targets\":[";
    uint8_t n = radar.getNrValidTargets();
    for (uint8_t i = 0; i < n; i++) {
      if (i) json += ",";
      json += radar.getTarget(i).toJson();
    }
    json += "]}";
    ws.textAll(json);
  }
}
