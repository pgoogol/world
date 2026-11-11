package com.pgoogol.npcworld.view;

import com.pgoogol.npcworld.app.AppConfig;
import com.pgoogol.npcworld.app.Args;
import com.pgoogol.npcworld.app.ConfigLoader;
import com.pgoogol.npcworld.app.SimulationRunner;
import com.pgoogol.npcworld.brain.GrpcBrain;
import com.pgoogol.npcworld.core.LifeEnded;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Simulation;
import com.pgoogol.npcworld.core.World;
import com.pgoogol.npcworld.core.WorldSnapshot;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Show mode window: the board on the left, the life length chart and NPC stats on the right.
 * The simulation runs on its own thread; this class only draws the latest snapshot.
 */
public final class ShowApp extends Application {

    private static final int ROLLING_LIVES = 20;
    private static final int MAX_CHART_POINTS = 400;

    private final Queue<LifeEnded> endedLives = new ConcurrentLinkedQueue<>();
    private final RollingMean rollingMean = new RollingMean(ROLLING_LIVES);
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();

    private GrpcBrain brain;
    private SimulationRunner runner;
    private long paintedTick = -1;
    private Path snapshotFile;
    private long snapshotTick;

    @Override
    public void start(Stage stage) throws Exception {
        Args args = new Args(getParameters().getRaw().toArray(String[]::new));
        AppConfig config = ConfigLoader.load(Path.of(args.get("config").orElse("config/stage-1.yaml")));
        double ticksPerSecond = args.get("tps").map(Double::parseDouble).orElse(config.ticksPerSecond());

        snapshotFile = args.get("snapshot").map(Path::of).orElse(null);
        snapshotTick = args.get("snapshot-tick").map(Long::parseLong).orElse(300L);

        World world = new World(config.world());
        world.addListener(endedLives::add);
        brain = GrpcBrain.connect(config.brainAddress());
        runner = new SimulationRunner(new Simulation(world, brain), ticksPerSecond);

        WorldSnapshot first = runner.latest();
        Canvas canvas = new Canvas(first.width() * BoardPainter.CELL, first.height() * BoardPainter.CELL);
        Label status = new Label();
        status.setFont(Font.font("Monospaced", 13));

        VBox side = new VBox(12, lifeChart(), status);
        side.setPadding(new Insets(12));
        BorderPane root = new BorderPane(canvas);
        root.setRight(side);
        root.setPadding(new Insets(12));

        stage.setTitle("NPC World: etap 1, mała osada");
        stage.setScene(new Scene(root));
        stage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                refresh(canvas, status);
                saveSnapshotIfDue(stage.getScene());
            }
        }.start();
        runner.start();
    }

    private void refresh(Canvas canvas, Label status) {
        LifeEnded ended;
        while ((ended = endedLives.poll()) != null) {
            series.getData().add(new XYChart.Data<>(ended.tick(), rollingMean.add(ended.lifeLength())));
            if (series.getData().size() > MAX_CHART_POINTS) {
                series.getData().removeFirst();
            }
        }

        WorldSnapshot snapshot = runner.latest();
        if (snapshot.tick() != paintedTick) {
            paintedTick = snapshot.tick();
            BoardPainter.paint(canvas.getGraphicsContext2D(), snapshot);
            status.setText(describe(snapshot));
        }
        runner.failure().ifPresent(failure ->
                status.setText(describe(snapshot) + "\nSymulacja zatrzymana: " + failure.getMessage()));
    }

    /** With {@code --snapshot <file.png>}: saves the window once, when the clock reaches {@code --snapshot-tick}. */
    private void saveSnapshotIfDue(Scene scene) {
        if (snapshotFile == null || paintedTick < snapshotTick) {
            return;
        }
        Path file = snapshotFile;
        snapshotFile = null;
        try {
            PngWriter.write(scene.snapshot(null), file);
            System.out.println("Snapshot saved: " + file.toAbsolutePath());
        } catch (java.io.IOException e) {
            System.err.println("Could not save snapshot: " + e.getMessage());
        }
    }

    private LineChart<Number, Number> lifeChart() {
        NumberAxis ticks = new NumberAxis();
        ticks.setLabel("Tura");
        ticks.setForceZeroInRange(false);
        NumberAxis length = new NumberAxis();
        length.setLabel("Tury życia");
        LineChart<Number, Number> chart = new LineChart<>(ticks, length);
        chart.setTitle("Średnia długość życia (ostatnie " + ROLLING_LIVES + " żyć)");
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefSize(460, 360);
        chart.getData().add(series);
        return chart;
    }

    private static String describe(WorldSnapshot snapshot) {
        StringBuilder text = new StringBuilder("Tura " + snapshot.tick() + "\n\n");
        text.append("Postać  Głód  Pragnienie  Energia  Wiek\n");
        for (NpcSnapshot npc : snapshot.npcs()) {
            text.append(String.format("%-6d  %4d  %10d  %7d  %4d%n",
                    npc.id(), npc.hunger(), npc.thirst(), npc.energy(), npc.age()));
        }
        text.append("\nZielone: jedzenie, niebieskie: woda,\nczerwone: lawa, szare: ściana");
        return text.toString();
    }

    @Override
    public void stop() throws Exception {
        if (runner != null) {
            runner.close();
        }
        if (brain != null) {
            brain.close();
        }
    }
}
