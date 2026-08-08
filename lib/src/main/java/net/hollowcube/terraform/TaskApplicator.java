package net.hollowcube.terraform;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.hollowcube.terraform.buffer.BlockBuffer;
import net.hollowcube.terraform.task.Task;
import net.hollowcube.terraform.task.TaskImpl;
import net.hollowcube.terraform.task.TaskResult;
import net.hollowcube.terraform.task.edit.WorldView;
import net.hollowcube.terraform.util.Format;
import net.hollowcube.terraform.util.ThreadUtil;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.ChunkHack;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.BlockEntityDataPacket;
import net.minestom.server.network.packet.server.play.MultiBlockChangePacket;
import net.minestom.server.utils.block.BlockUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TaskApplicator {
    private final ExecutorService threadPoolCompute;
    private final ExecutorService threadPoolApply;
    private static final Logger logger = LoggerFactory.getLogger(TaskApplicator.class);

    public TaskApplicator() {
        this.threadPoolCompute = Executors.newFixedThreadPool(3, new ThreadUtil.NamedThreadFactory("tf-compute"));
        this.threadPoolApply = Executors.newFixedThreadPool(3, new ThreadUtil.NamedThreadFactory("tf-apply"));
    }

    public void submitTask(@NotNull TaskImpl task) {
        if (task.state() != Task.State.INIT) return;
        task.setState(Task.State.QUEUED, task.computeFunc() != null
                ? threadPoolCompute.submit(computeTask(task))
                : threadPoolApply.submit(applyTask(task)));
    }

    private @NotNull Runnable computeTask(@NotNull TaskImpl task) {
        return () -> {
            try {
                logger.debug("{}: compute started", task);
                task.setState(Task.State.COMPUTE, null);
                long start = System.nanoTime();

                var world = WorldView.instance(task, task.instance);
                var buffer = Objects.requireNonNull(task.computeFunc()).exec(task, world);
                task.setBuffer(buffer);
                ThreadUtil.testInterrupt(); // Stop before submitting the apply task

                logger.debug("{}: compute complete in {}ms ({})", task, (System.nanoTime() - start) / 1_000_000d, Format.formatBytes(buffer.sizeBytes()));
                task.setState(Task.State.QUEUED, threadPoolApply.submit(applyTask(task)));
            } catch (InterruptedException interrupt) {
                task.setState(Task.State.CANCELLED, null);
                logger.debug("{}: compute cancelled", task);
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                logger.error("{}: compute failed", task, t);
                task.setState(Task.State.FAILED, null);
            }
        };
    }

    private @NotNull Runnable applyTask(@NotNull TaskImpl task) {
        Objects.requireNonNull(task.buffer(), "Task buffer must not be null when applying");
        return () -> {
            try {
                logger.debug("{}: apply started", task);
                task.setState(Task.State.APPLY, null);
                long start = System.nanoTime();

                var buffer = task.buffer();
                final var changeCount = new AtomicLong();

                var instance = task.instance;

                final var sectionChangeCache = new LongArrayList();
                final var paletteData = new int[4096]; // Reused buffer
                final var indexCache = new AtomicInteger(0);
                final var blockEntityUpdates = new ArrayList<SendablePacket>();

                final var undoBufferBuilder = BlockBuffer.builder(null); //todo add compute buffer min + max here

                buffer.forEachSection((chunkX, chunkY, chunkZ, palette) -> {
                    var chunk = instance.getChunk(chunkX, chunkZ);
                    if (chunk == null) {
                        // Chunk is not loaded
                        logger.warn("{}: reference to unloaded chunk at {}, {}", task, chunkX, chunkZ);
                        chunk = instance.loadChunk(chunkX, chunkZ).join(); // We are in apply thread, its fine to block
                    }
                    final var chunkRef = chunk; // Final var for lambda

                    sectionChangeCache.clear();
                    blockEntityUpdates.clear();

                    int totalSections = chunk.getMaxSection() - chunk.getMinSection();
                    if (chunkY > totalSections) {
                        logger.warn("{}: reference to invalid section at {}, {}: {}", task, chunkX, chunkZ, chunkY);
                        return;
                    }
                    var section = chunk.getSection(chunkY);
                    synchronized (chunk) { // Synchronized is OK, we always run this on one of the dedicated threads.
                        //todo optimize palette apply, if the palette is a full chunk of the same block we can do a single fill.
                        //todo replaceall is not working, but that would be ideal. instead we do a get then set.

                        indexCache.set(0);
                        section.blockPalette().getAll((sx, sy, sz, stateId) -> {
                            try {
                                var paletteIndex = indexCache.getAndIncrement();
                                var newBlockState = palette.get(sx, sy, sz);

                                if (newBlockState == null) {
                                    paletteData[paletteIndex] = stateId;
                                } else {
                                    var chunkOldBlock = chunkRef.getBlock(chunkX * 16 + sx, chunkY * 16 + sy, chunkZ * 16 + sz);
                                    //todo bad bad very bad
                                    if (!task.isDryRun())
                                        chunkRef.setBlock(chunkX * 16 + sx, chunkY * 16 + sy, chunkZ * 16 + sz, Block.AIR);

                                    paletteData[paletteIndex] = newBlockState.stateId();
                                    if (!task.isDryRun() && (newBlockState.handler() != null || newBlockState.hasNbt())) {
                                        var blockPosition = new Vec(chunkX * 16 + sx, chunkY * 16 + sy, chunkZ * 16 + sz);
                                        chunkRef.setBlock(chunkX * 16 + sx, chunkY * 16 + sy, chunkZ * 16 + sz, newBlockState);

                                        if (newBlockState.blockEntityType() != null) {
                                            var clientData = BlockUtils.extractClientNbt(newBlockState);
                                            blockEntityUpdates.add(new BlockEntityDataPacket(blockPosition, newBlockState.blockEntityType().id(), clientData));
                                        }
                                    }

                                    // Only send a client update if the block was actually modified (and for change counter)
                                    if (stateId != newBlockState.stateId()) {
                                        changeCount.incrementAndGet();
                                        sectionChangeCache.add(((long) newBlockState.stateId() << 12) | ((long) sx << 8 | (long) sz << 4 | sy));
                                    }

                                    // Save old state for undo batch
                                    // Note that we save regardless of whether the block was actually modified,
                                    // this is because undo-ing should change it in case the block changed after
                                    // the apply.
                                    //todo we need to save block entities here too
                                    undoBufferBuilder.set(
                                            (chunkX << 4) + sx,
                                            (chunkY << 4) + sy,
                                            (chunkZ << 4) + sz,
                                            chunkOldBlock
                                    );
                                }
                            } catch (InterruptedException interrupt) {
                                Thread.currentThread().interrupt();
                            }
                        });

                        if (!task.isDryRun()) {
                            indexCache.set(0);
                            section.blockPalette().setAll((sx, sy, sz) -> paletteData[indexCache.getAndIncrement()]);
                        }
                    }

                    if (!task.isDryRun()) {
                        var updateIndex = (((long) chunkX & 0x3FFFFF) << 42) | ((long) chunkY & 0xFFFFF) | (((long) chunkZ & 0x3FFFFF) << 20);
                        var packet = new MultiBlockChangePacket(updateIndex, sectionChangeCache.toLongArray());
                        chunk.sendPacketToViewers(packet); //todo these could be batched perhaps, maybe minestom does it on its own?
                        chunk.sendPacketsToViewers(blockEntityUpdates);
                        ChunkHack.invalidateChunk(chunk);
                    }

                    //todo the client is super laggy when sending many of these, perhaps this should be iterated by vertical chunk and resend the entire chunk if there are enough sections changed
                });

                // Append to history
                var undoBuffer = undoBufferBuilder.build();

                task.setState(Task.State.COMPLETE, null);
                logger.debug("{}: apply complete in {}ms", task, (System.nanoTime() - start) / 1_000_000d);

                var result = new TaskResult(undoBuffer, buffer, changeCount.get(), task.attributes());
                var postApplyFunc = task.postApplyFunc();
                if (postApplyFunc != null) {
                    postApplyFunc.exec(result);
                }
            } catch (Throwable t) {
                logger.error("{}: apply failed", task, t);
                task.setState(Task.State.FAILED, null);
            }
        };
    }

}
