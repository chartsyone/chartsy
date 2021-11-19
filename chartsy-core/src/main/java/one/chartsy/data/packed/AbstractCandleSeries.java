package one.chartsy.data.packed;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.Dataset;

import java.util.function.ToDoubleFunction;

public abstract class AbstractCandleSeries<T extends Candle, DS extends AbstractDoubleSeries<DS>> extends PackedSeries<T> {

    public AbstractCandleSeries(SymbolResource<T> resource, Dataset<T> dataset) {
        super(resource, dataset);
    }

    public DS highs() {
        return mapToDouble(Candle::high);
    }

    public DS closes() {
        return mapToDouble(Candle::close);
    }

    public DS volumes() {
        return mapToDouble(Candle::volume);
    }

    @Override
    public DS mapToDouble(ToDoubleFunction<T> mapper) {
        return (DS) super.mapToDouble(mapper);
    }

    public abstract DS trueRange();

    public DS atr(int periods) {
        return trueRange().wilders(periods);
    }

    public DS atrp(int periods) {
        return trueRange().div(closes()).wilders(periods).mul(100.0);
    }
}
