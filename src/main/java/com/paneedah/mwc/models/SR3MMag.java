package com.paneedah.mwc.models;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import javax.annotation.Nonnull;

// temp model
public class SR3MMag extends ModelBase {

    private final ModelRenderer mag;

    public SR3MMag() {
        textureWidth = 64;
        textureHeight = 64;

        mag = new ModelRenderer(this);
        mag.setRotationPoint(0.0F, 23.7F, 0.1F);

        ModelRenderer body = new ModelRenderer(this);
        body.setRotationPoint(0.0F, -28.7F, -24.0F);
        mag.addChild(body);

        body.cubeList.add(new ModelBox(
                body, 0, 0,
                -1.5F, -2.0F, -1.4F,
                3, 3, 3, 0.0F, false
        ));

        body.cubeList.add(new ModelBox(
                body, 0, 8,
                -2.0F, 0.0F, -1.5F,
                4, 5, 3, 0.0F, false
        ));

        ModelRenderer middle = new ModelRenderer(this);
        middle.setRotationPoint(0.0F, 5.0F, 0.5F);
        body.addChild(middle);

        middle.cubeList.add(new ModelBox(
                middle, 15, 0,
                -2.0F, 0.0F, -1.5F,
                4, 6, 3, 0.0F, false
        ));

        ModelRenderer lower = new ModelRenderer(this);
        lower.setRotationPoint(0.0F, 11.0F, 1.5F);
        body.addChild(lower);

        lower.cubeList.add(new ModelBox(
                lower, 15, 10,
                -2.0F, 0.0F, -1.5F,
                4, 5, 3, 0.0F, false
        ));

        ModelRenderer bottom = new ModelRenderer(this);
        bottom.setRotationPoint(0.0F, 16.0F, 2.4F);
        body.addChild(bottom);

        bottom.cubeList.add(new ModelBox(
                bottom, 0, 19,
                -2.0F, 0.0F, -1.5F,
                4, 2, 3, 0.0F, false
        ));

        addRibs(body);
        addRibs(middle);
        addRibs(lower);

        ModelRenderer floorPlate = new ModelRenderer(this);
        floorPlate.setRotationPoint(0.0F, 17.5F, 2.5F);
        body.addChild(floorPlate);

        floorPlate.cubeList.add(new ModelBox(
                floorPlate, 0, 25,
                -2.0F, 0.0F, -1.5F,
                4, 1, 3, 0.0F, false
        ));

        body.cubeList.add(new ModelBox(
                body, 18, 22,
                -1.8F, -2.5F, -1.8F,
                4, 1, 4, -0.2F, false
        ));
    }

    private void addRibs(ModelRenderer parent) {
        for (int i = 0; i < 3; i++) {
            float y = 0.7F + i * 1.65F;

            ModelRenderer leftRib = new ModelRenderer(this);
            parent.addChild(leftRib);

            leftRib.cubeList.add(new ModelBox(
                    leftRib, 32, 0,
                    -2.0F, y - 0.35F, -1.97F,
                    4, 1, 1, -0.35F, false
            ));

            ModelRenderer rightRib = new ModelRenderer(this);
            parent.addChild(rightRib);

            rightRib.cubeList.add(new ModelBox(
                    rightRib, 32, 0,
                    -2.0F, y - 0.35F, 0.97F,
                    4, 1, 1, -0.35F, false
            ));
        }
    }

    @Override
    public void render(@Nonnull Entity entity, float f, float f1,
                       float f2, float f3, float f4, float f5) {
        mag.render(f5);
    }

    public void setRotationAngle(ModelRenderer modelRenderer,
                                 float x, float y, float z) {
        modelRenderer.rotateAngleX = x;
        modelRenderer.rotateAngleY = y;
        modelRenderer.rotateAngleZ = z;
    }
}
