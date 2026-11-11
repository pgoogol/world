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
    void namesTheMissingSection() {
        assertThatThrownBy(() -> ConfigLoader.parse(Map.of("seed", 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("world");
    }
}
