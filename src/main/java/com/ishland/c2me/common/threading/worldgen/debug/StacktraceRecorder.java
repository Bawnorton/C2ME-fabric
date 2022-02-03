package com.ishland.c2me.common.threading.worldgen.debug;


import com.google.common.collect.Sets;
import com.ishland.c2me.common.config.C2MEConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.structure.processor.RuleStructureProcessor;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.WorldView;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.BlendingData;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

// Used to examine getChunk calls with reduced lock radius
public class StacktraceRecorder {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean doRecord = !Boolean.getBoolean("com.ishland.c2me.common.threading.worldgen.debug.NoDebugReducedLockRadius") && C2MEConfig.threadedWorldGenConfig.reduceLockRadius;
    private static final boolean warnAtWarningLevel = !Boolean.getBoolean("com.ishland.c2me.common.threading.worldgen.debug.DebugReducedLockRadiusAtWarningLevel");
    private static final int recordFrequency = MathHelper.clamp(Integer.getInteger("com.ishland.c2me.common.threading.worldgen.debug.DebugReducedLockRadiusFrequency", 4), 1, 16);
    private static final long frequencyBitMask = (1L << recordFrequency) - 1;

    private static final Set<StacktraceHolder> recordedStacktraces = Sets.newConcurrentHashSet();
    private static final AtomicLong sampledCount = new AtomicLong();

    public static void record() {
        if (!doRecord) return;
        if ((sampledCount.incrementAndGet() & frequencyBitMask) != 0) return;
        final StacktraceHolder stacktraceHolder = new StacktraceHolder();
        if (recordedStacktraces.add(stacktraceHolder)) {
            if (stacktraceHolder.needPrint()) {
                LOGGER.warn("Potential dangerous call with reducedLockRadius", stacktraceHolder.throwable);
            } else {
//                LOGGER.info("Ignoring safe call");
            }
        }
    }


    public static class StacktraceHolder {

        private static final String StructureProcessor$process = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_3491", "method_15110", "(Lnet/minecraft/class_4538;Lnet/minecraft/class_2338;Lnet/minecraft/class_2338;Lnet/minecraft/class_3499$class_3501;Lnet/minecraft/class_3499$class_3501;Lnet/minecraft/class_3492;)Lnet/minecraft/class_3499$class_3501;");
        private static final String BiomeAccess$Storage$getBiomeForNoiseGen = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_4543$class_4544", "method_16359", "(III)Lnet/minecraft/class_1959;");
        private static final String BlendingData$getBlendingData = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_6749", "method_39570", "(Lnet/minecraft/class_3233;II)Lnet/minecraft/class_6749;");
        private static final String ChunkGenerator$carve = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_2794", "method_12108", "(Lnet/minecraft/class_3233;JLnet/minecraft/class_4543;Lnet/minecraft/class_5138;Lnet/minecraft/class_2791;Lnet/minecraft/class_2893$class_2894;)V");
        private static final String SpawnHelper$populateEntities = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_1948", "method_8661", "(Lnet/minecraft/class_5425;Lnet/minecraft/class_1959;Lnet/minecraft/class_1923;Ljava/util/Random;)V");
        private static final String StructureAccessor$getStructureStarts = FabricLoader.getInstance().getMappingResolver()
                .mapMethodName("intermediary", "net.minecraft.class_5138", "method_38853", "(Lnet/minecraft/class_4076;Lnet/minecraft/class_3195;)Ljava/util/List;");

        @NotNull
        private final StackTraceElement[] stackTrace;
        private final Throwable throwable;

        public StacktraceHolder() {
            this.throwable = new Throwable();
            this.stackTrace = this.throwable.getStackTrace();
        }

        public boolean needPrint() {
            for (StackTraceElement stackTraceElement : stackTrace) {
                if (stackTraceElement.getMethodName().equals("method_26971"))
                    return false;
                if (stackTraceElement.getClassName().equals(RuleStructureProcessor.class.getName()) &&
                        stackTraceElement.getMethodName().equals(StructureProcessor$process))
                    return false;
                if (stackTraceElement.getClassName().equals(WorldView.class.getName()) &&
                        stackTraceElement.getMethodName().equals(BiomeAccess$Storage$getBiomeForNoiseGen))
                    return false;
                if (stackTraceElement.getClassName().equals(BlendingData.class.getName()) &&
                        stackTraceElement.getMethodName().equals(BlendingData$getBlendingData))
                    return false;
                if (stackTraceElement.getClassName().equals(NoiseChunkGenerator.class.getName()) &&
                        stackTraceElement.getMethodName().equals(ChunkGenerator$carve))
                    return false;
                if (stackTraceElement.getClassName().equals(SpawnHelper.class.getName()) &&
                        stackTraceElement.getMethodName().equals(SpawnHelper$populateEntities))
                    return false;
                if (stackTraceElement.getClassName().equals(StructureAccessor.class.getName()) &&
                        stackTraceElement.getMethodName().equals(StructureAccessor$getStructureStarts))
                    return false;

                // lithium
                if (stackTraceElement.getClassName().equals("me.jellysquid.mods.lithium.common.entity.movement.ChunkAwareBlockCollisionSweeper"))
                    return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "StacktraceHolder{" +
                    "stackTrace=" + Arrays.toString(stackTrace) +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StacktraceHolder that = (StacktraceHolder) o;
            return Arrays.equals(stackTrace, that.stackTrace);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(stackTrace);
        }
    }

}
