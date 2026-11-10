package com.lycoris.loconautics.common.strand;

public enum StrandKind {

    STEEL_CABLE,
    HOSE_LINK;

    private final ClientStrands clientStrands = new ClientStrands();

    public ClientStrands clientStrands() {
        return clientStrands;
    }
}
