package net.unbeta.content.lockey;

import java.util.UUID;

/** A chest block entity rebuilt from carried data that still owes its lock. */
public interface LockeyCarrier {
    /** The carried lock's key id, once; null if none. */
    UUID unbeta_takePendingLock();
}
