/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml;

@FunctionalInterface
public interface ClassifierFunction<I, O> {

    O classify(I data);
}
