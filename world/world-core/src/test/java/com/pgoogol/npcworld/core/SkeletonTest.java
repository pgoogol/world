package com.pgoogol.npcworld.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkeletonTest {

    @Test
    void buildRunsOnJava25OrNewer() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
