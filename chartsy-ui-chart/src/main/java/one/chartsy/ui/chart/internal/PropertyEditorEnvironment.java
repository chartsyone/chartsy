/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import java.beans.PropertyEditor;

public interface PropertyEditorEnvironment {
    PropertyEditor createTransparencyPropertyEditor();
}
