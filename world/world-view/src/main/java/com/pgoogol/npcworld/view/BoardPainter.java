package com.pgoogol.npcworld.view;

import com.pgoogol.npcworld.core.CellType;
import com.pgoogol.npcworld.core.NpcSnapshot;
import com.pgoogol.npcworld.core.WorldSnapshot;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Draws a world snapshot on a canvas. Reads the snapshot only. */
final class BoardPainter {

    static final int CELL = 30;

    private static final Color EMPTY = Color.web("#f3efe6");
    private static final Color GRID = Color.web("#ddd6c8");
    private static final Color WALL = Color.web("#4a4a4a");
    private static final Color FOOD = Color.web("#5aa845");
    private static final Color WATER = Color.web("#3f8fd9");
    private static final Color LAVA = Color.web("#e2542b");
    private static final Color[] NPC_COLORS = {
        Color.web("#7b3fa0"), Color.web("#c2185b"), Color.web("#00796b"), Color.web("#5d4037"), Color.web("#283593"),
    };
    private static final Font ID_FONT = Font.font("System", FontWeight.BOLD, 12);

    private BoardPainter() {
    }

    static Color npcColor(int id) {
        return NPC_COLORS[id % NPC_COLORS.length];
    }

    static void paint(GraphicsContext graphics, WorldSnapshot snapshot) {
        for (int y = 0; y < snapshot.height(); y++) {
            for (int x = 0; x < snapshot.width(); x++) {
                graphics.setFill(colorOf(snapshot.cellAt(x, y)));
                graphics.fillRect(x * CELL, y * CELL, CELL, CELL);
                graphics.setStroke(GRID);
                graphics.strokeRect(x * CELL + 0.5, y * CELL + 0.5, CELL - 1, CELL - 1);
            }
        }

        graphics.setFont(ID_FONT);
        graphics.setTextAlign(TextAlignment.CENTER);
        graphics.setTextBaseline(VPos.CENTER);
        for (NpcSnapshot npc : snapshot.npcs()) {
            // NPCs may share a cell: a small offset per id keeps all of them visible.
            double angle = 2 * Math.PI * (npc.id() % NPC_COLORS.length) / NPC_COLORS.length;
            double centreX = npc.position().x() * CELL + CELL / 2.0 + 4 * Math.cos(angle);
            double centreY = npc.position().y() * CELL + CELL / 2.0 + 4 * Math.sin(angle);
            double radius = CELL * 0.36;

            graphics.setFill(npcColor(npc.id()));
            graphics.fillOval(centreX - radius, centreY - radius, 2 * radius, 2 * radius);
            graphics.setStroke(Color.WHITE);
            graphics.strokeOval(centreX - radius, centreY - radius, 2 * radius, 2 * radius);
            graphics.setFill(Color.WHITE);
            graphics.fillText(Integer.toString(npc.id()), centreX, centreY);
        }
    }

    private static Color colorOf(CellType cell) {
        return switch (cell) {
            case EMPTY -> EMPTY;
            case WALL -> WALL;
            case FOOD -> FOOD;
            case WATER -> WATER;
            case LAVA -> LAVA;
        };
    }
}
