package net.unbeta.content.skyhold;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;

/**
 * The Skyhold: Unbeta's second kind of stronghold - a workshop hollowed into a floating island,
 * ringed by smaller islands, between y 200 and y 300. It is its own registered structure
 * (unbeta-content:skyhold), so anything can ask "am I inside a Skyhold?".
 */
public final class Skyhold {

    public static final Identifier ID = new Identifier("unbeta-content", "skyhold");

    public static StructureType<SkyholdStructure> TYPE;
    public static StructurePieceType ISLAND;

    private Skyhold() {}

    public static void register() {
        StructureType<SkyholdStructure> type = () -> SkyholdStructure.CODEC;
        TYPE = Registry.register(Registries.STRUCTURE_TYPE, ID, type);
        StructurePieceType.Simple island = SkyholdIslandPiece::new;
        ISLAND = Registry.register(Registries.STRUCTURE_PIECE, new Identifier("unbeta-content", "skyhold_island"), island);
    }
}
