package com.lycoris.loconautics.common.strand;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class StrandAttachments {

    private StrandAttachments() {
    }

    @Nullable
    public static <B extends BlockEntityBehaviour & StrandAttachmentStore> B store(Object endpoint,
                                                                                   BehaviourType<B> type) {
        return endpoint instanceof SmartBlockEntity be ? be.getBehaviour(type) : null;
    }

    public static <B extends BlockEntityBehaviour & StrandAttachmentStore> boolean isFree(Object endpoint,
                                                                                         BehaviourType<B> type) {
        B store = store(endpoint, type);
        return store != null && store.isFree();
    }

    public static <B extends BlockEntityBehaviour & StrandAttachmentStore> void attach(Object endpoint,
                                                                                       BehaviourType<B> type,
                                                                                       UUID strandId) {
        B store = store(endpoint, type);
        if (store != null) {
            store.attach(strandId);
        }
    }

    public static <B extends BlockEntityBehaviour & StrandAttachmentStore> void detach(Object endpoint,
                                                                                       BehaviourType<B> type,
                                                                                       UUID strandId) {
        B store = store(endpoint, type);
        if (store != null) {
            store.detach(strandId);
        }
    }

    public static <B extends BlockEntityBehaviour & SingleStrandAttachment> void handOver(
            @Nullable Object from, @Nullable Object to, BehaviourType<B> type, UUID strandId) {
        B old = from instanceof SmartBlockEntity be ? be.getBehaviour(type) : null;
        if (old != null && strandId.equals(old.rawAttachedStrandId())) {
            old.rawAttachedStrandId(null);
        }
        B next = to instanceof SmartBlockEntity be ? be.getBehaviour(type) : null;
        if (next != null && !strandId.equals(next.rawAttachedStrandId())) {
            next.setAttachedStrandId(strandId);
        }
    }

    public static <B extends BlockEntityBehaviour & StrandAttachmentStore> boolean holds(Object endpoint,
                                                                                         BehaviourType<B> type,
                                                                                         UUID strandId) {
        B store = store(endpoint, type);
        return store != null && store.holds(strandId);
    }
}
