package net.unbeta.content.darkhold;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.StructureType;

/**
 * The Darkhold: Unbeta's third kind of stronghold - a narrow, five-storey obsidian block of
 * rooms standing on the surface, roofed with pyramids, ringed by watch towers, haunted by
 * Endermen, Endermites and Guardian Posts. Registered as unbeta-content:darkhold.
 *
 * <p>Code plans the layout (floors, rooms, doorways, stairwells, roofs, towers); the rooms
 * themselves are structure templates in data/unbeta-content/structures/darkhold/, editable
 * in-game with a structure block. Markers in them (structure blocks in Data mode):
 * {@code challenge}, {@code chest <facing>}, {@code endgate}.
 */
public final class Darkhold {

    public static final Identifier ID = new Identifier("unbeta-content", "darkhold");

    public static StructureType<DarkholdStructure> TYPE;
    public static StructurePieceType ROOM;
    public static StructurePieceType CORRIDOR;
    public static StructurePieceType STAIR;
    public static StructurePieceType ROOF;
    public static StructurePieceType TOWER;
    public static StructurePieceType FOOTING;

    private Darkhold() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    public static void register() {
        StructureType<DarkholdStructure> type = () -> DarkholdStructure.CODEC;
        TYPE = Registry.register(Registries.STRUCTURE_TYPE, ID, type);
        StructurePieceType.ManagerAware room = DarkholdRoomPiece::new;
        ROOM = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_room"), room);
        StructurePieceType.Simple corridor = DarkholdCorridorPiece::new;
        CORRIDOR = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_corridor"), corridor);
        StructurePieceType.Simple stair = DarkholdStairPiece::new;
        STAIR = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_stair"), stair);
        StructurePieceType.Simple roof = DarkholdRoofPiece::new;
        ROOF = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_roof"), roof);
        StructurePieceType.Simple tower = DarkholdTowerPiece::new;
        TOWER = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_tower"), tower);
        StructurePieceType.Simple footing = DarkholdFootingPiece::new;
        FOOTING = Registry.register(Registries.STRUCTURE_PIECE, id("darkhold_footing"), footing);
        DarkholdSpawns.register();
    }
}
