package com.pgoogol.npcworld.view;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.Season;
import com.pgoogol.npcworld.core.WorldSnapshot;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Draws a world snapshot on a canvas. Reads the snapshot only. */
final class BoardPainter {

    /** Cell size on small boards (stages 1 and 2). */
    static final int CELL = 30;
    /** The board never grows beyond this many pixels: a 64x64 board gets smaller cells. */
    static final int MAX_BOARD_PIXELS = 900;

    private static final Color EMPTY = Color.web("#f3efe6");
    private static final Color EMPTY_AUTUMN = Color.web("#f1e6d0");
    private static final Color EMPTY_WINTER = Color.web("#e8eef5");
    private static final Color CARRIED_FOOD = Color.web("#9be07f");
    private static final Color CARRIED_WOOD = Color.web("#c79a63");
    private static final Color GRID = Color.web("#ddd6c8");
    private static final Color WALL = Color.web("#4a4a4a");
    private static final Color FOOD = Color.web("#5aa845");
    private static final Color WATER = Color.web("#3f8fd9");
    private static final Color LAVA = Color.web("#e2542b");
    private static final Color TREE = Color.web("#2f6b2a");
    private static final Color STUMP = Color.web("#8a6d4b");
    private static final Color FIELD_SOWN = Color.web("#c9b27c");
    private static final Color FIELD_RIPE = Color.web("#e0b43c");
    private static final Color SITE = Color.web("#b9a58c");
    private static final Color SHELTER = Color.web("#7a4f2a");
    private static final Color STORE = Color.web("#5a3a8a");
    private static final Color[] NPC_COLORS = {
        Color.web("#7b3fa0"), Color.web("#c2185b"), Color.web("#00796b"), Color.web("#5d4037"), Color.web("#283593"),
    };
    private static final Font ID_FONT = Font.font("System", FontWeight.BOLD, 12);
    private static final Font SMALL_ID_FONT = Font.font("System", FontWeight.BOLD, 9);
    private static final Color CONTACT = Color.web("#f2b600");
    private static final Color ELDER_RING = Color.web("#f2b600");

    /** Share of the lifespan below which an NPC is drawn as a newborn. */
    static final double NEWBORN_SHARE = 0.1;
    /** Share of the lifespan above which an NPC is drawn as an elder. */
    static final double ELDER_SHARE = 0.75;

    private BoardPainter() {
    }

    static Color npcColor(int id) {
        return NPC_COLORS[id % NPC_COLORS.length];
    }

    /** Pixels per cell for a board of the given size. */
    static int cellSize(int width, int height) {
        return Math.max(4, Math.min(CELL, MAX_BOARD_PIXELS / Math.max(width, height)));
    }

    /** Empty ground tells the season: warm in autumn, pale in winter. */
    static Color groundColor(Season season) {
        if (season == Season.WINTER) {
            return EMPTY_WINTER;
        }
        return season == Season.AUTUMN ? EMPTY_AUTUMN : EMPTY;
    }

