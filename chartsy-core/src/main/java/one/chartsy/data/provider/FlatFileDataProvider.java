package one.chartsy.data.provider;

import one.chartsy.*;
import one.chartsy.concurrent.AbstractCompletableRunnable;
import one.chartsy.core.ResourceHandle;
import one.chartsy.core.ThrowingRunnable;
import one.chartsy.data.DataQuery;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.UnsupportedDataQueryException;
import one.chartsy.data.batch.Batch;
import one.chartsy.data.batch.Batchers;
import one.chartsy.data.batch.SimpleBatch;
import one.chartsy.data.provider.file.*;
import one.chartsy.misc.ManagedReference;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FlatFileDataProvider extends AbstractDataProvider implements AutoCloseable, SymbolListAccessor, SymbolProposalProvider, HierarchicalConfiguration {
    private final Lookup lookup = Lookups.singleton(this);
    private final FlatFileFormat fileFormat;
    private final ResourceHandle<FileSystem> fileSystem;
    private final Iterable<Path> baseDirectories;
    private final ExecutionContext context;

    public FlatFileDataProvider(FlatFileFormat fileFormat, Path archiveFile) throws IOException {
        this(fileFormat, FileSystemCache.getGlobal().getFileSystem(archiveFile, Map.of()), fileName(archiveFile));
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, FileSystem fileSystem, String name) throws IOException {
        this(fileFormat, ResourceHandle.of(fileSystem), name);
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, ResourceHandle<FileSystem> fileSystem, String name) throws IOException {
        this(fileFormat, fileSystem, name, fileSystem.get().getRootDirectories());
    }

    public FlatFileDataProvider(FlatFileFormat fileFormat, ResourceHandle<FileSystem> fileSystem, String name, Iterable<Path> baseDirectories) throws IOException {
        super(Objects.requireNonNull(name, "name"));
        this.fileFormat = Objects.requireNonNull(fileFormat, "fileFormat");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
        this.baseDirectories = Objects.requireNonNull(baseDirectories, "baseDirectories");
        this.context = new ExecutionContext();
        registerCleaner();
    }

    protected void registerCleaner() {
        if (isCloseable(fileSystem)) {
            var fsObj = fileSystem.get();
            Cached.get(Cleaner.class, Cleaner::create).register(this, ThrowingRunnable.unchecked(fsObj::close));
        }
    }

    protected static boolean isCloseable(ResourceHandle<FileSystem> ref) {
        return !(ref instanceof ManagedReference<FileSystem>) && ref.get() != FileSystems.getDefault();
    }

    private static String fileName(Path file) {
        return file.getFileName().toString();
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    @Override
    public List<SymbolGroup> getRootGroups() {
        return asGroups(getBaseDirectories());
    }

    @Override
    public List<SymbolGroup> getSubGroups(SymbolGroup parent) {
        try {
            return asGroups(Files.newDirectoryStream(asPath(parent), Files::isDirectory));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getSimpleName(SymbolGroup group) {
        return Paths.get(group.name()).getFileName().toString();
    }

    @Override
    public List<? extends SymbolIdentity> listSymbols(SymbolGroup group) {
        try {
            return asIdentifiers(Files.newDirectoryStream(asPath(group), Files::isRegularFile));
        } catch (IOException e) {
            throw new DataProviderException("I/O error occurred", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Chronological> Batch<T> queryForBatches(Class<T> type, DataQuery<T> request) {
        if (type == Candle.class || type == SimpleCandle.class)
            return (Batch<T>) queryForCandles((DataQuery<Candle>) request);
        else
            throw new UnsupportedDataQueryException(request, String.format("DataType `%s` not supported", type.getSimpleName()));
    }

    public <T extends Candle> CompletableFuture<Void> queryForCandles(DataQuery<T> request, Consumer<Batch<T>> consumer, Executor executor) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException(String.format("Symbol '%s' not found", identifier));

        var itemReader = new FlatFileItemReader<T>();
        itemReader.setLineMapper((LineMapper<T>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));

        var work = new FlatFileItemReaderWork<>(itemReader, consumer, request);
        executor.execute(work);
        return work.getFuture();
    }

    protected static class FlatFileItemReaderWork<T extends Chronological> extends AbstractCompletableRunnable<Void> {
        private final FlatFileItemReader<T> itemReader;
        private final Consumer<Batch<T>> consumer;
        private final DataQuery<T> request;

        public FlatFileItemReaderWork(FlatFileItemReader<T> itemReader, Consumer<Batch<T>> consumer, DataQuery<T> request) {
            this.itemReader = itemReader;
            this.consumer = consumer;
            this.request = request;
        }

        @Override
        public void run(CompletableFuture<Void> future) {
            try {
                itemReader.open();
                List<T> items = itemReader.readAll();
                //items.sort(Comparator.naturalOrder());
                int itemCount = items.size();
                int itemLimit = request.limit();
                if (itemLimit > 0 && itemLimit < itemCount)
                    items = items.subList(itemCount - itemLimit, itemCount);
                consumer.accept(createItemsBatch(items));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                itemReader.close();
            }
        }

        protected Batch<T> createItemsBatch(List<T> items) {
            return new SimpleBatch<>(new Batchers.StandaloneQueryBatcher<>(request), Chronological.Order.CHRONOLOGICAL, 0L, items);
        }
    }

    public <T extends Candle> Batch<T> queryForCandles(DataQuery<T> request) {
        SymbolIdentifier identifier = new SymbolIdentifier(request.resource().symbol());
        Path file = getFileTreeMetadata().availableSymbols.get(identifier);
        if (file == null)
            throw new DataProviderException(String.format("Symbol '%s' not found", identifier));

        FlatFileItemReader<T> itemReader = new FlatFileItemReader<>();
        itemReader.setLineMapper((LineMapper<T>) fileFormat.getLineMapper().createLineMapper(context));
        itemReader.setLinesToSkip(fileFormat.getSkipFirstLines());
        itemReader.setInputStreamSource(() -> Files.newInputStream(file));

        try {
            itemReader.open();
            List<T> items = itemReader.readAll();
            //items.sort(Comparator.naturalOrder());
            if (request.endTime() != null) {
                long endTime = Chronological.toEpochMicros(request.endTime());
                items.removeIf(item -> item.getTime() > endTime);
            }

            int itemCount = items.size();
            int itemLimit = request.limit();
            if (itemLimit > 0 && itemLimit < itemCount)
                items = items.subList(itemCount - itemLimit, itemCount);
            return new SimpleBatch<>(new Batchers.StandaloneQueryBatcher<>(request), Chronological.Order.CHRONOLOGICAL, 0L, items);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            itemReader.close();
        }
    }

    public final FileSystem getFileSystem() {
        return fileSystem.get();
    }

    public final FlatFileFormat getFileFormat() {
        return fileFormat;
    }

    public final Iterable<Path> getBaseDirectories() {
        return baseDirectories;
    }

    @Override
    public void close() throws IOException {
        if (isCloseable(fileSystem))
            getFileSystem().close();
    }

    protected SymbolIdentity asIdentifier(Path path) {
        return new SymbolIdentifier(asAssetName(path.getFileName()), asAssetType(path));
    }

    protected String asAssetName(Path fileName) {
        String name = fileName.toString();
        int lastDot = name.lastIndexOf('.');
        name = (lastDot > 0)? name.substring(0, lastDot): name;
        return getFileFormat().isCaseSensitiveSymbols()? name : name.toUpperCase();
    }

    protected AssetType asAssetType(Path path) {
        return AssetTypes.GENERIC;
    }

    protected List<SymbolIdentity> asIdentifiers(Iterable<Path> paths) {
        List<SymbolIdentity> symbols = new ArrayList<>();
        for (Path dir : paths)
            symbols.add(asIdentifier(dir));
        symbols.sort(SymbolIdentity.comparator());
        return symbols;
    }

    protected List<SymbolGroup> asGroups(Iterable<Path> paths) {
        List<SymbolGroup> groups = new ArrayList<>();
        for (Path dir : paths)
            groups.add(asGroup(dir));
        groups.sort(Comparator.comparing(SymbolGroup::name));
        return groups;
    }

    protected SymbolGroup asGroup(Path dir) {
        return new SymbolGroup(dir.toString());
    }

    protected Path asPath(SymbolGroup group) {
        String pathName = group.isBase()? "/": group.name();
        return getFileSystem().getPath(pathName);
    }

    @Override
    public List<SymbolGroup> listSymbolGroups() {
        return getFileTreeMetadata().availableGroupsList();
    }

    public List<SymbolGroup> listSymbolGroups(Predicate<SymbolGroup> filter) {
        return getFileTreeMetadata().availableGroupsList(filter);
    }

    public List<? extends SymbolIdentity> listSymbols() {
        return getFileTreeMetadata().getAvailableSymbolsList();
    }

    private static class FileTreeMetadata {
        private final Map<String, SymbolGroup> availableGroups;
        private final Map<SymbolIdentifier, Path> availableSymbols;
        private List<SymbolGroup> availableGroupsList;
        private List<SymbolIdentifier> availableSymbolsList;

        private FileTreeMetadata(Map<String, SymbolGroup> availableGroups, Map<SymbolIdentifier, Path> availableSymbols) {
            this.availableGroups = availableGroups;
            this.availableSymbols = availableSymbols;
        }

        public List<SymbolGroup> availableGroupsList() {
            if (availableGroupsList == null)
                availableGroupsList = List.copyOf(availableGroups.values());
            return availableGroupsList;
        }

        public List<SymbolGroup> availableGroupsList(Predicate<SymbolGroup> filter) {
            return availableGroups.values().stream().filter(filter).toList();
        }

        public List<SymbolIdentifier> getAvailableSymbolsList() {
            if (availableSymbolsList == null)
                availableSymbolsList = List.copyOf(availableSymbols.keySet());
            return availableSymbolsList;
        }
    }

    private FileTreeMetadata metadata;

    private FileTreeMetadata getFileTreeMetadata() {
        if (metadata == null)
            metadata = scanFileTree(getBaseDirectories());
        return metadata;
    }

    protected FileTreeMetadata scanFileTree(Iterable<Path> baseDirs) {
        var availableGroups = new TreeMap<String, SymbolGroup>();
        var availableSymbols = new TreeMap<SymbolIdentifier, Path>();

        try {
            FileTreeScanner scanner = new FileTreeScanner(availableGroups, availableSymbols);
            for (Path rootDir : baseDirs)
                Files.walkFileTree(rootDir, scanner);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new FileTreeMetadata(availableGroups, availableSymbols);
    }

    private class FileTreeScanner extends SimpleFileVisitor<Path> {
        private final Map<String, SymbolGroup> availableGroups;
        private final Map<SymbolIdentifier, Path> availableSymbols;

        private FileTreeScanner(Map<String, SymbolGroup> availableGroups, Map<SymbolIdentifier, Path> availableSymbols) {
            this.availableGroups = availableGroups;
            this.availableSymbols = availableSymbols;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            SymbolGroup group = asGroup(dir);
            availableGroups.put(group.name(), group);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            SymbolIdentity symbol = asIdentifier(file);
            availableSymbols.put(new SymbolIdentifier(symbol), file);
            return super.visitFile(file, attrs);
        }
    }

    private boolean nonNumericProposalExchange = true;

    public void setNonNumericProposalExchange(boolean flag) {
        this.nonNumericProposalExchange = flag;
    }

    @Override
    public List<Symbol> getProposals(String keyword) {
        if (keyword.length() <= 1)
            return List.of();

        // convert text to upper case
        String text = keyword.toUpperCase();
        List<Symbol> list = new ArrayList<>();
        getFileTreeMetadata().availableSymbols.forEach((symbol, path) -> {
            if (symbol.name().contains(text)) {
                Symbol match = new Symbol(symbol, this);

                Path parent = path.getParent();
                if (parent != null)
                    match.setExchange(getProposalExchangeName(parent));

                list.add(match);
            }
        });

        list.sort((o1, o2) -> {
            int p1 = o1.getName().indexOf(text);
            int p2 = o2.getName().indexOf(text);
            if (p1 != p2)
                return p1 - p2;
            return o1.getName().compareTo(o2.getName());
        });
        return list;
    }

    protected String getProposalExchangeName(Path inFolder) {
        SymbolGroup symbolGroup = asGroup(inFolder);
        if (symbolGroup == null)
            return "";

        String symbolGroupName = getSimpleName(symbolGroup);
        if (nonNumericProposalExchange
                && StringUtils.isNumeric(symbolGroupName)
                && (inFolder = inFolder.getParent()) != null) {
            symbolGroupName = getProposalExchangeName(inFolder) + '/' + symbolGroupName;
        }
        return symbolGroupName;
    }
}
