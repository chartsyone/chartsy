/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.time;

import one.chartsy.misc.BinarySearch;

public abstract class AbstractTimeline implements Timeline {

    @Override
    public int getTimeLocation(long time) {
        if (getOrder().isReversed())
            return BinarySearch.binarySearchReversed(this::getTimeAt, 0, length(), time);
        else
            return BinarySearch.binarySearch(this::getTimeAt, 0, length(), time);
    }
}
