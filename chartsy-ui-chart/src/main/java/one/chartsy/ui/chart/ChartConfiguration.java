/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.kernel.boot.config.FrontEndConfiguration;
import one.chartsy.ui.chart.type.CandlestickChart;
import one.chartsy.ui.chart.type.OHLC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(FrontEndConfiguration.class)
public class ChartConfiguration {

    @Bean(name = {"Candlestick Chart", "candlestickChart"})
    public Chart candlestickChart() {
        return new CandlestickChart();
    }

    @Bean(name = {"OHLC", "ohlcChart"})
    public Chart ohlcChart() {
        return new OHLC();
    }
}
