package net.unbeta.hg.client;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Who the server says is out of the round (drawn as ghosts). */
public final class OutClient {

    private OutClient() {}

    private static Set<UUID> out = new HashSet<>();

    public static void update(Set<UUID> ids) {
        out = ids;
    }

    public static void reset() {
        out = new HashSet<>();
    }

    public static boolean isOut(UUID id) {
        return out.contains(id);
    }
}
