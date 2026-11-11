package com.pgoogol.npcworld.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {

    /** Maven runs tests with the module directory as the working directory. */
    private static final Path STAGE_1 = Path.of("..", "..", "config", "stage-1.yaml");

    @Test
    void readsTheStageOneFile() throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_1);

        assertThat(config.world().seed()).isEqualTo(42);
        assertThat(config.world().width()).isEqualTo(20);
        assertThat(config.world().npcs()).isEqualTo(5);
        assertThat(config.world().foodShare()).isEqualTo(0.08);
        assertThat(config.world().lifeLimit()).isEqualTo(2000);
        assertThat(config.world().rewards().death()).isEqualTo(-10.0);
        assertThat(config.brainAddress()).isEqualTo("localhost:50051");
        assertThat(config.statsWindow()).isEqualTo(1000);
        assertThat(config.ticksPerSecond()).isEqualTo(10.0);
    }

    @Test
    void stageOneFileMeansNoAgeing() throws Exception {
        AppConfig config = ConfigLoader.load(STAGE_1);

        assertThat(config.world().agingEnabled()).isFalse();
        assertThat(config.world().lifespanTicks()).isZero();
    }

    @Test
    void readsTheStageTwoAdditions() throws Exception {
        AppConfig config = ConfigLoader.load(Path.of("..", "..", "config", "stage-2.yaml"));

        assertThat(config.world().npcs()).isEqualTo(12);
        assertThat(config.world().agingEnabled()).isTrue();
        assertThat(config.world().lifespanTicks()).isEqualTo(60_000);
        assertThat(config.world().lifespanSpread()).isEqualTo(0.2);
        assertThat(config.world().agingStartTick()).isEqualTo(150_000);
    }

    @Test
    void namesTheMissingSection() {
        assertThatThrownBy(() -> ConfigLoader.parse(Map.of("seed", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("world");
    }
}
