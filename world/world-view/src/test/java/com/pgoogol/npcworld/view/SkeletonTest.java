package com.pgoogol.npcworld.view;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class SkeletonTest {

    @Test
    void javaFxIsOnTheClasspath() {
        assertThatCode(() -> Class.forName("javafx.scene.canvas.Canvas", false, getClass().getClassLoader()))
                .doesNotThrowAnyException();
    }
}
