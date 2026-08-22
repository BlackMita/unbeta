package net.unbeta.content.datapack;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BundledDatapackInstaller {

    private static final String[] PACKS = {
        "DATA_PACK_Attrition 2.4.4 for MC 1.20.zip",
        "DATA_PACK_daycount-v1-2.zip",
        "DATA_PACK_dynamiclights-v1.8.3-mc1.17x-1.21x.zip",
        "DATA_PACK_triggerVarnish.zip"
    };

    private BundledDatapackInstaller() {}

    public static void register() {
        ServerWorldEvents.LOAD.register(BundledDatapackInstaller::onWorldLoad);
    }

    private static void onWorldLoad(MinecraftServer server, ServerWorld world) {
        if (world != server.getOverworld()) return;
        Path datapacksDir = server.getSavePath(WorldSavePath.DATAPACKS);
        try { Files.createDirectories(datapacksDir); } catch (Exception e) { return; }

        boolean installedAny = false;
        for (String packName : PACKS) {
            Path dest = datapacksDir.resolve(packName);
            if (Files.exists(dest)) continue;
            String resource = "/unbeta_bundled_datapacks/" + packName;
            try (InputStream in = BundledDatapackInstaller.class.getResourceAsStream(resource)) {
                if (in == null) continue;
                try (OutputStream out = Files.newOutputStream(dest)) { in.transferTo(out); }
                net.unbeta.content.UnbetaContent.LOG.info("[Unbeta] Installed datapack: {}", packName);
                installedAny = true;
            } catch (Exception e) {
                net.unbeta.content.UnbetaContent.LOG.error("[Unbeta] Failed to install datapack: {}", packName, e);
            }
        }

        if (installedAny) {
            // Rescan so the pack manager discovers the newly copied zips,
            // then reload with ALL known pack names so they get enabled.
            server.getDataPackManager().scanPacks();
            server.reloadResources(server.getDataPackManager().getNames())
                .exceptionally(t -> { net.unbeta.content.UnbetaContent.LOG.error("[Unbeta] Reload failed", t); return null; });
        }
    }
}
