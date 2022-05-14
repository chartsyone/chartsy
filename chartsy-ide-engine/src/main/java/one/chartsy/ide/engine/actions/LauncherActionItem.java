/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ide.engine.actions;

public interface LauncherActionItem {

    String getDisplayName();

    void repeatExecution();
}
