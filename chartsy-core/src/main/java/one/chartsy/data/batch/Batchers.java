package one.chartsy.data.batch;

import one.chartsy.data.DataQuery;
import one.chartsy.time.Chronological;

import java.util.function.UnaryOperator;

/**
 * A static collection of frequently used {@link Batcher}'s.
 *
 * @author Mariusz Bernacki
 */
public class Batchers {

    public static <T extends Chronological> Batcher<T> fetching(DataQuery<T> query, UnaryOperator<Batch<T>> next) {
        return new Batcher<T>(query) {
            @Override
            public Batch<T> getNext(Batch<T> prev) {
                return next.apply(prev);
            }
        };
    }

    public static class StandaloneQueryBatcher<T extends Chronological> extends Batcher<T> {

        public StandaloneQueryBatcher(DataQuery<T> query) {
            super(query);
        }

        @Override
        public boolean hasNext(Batch<T> prevBatch) {
            return false;
        }

        @Override
        public Batch<T> getNext(Batch<T> prevBatch) {
            return null;
        }
    }
}
