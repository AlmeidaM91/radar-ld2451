package radar;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class Main extends Application {

    public record Target(int id, int x, int y, int velocity, int distance, int angle, int snr) {}

    private static final int MAX_POINTS = 300;

    private final ObservableList<Target> rows = FXCollections.observableArrayList();
    private final TableView<Target> table = new TableView<>(rows);

    private final NumberAxis xAxis = new NumberAxis();
    private final NumberAxis yAxis = new NumberAxis();
    private final LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
    private final XYChart.Series<Number, Number> speedSeries = new XYChart.Series<>();

    private final TextField hostField = new TextField("192.168.0.50");
    private final Label status = new Label("desconectado");
    private final Label ackLabel = new Label("");
    private final long t0 = System.currentTimeMillis();
    private RadarClient client;

    @Override
    public void start(Stage stage) {
        table.getColumns().addAll(
            col("ID", Target::id),
            col("X (m)", Target::x),
            col("Y (m)", Target::y),
            col("Velocidade", Target::velocity),
            col("Distância", Target::distance),
            col("Ângulo", Target::angle),
            col("SNR", Target::snr)
        );

        xAxis.setLabel("tempo (s)");
        yAxis.setLabel("velocidade");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.getData().add(speedSeries);
        chart.setPrefHeight(260);

        HBox top = new HBox(8, new Label("IP do ESP32:"), hostField,
                btn("Conectar", this::connect), status);
        top.setPadding(new Insets(8));
        top.setAlignment(Pos.CENTER_LEFT);

        VBox config = new VBox(8,
                new Label("Configuração"),
                btn("Entrar em configuração", () -> withClient(RadarClient::enterConfig)),
                btn("Sair da configuração", () -> withClient(RadarClient::exitConfig)),
                btn("Ler firmware", () -> withClient(RadarClient::queryFirmware)),
                btn("Ler MAC", () -> withClient(RadarClient::queryMac)),
                btn("Bluetooth ligado", () -> withClient(c -> c.setBluetooth(true))),
                btn("Bluetooth desligado", () -> withClient(c -> c.setBluetooth(false))),
                btn("Reiniciar radar", () -> withClient(RadarClient::reboot)),
                btn("Restaurar fábrica", () -> withClient(RadarClient::factoryReset)),
                ackLabel);
        config.setPadding(new Insets(8));
        config.setPrefWidth(200);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setRight(config);
        root.setCenter(table);
        root.setBottom(chart);

        stage.setTitle("Radar HLK-LD2451");
        stage.setScene(new Scene(root, 1100, 750));
        stage.show();
    }

    private void connect() {
        if (client != null) client.disconnect();
        client = new RadarClient(hostField.getText().trim());
        client.setOnMessage(msg -> Platform.runLater(() -> handle(msg)));
        client.setOnStatus(s -> Platform.runLater(() -> status.setText(s)));
        client.connect();
    }

    private void withClient(Consumer<RadarClient> action) {
        if (client != null) action.accept(client);
    }

    private void handle(JsonNode msg) {
        if (msg.has("targets")) {
            List<Target> list = new ArrayList<>();
            Target fastest = null;
            for (JsonNode t : msg.get("targets")) {
                Target tg = new Target(
                        t.path("id").asInt(), t.path("x").asInt(), t.path("y").asInt(),
                        t.path("velocity").asInt(), t.path("distance").asInt(),
                        t.path("angle").asInt(), t.path("snr").asInt());
                list.add(tg);
                if (fastest == null || Math.abs(tg.velocity()) > Math.abs(fastest.velocity())) {
                    fastest = tg;
                }
            }
            rows.setAll(list);
            if (fastest != null) addSpeedPoint(fastest.velocity());

        } else if (msg.has("ack")) {
            boolean ok = msg.path("ok").asBoolean();
            String value = msg.path("value").asText("");
            ackLabel.setText(msg.path("ack").asText() + (ok ? ": OK " : ": FALHOU ") + value);
        }
    }

    private void addSpeedPoint(int velocity) {
        double t = (System.currentTimeMillis() - t0) / 1000.0;
        speedSeries.getData().add(new XYChart.Data<>(t, velocity));
        if (speedSeries.getData().size() > MAX_POINTS) {
            speedSeries.getData().remove(0);
        }
    }

    private Button btn(String text, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static TableColumn<Target, Integer> col(String title, Function<Target, Integer> f) {
        TableColumn<Target, Integer> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(f.apply(d.getValue())));
        return c;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
