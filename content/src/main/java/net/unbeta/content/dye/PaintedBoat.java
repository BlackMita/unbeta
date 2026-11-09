package net.unbeta.content.dye;

/** A boat's paint colour (added to boats by BoatPaintMixin): a DyeColor id, or -1 for plain wood. */
public interface PaintedBoat {
    int unbeta_paint();

    void unbeta_setPaint(int color);
}
