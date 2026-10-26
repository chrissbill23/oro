package com.asata.org.oro.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** the pools follow the CPUs as measured, within what was measured; a size given wins */
class PoolSizesTest {

    @Test
    void fromTheCpus() {
        assertThat(PoolSizes.api(0, 1)).isEqualTo(10);
        assertThat(PoolSizes.api(0, 2)).isEqualTo(10);
        assertThat(PoolSizes.api(0, 3)).isEqualTo(15);
        assertThat(PoolSizes.api(0, 4)).isEqualTo(20);
        assertThat(PoolSizes.api(0, 16)).isEqualTo(20);
        assertThat(PoolSizes.system(0, 2)).isEqualTo(5);
        assertThat(PoolSizes.system(0, 3)).isEqualTo(8);
        assertThat(PoolSizes.system(0, 4)).isEqualTo(10);
        assertThat(PoolSizes.system(0, 16)).isEqualTo(10);
    }

    @Test
    void aSizeGivenWins() {
        assertThat(PoolSizes.api(30, 2)).isEqualTo(30);
        assertThat(PoolSizes.system(3, 16)).isEqualTo(3);
    }
}
