package com.lycoris.loconautics.common.strand;

import java.util.UUID;

public interface StrandAttachmentStore {

    void attach(UUID strandId);

    void detach(UUID strandId);

    boolean holds(UUID strandId);

    boolean isFree();
}
