package com.ishland.c2me.natives.mixin.density_functions;

import com.ishland.c2me.base.mixin.access.IDoublePerlinNoiseSampler;
import com.ishland.c2me.natives.common.NativeInterface;
import com.ishland.c2me.natives.common.NativeMemoryTracker;
import com.ishland.c2me.natives.common.NativeStruct;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(DensityFunctionTypes.ShiftA.class)
public abstract class MixinDensityFunctionTypesShiftA implements DensityFunction.class_6913, NativeStruct {

    @Shadow
    @Final
    private @Nullable DoublePerlinNoiseSampler offsetNoise;
    @Unique
    private long pointer = 0;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo info) {
        System.out.println("Compiling density function: shift_a %s".formatted(this));
        if (this.offsetNoise != null) {
            this.pointer = NativeInterface.createDFIShifted0Data(
                    false,
                    ((NativeStruct) ((IDoublePerlinNoiseSampler) this.offsetNoise).getFirstSampler()).getNativePointer(),
                    ((NativeStruct) ((IDoublePerlinNoiseSampler) this.offsetNoise).getSecondSampler()).getNativePointer(),
                    ((IDoublePerlinNoiseSampler) this.offsetNoise).getAmplitude()
            );
        } else {
            this.pointer = NativeInterface.createDFIShiftedAData(
                    true,
                    0,
                    0,
                    0
            );
        }
        NativeMemoryTracker.registerAllocatedMemory(this, NativeInterface.SIZEOF_density_function_data + NativeInterface.SIZEOF_dfi_simple_shifted_noise_data, this.pointer);
    }

    /**
     * @author ishland
     * @reason use native method
     */
    @Overwrite
    public double sample(NoisePos pos) {
        if (this.offsetNoise == null) return 0.0;
        return NativeInterface.dfiBindingsSingleOp(this.pointer, pos.blockX(), pos.blockY(), pos.blockZ());
    }

    @Override
    public void method_40470(double[] ds, class_6911 arg) {
        if (this.offsetNoise == null) {
            Arrays.fill(ds, 0.0);
            return;
        }
        if (arg instanceof NativeStruct nativeStruct) {
            NativeInterface.dfiBindingsMultiOp(this.pointer, nativeStruct.getNativePointer(), ds);
        } else {
            class_6913.super.method_40470(ds, arg);
        }
    }

    @Override
    public long getNativePointer() {
        return this.pointer;
    }

}