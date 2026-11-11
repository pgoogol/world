package com.pgoogol.npcworld.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RollingMeanTest {

    @Test
    void averagesOnlyTheMostRecentValues() {
        RollingMean mean = new RollingMean(3);

        assertThat(mean.add(10)).isEqualTo(10.0);
        assertThat(mean.add(20)).isEqualTo(15.0);
        assertThat(mean.add(30)).isEqualTo(20.0);
        assertThat(mean.add(100)).isEqualTo(50.0);
    }
}
