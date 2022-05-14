/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

public interface TypedKey<T> {

    Class<T> type();

    String name();
}
