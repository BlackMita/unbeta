package net.unbeta.content.client.unmason;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.util.Identifier;
import net.unbeta.content.unmason.UnmasonRegistry;

public class UnmasonRenderer extends ZombieEntityRenderer {

    private static final Identifier TEXTURE =
            new Identifier("unbeta-content", "textures/entity/unmason.png");

    public UnmasonRenderer(EntityRendererFactory.Context context) {
        super(context);
    }

    @Override
    public Identifier getTexture(net.minecraft.entity.mob.ZombieEntity entity) {
        return TEXTURE;
    }
}
