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

    /** Stage 3: one point on the settlement chart every this many ticks. */
    private static final int SETTLEMENT_SAMPLE_TICKS = 25;

    private final XYChart.Series<Number, Number> populationSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> storedSeries = new XYChart.Series<>();
    private int seasonTicks;
    private long sampledTick = -1;

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

        // A world handed over by another program (a save loaded from the chronicle) goes first.
        World world = ShowMain.takePreloaded().orElseGet(() -> new World(config.world()));
        seasonTicks = config.world().seasons().seasonTicks();
        world.addListener(endedLives::add);
        brain = GrpcBrain.connect(config.brainAddress());
        runner = new SimulationRunner(new Simulation(world, brain), ticksPerSecond);

        WorldSnapshot first = runner.latest();
        int cell = BoardPainter.cellSize(first.width(), first.height());
        Canvas canvas = new Canvas(first.width() * cell, first.height() * cell);
        Label status = new Label();
        status.setFont(Font.font("Monospaced", 13));

        VBox side = seasonTicks > 0 ? new VBox(12, settlementChart(), status) : new VBox(12, lifeChart(), status);
        side.setPadding(new Insets(12));
        BorderPane root = new BorderPane(canvas);
        root.setRight(side);
        root.setPadding(new Insets(12));

        stage.setTitle(seasonTicks > 0 ? "NPC World: osada i pory roku" : "NPC World: mała osada");
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
            sampleSettlement(snapshot);
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
        chart.setPrefSize(460, 260);
        chart.getData().add(series);
        return chart;
    }

    /** Stage 3: the population and the stores over time, the yearly cycle in one picture. */
    private LineChart<Number, Number> settlementChart() {
        NumberAxis ticks = new NumberAxis();
        ticks.setLabel("Tura");
        ticks.setForceZeroInRange(false);
        NumberAxis count = new NumberAxis();
        LineChart<Number, Number> chart = new LineChart<>(ticks, count);
        chart.setTitle("Osada w czasie");
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefSize(460, 260);
        populationSeries.setName("Mieszkańcy");
        storedSeries.setName("Zapas w magazynach (dziesiątki porcji)");
        chart.getData().add(populationSeries);
        chart.getData().add(storedSeries);
        return chart;
    }

    private void sampleSettlement(WorldSnapshot snapshot) {
        if (seasonTicks == 0 || snapshot.tick() / SETTLEMENT_SAMPLE_TICKS == sampledTick / SETTLEMENT_SAMPLE_TICKS
                && sampledTick >= 0) {
            return;
        }
        sampledTick = snapshot.tick();
        SettlementSummary summary = SettlementSummary.of(snapshot);
        populationSeries.getData().add(new XYChart.Data<>(snapshot.tick(), summary.population()));
        storedSeries.getData().add(new XYChart.Data<>(snapshot.tick(), summary.storedFood() / 10.0));
        if (populationSeries.getData().size() > MAX_CHART_POINTS) {
            populationSeries.getData().removeFirst();
            storedSeries.getData().removeFirst();
        }
    }

    /** Stage 3: the calendar, the settlement in numbers and what every NPC carries. */
    static String describeSettlement(WorldSnapshot snapshot, int seasonTicks) {
        SettlementSummary summary = SettlementSummary.of(snapshot);
        StringBuilder text = new StringBuilder("Tura " + snapshot.tick() + "\n");
        text.append(SettlementSummary.calendar(snapshot, seasonTicks)).append("\n\n");
        text.append(String.format("Mieszkańcy: %d (w schronieniach: %d)%n", summary.population(), summary.inShelters()));
        text.append(String.format("Zapas w magazynach: %d porcji%n", summary.storedFood()));
        text.append(String.format("Schronienia: %d, magazyny: %d, place budowy: %d%n",
                summary.shelters(), summary.stores(), summary.sites()));
        text.append(String.format("Pola zasiane: %d, dojrzałe: %d%n", summary.fieldsSown(), summary.fieldsRipe()));
        text.append(String.format("Niesione: %d porcji jedzenia, %d drewna%n%n",
                summary.carriedFood(), summary.carriedWood()));
        text.append("Postać  Głód  Pragn.  Energia  Jedz.  Drewno  Wiek\n");
        for (NpcSnapshot npc : snapshot.npcs()) {
            text.append(String.format("%-6d  %4d  %6d  %7d  %5d  %6d  %3.0f%%%n", npc.id(), npc.hunger(), npc.thirst(),
                    npc.energy(), npc.carriedFood(), npc.carriedWood(), 100 * BoardPainter.ageShare(npc)));
        }
        text.append("\nZielone: jedzenie, ciemnozielone: drzewo, niebieskie: woda,\n"
                + "czerwone: lawa, żółte: dojrzałe pole, beżowe: zasiane pole,\n"
                + "brązowe: schronienie, fioletowe: magazyn (liczba: zapas),\n"
                + "jasnobrązowe: plac budowy (liczba: drewno).\n"
                + "Kropka w postaci: niesie jedzenie (zielona)\n"
                + "albo drewno (brązowa).");
        return text.toString();
    }

    private String describe(WorldSnapshot snapshot) {
        return seasonTicks > 0 && snapshot.season() != null
                ? describeSettlement(snapshot, seasonTicks) : describeLives(snapshot);
    }

    private static String describeLives(WorldSnapshot snapshot) {
        boolean ageing = snapshot.npcs().stream().anyMatch(npc -> npc.lifespan() > 0);
        StringBuilder text = new StringBuilder("Tura " + snapshot.tick() + "\n\n");
        text.append("Postać  Głód  Pragn.  Energia  Bez upadku");
        text.append(ageing ? "  Pokolenie  Wiek\n" : "\n");
        for (NpcSnapshot npc : snapshot.npcs()) {
            text.append(String.format("%-6d  %4d  %6d  %7d  %10d",
                    npc.id(), npc.hunger(), npc.thirst(), npc.energy(), npc.age()));
            if (ageing) {
                text.append(String.format("  %9d  %3.0f%%", npc.generation(), 100 * BoardPainter.ageShare(npc)));
            }
            text.append('\n');
        }
        text.append("\nZielone: jedzenie, niebieskie: woda,\nczerwone: lawa, szare: ściana");
        if (ageing) {
            text.append("\nMałe kółko: noworodek, złota obwódka: starszy,\nżółta linia: sąsiedzi wymieniają wiedzę");
        }
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
