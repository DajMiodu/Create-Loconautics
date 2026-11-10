package com.lycoris.loconautics.content.trainmail;

import java.util.Set;
import java.util.UUID;

public interface PostalState {

    boolean loconautics$tripped();

    void loconautics$setTripped(boolean tripped);

    void loconautics$setRepeat(boolean repeat);

    boolean loconautics$repeat();

    boolean loconautics$deposit();

    void loconautics$setDeposit(boolean deposit);

    Set<UUID> loconautics$warned();
}
