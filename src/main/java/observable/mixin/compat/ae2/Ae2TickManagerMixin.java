package observable.mixin.compat.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import net.minecraft.core.BlockPos;
import observable.Observable;
import observable.compat.ae2.Ae2ProfilerSupport;
import observable.Props;
import observable.server.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Profiles the actual per-device work performed by AE2's grid tick scheduler. */
@Pseudo
@Mixin(targets = "appeng.me.service.TickManagerService", remap = false)
public abstract class Ae2TickManagerMixin {
    @Redirect(
            method = "unsafeTickingRequest",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/networking/ticking/IGridTickable;tickingRequest(Lappeng/api/networking/IGridNode;I)Lappeng/api/networking/ticking/TickRateModulation;",
                    remap = false
            ),
            require = 0
    )
    private static TickRateModulation observable$profileAe2GridTick(
            IGridTickable tickable,
            IGridNode node,
            int ticksSinceLastCall
    ) {
        if (Props.notProcessing) {
            return tickable.tickingRequest(node, ticksSinceLastCall);
        }

        Profiler profiler = Observable.INSTANCE.getPROFILER();
        Profiler.TimingData data = profiler.findExternalBlockTiming(node);
        if (data == null) {
            Object owner = node.getOwner();
            BlockPos position = Ae2ProfilerSupport.findBlockPos(owner);
            if (position == null) {
                // Some synthetic grid nodes have no world representation. They cannot be
                // shown in Observable's block-position based results, so leave them alone.
                return tickable.tickingRequest(node, ticksSinceLastCall);
            }
            data = profiler.processExternalBlock(
                    node,
                    node.getLevel(),
                    position,
                    Ae2ProfilerSupport.findDisplayName(owner)
            );
        }

        Profiler.TimingData previousTarget = Props.currentTarget.getAndSet(data);
        long start = System.nanoTime();
        try {
            return tickable.tickingRequest(node, ticksSinceLastCall);
        } finally {
            data.setTime(data.getTime() + (System.nanoTime() - start));
            // Keep Observable's original data contract: ticks is the number of
            // measured invocations. Normalization later computes total time / profile ticks.
            data.setTicks(data.getTicks() + 1);
            Props.currentTarget.set(previousTarget);
        }
    }
}
