/* Copyright 2022 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;

import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.ui.chart.BasicStrokes;
import org.openide.util.lookup.ServiceProvider;

import one.chartsy.ui.chart.AbstractOverlay;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.plot.LinePlot;

/**
 * The relative performance moving average.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = Overlay.class)
public class FRAMATrailing extends AbstractOverlay {
    
    public FRAMATrailing() {
        super("FRAMA, Trailing");
    }
    
    @Override
    public String getLabel() {
        return "FRAMA, Trailing";
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            DoubleSeries smudge = FinancialIndicators.trailingFrama(quotes);
            addPlot("frama", new LinePlot(smudge, color, stroke));
        }
    }
    
    @Parameter(name = "Color")
    public Color color = new Color(0, 204, 204);
    @Parameter(name = "Stroke")
    public Stroke stroke = BasicStrokes.DEFAULT;
    
}
