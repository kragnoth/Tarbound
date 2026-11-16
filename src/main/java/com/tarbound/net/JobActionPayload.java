package com.tarbound.net;

import com.tarbound.Tarbound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: start a single-step job from the contract in a reliquary slot, or cancel a job.
 *
 * @param machine      the bound machine (its reliquary sits above it), for REQUEST
 * @param contractSlot the reliquary's contract slot, for REQUEST
 * @param ops          operations to run, for REQUEST
 * @param jobId        the job, for CANCEL
 */
public record JobActionPayload(int containerId, boolean cancel, BlockPos machine, int contractSlot, int ops, int jobId)
        implements CustomPacketPayload {

    public static JobActionPayload request(int containerId, BlockPos machine, int contractSlot, int ops) {
        return new JobActionPayload(containerId, false, machine, contractSlot, ops, 0);
    }

    public static JobActionPayload cancel(int containerId, int jobId) {
        return new JobActionPayload(containerId, true, BlockPos.ZERO, 0, 0, jobId);
    }

    /** Job id meaning "every job". */
    public static final int ALL = -1;

    public static JobActionPayload cancelAll(int containerId) {
        return cancel(containerId, ALL);
    }

    public static final Type<JobActionPayload> TYPE = new Type<>(Tarbound.id("job_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(JobActionPayload::write, JobActionPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBoolean(cancel);
        buf.writeBlockPos(machine);
        buf.writeVarInt(contractSlot);
        buf.writeVarInt(ops);
        buf.writeVarInt(jobId);
    }

    private static JobActionPayload read(RegistryFriendlyByteBuf buf) {
        return new JobActionPayload(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