    static void paint(GraphicsContext graphics, WorldSnapshot snapshot) {
        int cell = cellSize(snapshot.width(), snapshot.height());
        if (cell != CELL) {
            paintSmall(graphics, snapshot, cell);
            return;
        }
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                graphics.setFill(colorOf(snapshot.cellAt(x, y), snapshot.season()));
                graphics.fillRect(x * CELL, y * CELL, CELL, CELL);
                graphics.setStroke(GRID);
                graphics.strokeRect(x * CELL + 0.5, y * CELL + 0.5, CELL - 1, CELL - 1);
            }
        }

        paintContacts(graphics, snapshot);

        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        for (NpcSnapshot npc : snapshot.npcs()) {
            double ageShare = ageShare(npc);
            boolean newborn = npc.lifespan() > 0 && ageShare < NEWBORN_SHARE;
            boolean elder = ageShare >= ELDER_SHARE;
            double radius = CELL * (newborn ? 0.24 : 0.36);

            graphics.setFill(npcColor(npc.id()));
            graphics.fillOval(centreX(npc) - radius, centreY(npc) - radius, 2 * radius, 2 * radius);
            // Elders wear a gold ring: they are the ones worth standing next to.
            graphics.setStroke(elder ? ELDER_RING : Color.WHITE);
            graphics.setLineWidth(elder ? 3 : 1);
            graphics.strokeOval(centreX(npc) - radius, centreY(npc) - radius, 2 * radius, 2 * radius);
            graphics.setLineWidth(1);
            graphics.setFill(Color.WHITE);
            graphics.setFont(newborn ? SMALL_ID_FONT : ID_FONT);
            graphics.fillText(Integer.toString(npc.id()), centreX(npc), centreY(npc));
        }
    }

    /**
     * Stage 3: a big board with small cells. Buildings show what they hold (food in a store, wood on a
     * site), NPCs are dots with a mark for what they carry: green for food, brown for wood.
     */
    private static void paintSmall(GraphicsContext graphics, WorldSnapshot snapshot, int cell) {
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                graphics.setFill(colorOf(snapshot.cellAt(x, y), snapshot.season()));
                graphics.fillRect(x * cell, y * cell, cell, cell);
            }
        }
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        graphics.setFont(Font.font("System", FontWeight.BOLD, Math.max(7, cell * 0.6)));
        graphics.setFill(Color.WHITE);
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                CellType type = snapshot.cellAt(x, y);
                if (type == CellType.STORE || type == CellType.SITE_SHELTER || type == CellType.SITE_STORE) {
                    graphics.fillText(Integer.toString(snapshot.amountAt(x, y)), x * cell + cell / 2.0,
                            y * cell + cell / 2.0);
                }
            }
        }
        double radius = cell * 0.38;
        for (NpcSnapshot npc : snapshot.npcs()) {
            // NPCs often share a cell (a shelter holds four): a small offset per id keeps them apart.
            double angle = 2 * Math.PI * (npc.id() % 4) / 4;
            double x = npc.position().x() * cell + cell / 2.0 + 0.2 * cell * Math.cos(angle);
            double y = npc.position().y() * cell + cell / 2.0 + 0.2 * cell * Math.sin(angle);
            graphics.setFill(npcColor(npc.id()));
            graphics.fillOval(x - radius, y - radius, 2 * radius, 2 * radius);
            graphics.setStroke(Color.WHITE);
            graphics.strokeOval(x - radius, y - radius, 2 * radius, 2 * radius);
            if (npc.carriedFood() + npc.carriedWood() > 0) {
                graphics.setFill(npc.carriedWood() > npc.carriedFood() ? CARRIED_WOOD : CARRIED_FOOD);
                graphics.fillOval(x - radius / 2, y - radius / 2, radius, radius);
            }
        }
    }

    /** A line between every two NPCs that stand next to each other: knowledge passes along it. */
    private static void paintContacts(GraphicsContext graphics, WorldSnapshot snapshot) {
        graphics.setStroke(CONTACT);
        graphics.setLineWidth(3);
        for (NpcSnapshot first : snapshot.npcs()) {
            for (NpcSnapshot second : snapshot.npcs()) {
                if (first.id() < second.id() && areNeighbours(first, second)) {
                    graphics.strokeLine(centreX(first), centreY(first), centreX(second), centreY(second));
                }
            }
        }
        graphics.setLineWidth(1);
    }

    static boolean areNeighbours(NpcSnapshot first, NpcSnapshot second) {
        return Math.abs(first.position().x() - second.position().x()) <= 1
                && Math.abs(first.position().y() - second.position().y()) <= 1;
    }

    /** Age as a share of the lifespan; zero when nobody dies of old age. */
    static double ageShare(NpcSnapshot npc) {
        return npc.lifespan() > 0 ? (double) npc.totalAge() / npc.lifespan() : 0;
    }

    // NPCs may share a cell: a small offset per id keeps all of them visible.
    private static double centreX(NpcSnapshot npc) {
        double angle = 2 * Math.PI * (npc.id() % NPC_COLORS.length) / NPC_COLORS.length;
        return npc.position().x() * CELL + CELL / 2.0 + 4 * Math.cos(angle);
    }

    private static double centreY(NpcSnapshot npc) {
        double angle = 2 * Math.PI * (npc.id() % NPC_COLORS.length) / NPC_COLORS.length;
        return npc.position().y() * CELL + CELL / 2.0 + 4 * Math.sin(angle);
    }

    private static Color colorOf(CellType cell, Season season) {
        return switch (cell) {
            case EMPTY -> groundColor(season);
            case WALL -> WALL;
            case FOOD -> FOOD;
            case WATER -> WATER;
            case LAVA -> LAVA;
            case TREE -> TREE;
            case STUMP -> STUMP;
            case FIELD_SOWN -> FIELD_SOWN;
            case FIELD_RIPE -> FIELD_RIPE;
            case SITE_SHELTER, SITE_STORE -> SITE;
            case SHELTER -> SHELTER;
            case STORE -> STORE;
        };
    }
}
