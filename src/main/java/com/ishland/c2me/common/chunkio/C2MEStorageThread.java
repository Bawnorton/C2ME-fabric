package com.ishland.c2me.common.chunkio;

import com.ishland.c2me.common.GlobalExecutors;
import com.ishland.c2me.common.config.C2MEConfigConstants;
import com.ishland.c2me.common.util.SneakyThrow;
import com.ishland.c2me.mixin.access.IRegionFile;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.scanner.NbtScanner;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

public class C2MEStorageThread extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger("C2ME Storage");

    private static final AtomicLong SERIAL = new AtomicLong(0);

    private final AtomicBoolean closing = new AtomicBoolean(false);
    private final CompletableFuture<Void> closeFuture = new CompletableFuture<>();

    private final RegionBasedStorage storage;
    private final Long2ObjectLinkedOpenHashMap<NbtCompound> writeBacklog = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<NbtCompound> cache = new Long2ObjectOpenHashMap<>();
    private final ConcurrentLinkedQueue<ReadRequest> pendingReadRequests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<WriteRequest> pendingWriteRequests = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();
    private final Executor executor = command -> {
        if (Thread.currentThread() == this) {
            command.run();
        } else {
            pendingTasks.add(command);
            LockSupport.unpark(this);
        }
    };
    private final ObjectArraySet<CompletableFuture<Void>> writeFutures = new ObjectArraySet<>();

    public C2MEStorageThread(Path directory, boolean dsync, String name) {
        this.storage = new RegionBasedStorage(directory, dsync);
        this.setName("C2ME Storage #%d".formatted(SERIAL.incrementAndGet()));
        this.setDaemon(true);
        this.setUncaughtExceptionHandler(new net.minecraft.util.logging.UncaughtExceptionHandler(LOGGER));
        this.start();
    }

    @Override
    public void run() {
        while (true) {
            boolean hasWork = false;
            hasWork = handleTasks() || hasWork;
            hasWork = handlePendingWrites() || hasWork;
            hasWork = handlePendingReads() || hasWork;

            if (!hasWork) {
                hasWork = this.writeBacklog() || hasWork;
            }

            if (!hasWork) {
                if (this.closing.get()) {
                    flush0();
                    try {
                        this.storage.close();
                    } catch (Throwable t) {
                        LOGGER.error("Error closing storage", t);
                    }
                    break;
                } else {
                    LockSupport.parkNanos("Waiting for tasks", 1_000_000);
                }
            }
        }
        LOGGER.info("Storage thread {} stopped", this);
    }

    /**
     * Read chunk data from storage
     * @param pos target pos
     * @param scanner if null then ignored, if non-null then used and produce null future
     * @return future
     */
    public CompletableFuture<NbtCompound> getChunkData(long pos, NbtScanner scanner) {
        final CompletableFuture<NbtCompound> future = new CompletableFuture<>();
        this.pendingReadRequests.add(new ReadRequest(pos, future, null));
        LockSupport.unpark(this);
        return future.thenApply(Function.identity());
    }

    public void setChunkData(long pos, @Nullable NbtCompound nbt) {
        this.pendingWriteRequests.add(new WriteRequest(pos, nbt));
        LockSupport.unpark(this);
    }

    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(this::flush0, this.executor);
    }

    private void flush0() {
        try {
            while (true) {
                if (handleTasks()) continue;
                if (handlePendingReads()) continue;
                if (handlePendingWrites()) continue;
                if (writeBacklog()) continue;

                break;
            }
            this.storage.sync();
        } catch (Throwable t) {
            LOGGER.error("Error flushing storage", t);
        }
    }

    public CompletableFuture<Void> close() {
        this.closing.set(true);
        LockSupport.unpark(this);
        return this.closeFuture.thenApply(Function.identity());
    }

    private boolean handleTasks() {
        boolean hasWork = false;
        Runnable runnable;
        while ((runnable = this.pendingTasks.poll()) != null) {
            hasWork = true;
            try {
                runnable.run();
            } catch (Throwable t) {
                LOGGER.error("Error while executing task", t);
            }
        }
        return hasWork;
    }

    private boolean handlePendingWrites() {
        boolean hasWork = false;
        WriteRequest writeRequest;
        while ((writeRequest = this.pendingWriteRequests.poll()) != null) {
            hasWork = true;
            this.cache.put(writeRequest.pos, writeRequest.nbt);
            this.writeBacklog.put(writeRequest.pos, writeRequest.nbt);
        }
        return hasWork;
    }

    private boolean handlePendingReads() {
        boolean hasWork = false;
        ReadRequest readRequest;
        while ((readRequest = this.pendingReadRequests.poll()) != null) {
            hasWork = true;
            final long pos = readRequest.pos;
            final CompletableFuture<NbtCompound> future = readRequest.future;
            final NbtScanner scanner = readRequest.scanner;
            final NbtCompound cached = this.cache.get(pos);
            if (cached != null) {
                future.complete(cached);
                continue;
            }
            scheduleChunkRead(pos, future, scanner);
        }
        return hasWork;
    }

    private boolean writeBacklog() {
        if (!this.writeBacklog.isEmpty()) {
            final long pos = this.writeBacklog.firstLongKey();
            final NbtCompound nbt = this.writeBacklog.removeFirst();
            writeChunk(pos, nbt);
            return true;
        }
        return false;
    }

    private void scheduleChunkRead(long pos, CompletableFuture<NbtCompound> future, NbtScanner scanner) {
        final NbtCompound cached = this.cache.get(pos);
        if (cached != null) {
            if (scanner != null) {
                cached.accept(scanner);
                future.complete(null);
                return;
            } else {
                future.complete(cached);
                return;
            }
        }

        try {
            final ChunkPos pos1 = new ChunkPos(pos);
            final RegionFile regionFile = this.storage.getRegionFile(pos1);
            final DataInputStream chunkInputStream = regionFile.getChunkInputStream(pos1);
            if (chunkInputStream == null) {
                future.complete(null);
                return;
            }
            CompletableFuture.supplyAsync(() -> {
                try {
                    try (DataInputStream inputStream = chunkInputStream) {
                        if (scanner != null) {
                            NbtIo.read(inputStream, scanner);
                            return null;
                        } else {
                            return NbtIo.read(inputStream);
                        }
                    }
                } catch (Throwable t) {
                    SneakyThrow.sneaky(t);
                    return null; // Unreachable anyway
                }
            }, GlobalExecutors.executor).handleAsync((compound, throwable) -> {
                if (throwable != null) future.completeExceptionally(throwable);
                else future.complete(compound);
                return null;
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private void writeChunk(long pos, NbtCompound nbt) {
        if (nbt == null) {
            try {
                final ChunkPos pos1 = new ChunkPos(pos);
                final RegionFile regionFile = this.storage.getRegionFile(pos1);
                regionFile.method_31740(pos1);
            } catch (Throwable t) {
                LOGGER.error("Error writing chunk %s".formatted(new ChunkPos(pos)), t);
            }
        } else {
            final CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                try {
                    final RawByteArrayOutputStream out = new RawByteArrayOutputStream(8096);
                    // TODO [VanillaCopy] RegionFile.ChunkBuffer
                    out.write(0);
                    out.write(0);
                    out.write(0);
                    out.write(0);
                    out.write(C2MEConfigConstants.CHUNK_STREAM_VERSION.getId());
                    try (DataOutputStream dataOutputStream = new DataOutputStream(C2MEConfigConstants.CHUNK_STREAM_VERSION.wrap(out))) {
                        NbtIo.write(nbt, dataOutputStream);
                    }
                    return out;
                } catch (Throwable t) {
                    SneakyThrow.sneaky(t);
                    return null; // Unreachable anyway
                }
            }, GlobalExecutors.executor).thenAcceptAsync(bytes -> {
                if (nbt == this.cache.get(pos)) { // only write if match to avoid overwrites
                    try {
                        final ChunkPos pos1 = new ChunkPos(pos);
                        final RegionFile regionFile = this.storage.getRegionFile(pos1);
                        ByteBuffer byteBuffer = bytes.asByteBuffer();
                        // TODO [VanillaCopy] RegionFile.ChunkBuffer
                        byteBuffer.putInt(0, bytes.size() - 5 + 1);
                        ((IRegionFile) regionFile).invokeWriteChunk(pos1, byteBuffer);
                    } catch (Throwable t) {
                        SneakyThrow.sneaky(t);
                    }
                }
            }, this.executor).handleAsync((unused, throwable) -> {
                if (throwable != null) LOGGER.error("Error writing chunk %s".formatted(new ChunkPos(pos)), throwable);
                // TODO error retry

                this.cache.remove(pos);
                return null;
            }, this.executor);
            this.writeFutures.add(future);
        }
    }

    private record ReadRequest(long pos, CompletableFuture<NbtCompound> future, @Nullable NbtScanner scanner) {
    }

    private record WriteRequest(long pos, NbtCompound nbt) {
    }

}