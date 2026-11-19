# Radar HLK-LD2451 — ESP32 + interface Java

Projeto para configurar e monitorar o radar HLK-LD2451 a partir de um ESP32 DEV KIT V1,
com comunicação via WiFi (WebSocket) e uma interface JavaFX.

## Estrutura

```
radar-ld2451/
├── esp32-firmware/     firmware PlatformIO (ESP32)
│   ├── platformio.ini
│   ├── src/main.cpp
│   └── lib/HLK-LD245X/ biblioteca do radar (BSD-3, Fiooodooor / Miłosz Linkiewicz)
└── java-interface/     interface JavaFX (Maven)
    ├── pom.xml
    └── src/main/java/radar/
        ├── RadarClient.java
        └── Main.java
```

## Ligação

| Radar HLK-LD2451 | ESP32 DEV KIT V1 |
|------------------|------------------|
| VCC              | 5V (confirme no seu módulo) |
| GND              | GND              |
| TX               | GPIO16 (RX2)     |
| RX               | GPIO17 (TX2)     |

UART do radar: 115200 baud, 8N1.

## Firmware

1. Abra `esp32-firmware/` no VS Code com PlatformIO.
2. Em `src/main.cpp`, preencha `WIFI_SSID` e `WIFI_PASS`.
3. Compile e grave (`pio run -t upload`), depois abra o monitor serial e anote o IP.

## Interface Java

Requisitos: JDK 17 ou superior e Maven.

```
cd java-interface
mvn javafx:run
```

Informe o IP do ESP32 no campo superior e clique em **Conectar**.

## Protocolo WebSocket

Endpoint: `ws://IP_DO_ESP32/ws`

Leituras (ESP32 → Java), enviadas continuamente enquanto não estiver em configuração:

```json
{"targets":[{"id":0,"x":12,"y":-3,"velocity":-18,"distance":12,"angle":-14,"resolution":0,"snr":45,"valid":true}]}
```

Comandos (Java → ESP32):

- `{"cmd":"enter"}` e `{"cmd":"exit"}`
- `{"cmd":"firmware"}` e `{"cmd":"mac"}`
- `{"cmd":"bluetooth","on":true}`
- `{"cmd":"reboot"}` e `{"cmd":"factory_reset"}`

Respostas (ESP32 → Java):

```json
{"ack":"firmware","ok":true,"value":"..."}
```

Comandos de configuração (exceto `enter`, `exit`, e os que já saem do modo) exigem que a sessão esteja aberta, senão respondem `fora_de_configuracao`. A sessão fecha sozinha após 30 s sem comandos, e também quando o cliente WebSocket desconecta.

## Pontos a validar no hardware

- Unidade da velocidade (km/h ou m/s) — a biblioteca entrega o valor bruto.
- Significado do sinal da velocidade (aproximação ou afastamento).
- Campo `resolution` não é preenchido pelo LD2451 (fica em 0).
- Confirme a tensão de alimentação e o nível lógico da UART do seu módulo antes de ligar.
