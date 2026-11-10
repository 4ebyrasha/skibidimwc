package com.paneedah.mwc.groovyscript;

import com.cleanroommc.groovyscript.api.GroovyPlugin;
import com.cleanroommc.groovyscript.compat.mods.GroovyContainer;
import com.cleanroommc.groovyscript.documentation.linkgenerator.LinkGeneratorHooks;
import com.paneedah.mwc.groovyscript.script.CraftingStation;

import static com.paneedah.mwc.ProjectConstants.ID;
import static com.paneedah.mwc.ProjectConstants.NAME;

/**
 * @author yor42
 * @version 2024-12-04
 * @since 0.1
 */
public final class MWCGroovyPlugin implements GroovyPlugin {

    public static final CraftingStation CRAFTING_STATION = new CraftingStation();

    @Override
    public String getModId() {
        return ID;
    }

    @Override
    public String getContainerName() {
        return NAME;
    }

    @Override
    public void onCompatLoaded(final GroovyContainer<?> groovyContainer) {
        groovyContainer.addProperty(CRAFTING_STATION);
        LinkGeneratorHooks.registerLinkGenerator(new MWCLinkGenerator());
    }
}
