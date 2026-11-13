package com.paneedah.mwc.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import static com.paneedah.mwc.ProjectConstants.NAME;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name(NAME + " Plugin")
@IFMLLoadingPlugin.TransformerExclusions("com.paneedah.mwc.asm")
public class MWCPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
                "com.paneedah.mwc.asm.MWCClassTransformer"
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    /**
     * Returns the mixin configurations loaded during early Mixin initialization.
     *
     * <p>The main configuration contains existing client-side MWC mixins.
     * The combat configuration contains server-side movement history hooks.</p>
     *
     * @return early mixin configuration resource names
     */
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList(
                "mixin.mwc.json",
                "mixin.combat.json"
        );
    }
}
