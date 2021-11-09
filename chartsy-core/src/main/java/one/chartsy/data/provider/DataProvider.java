package one.chartsy.data.provider;

import one.chartsy.FinancialService;
import one.chartsy.SymbolGroup;
import one.chartsy.SymbolIdentity;
import one.chartsy.data.DataQuery;
import one.chartsy.data.batch.Batch;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.List;

public interface DataProvider extends FinancialService {
    @Override
    default Lookup getLookup() {
        return Lookup.EMPTY;
    }

    default Collection<SymbolGroup> getAvailableGroups() {
        return List.of(SymbolGroup.BASE);
    }

    default List<SymbolIdentity> getSymbols() {
        return getSymbols(SymbolGroup.BASE);
    }

    List<SymbolIdentity> getSymbols(SymbolGroup group);

    <T extends Chronological> Batch<T> queryInBatches(Class<T> type, DataQuery<T> request);

    // TODO - to be continued...
}
