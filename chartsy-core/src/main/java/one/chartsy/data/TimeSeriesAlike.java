/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.time.Timeline;

public interface TimeSeriesAlike {

    Timeline getTimeline();

    int length();
}
