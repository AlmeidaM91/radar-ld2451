package radar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class RadarClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI uri;
    private volatile WebSocket socket;
    private Consumer<JsonNode> onMessage = m -> {};
    private Consumer<String> onStatus = s -> {};

    public RadarClient(String host) {
        this.uri = URI.create("ws://" + host + "/ws");
    }

    public void setOnMessage(Consumer<JsonNode> c) { this.onMessage = c; }
    public void setOnStatus(Consumer<String> c) { this.onStatus = c; }

    public void connect() {
        HTTP.newWebSocketBuilder()
            .buildAsync(uri, new Listener())
            .thenAccept(ws -> {
                socket = ws;
                onStatus.accept("conectado a " + uri);
            })
            .exceptionally(e -> {
                onStatus.accept("erro ao conectar: " + e.getMessage());
                return null;
            });
    }

    public void disconnect() {
        WebSocket ws = socket;
        if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    private void send(String json) {
        WebSocket ws = socket;
        if (ws != null) ws.sendText(json, true);
    }

    public void enterConfig()   { send("{\"cmd\":\"enter\"}"); }
    public void exitConfig()    { send("{\"cmd\":\"exit\"}"); }
    public void queryFirmware() { send("{\"cmd\":\"firmware\"}"); }
    public void queryMac()      { send("{\"cmd\":\"mac\"}"); }
    public void reboot()        { send("{\"cmd\":\"reboot\"}"); }
    public void factoryReset()  { send("{\"cmd\":\"factory_reset\"}"); }
    public void setBluetooth(boolean on) {
        send("{\"cmd\":\"bluetooth\",\"on\":" + on + "}");
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                try {
                    onMessage.accept(mapper.readTree(text));
                } catch (Exception e) {
                    onStatus.accept("JSON inválido: " + e.getMessage());
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            onStatus.accept("desconectado (" + code + ")");
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            onStatus.accept("erro: " + error.getMessage());
        }
    }
}
