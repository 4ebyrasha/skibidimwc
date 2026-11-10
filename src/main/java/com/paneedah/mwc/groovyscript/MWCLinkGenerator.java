package com.paneedah.mwc.groovyscript;

import com.cleanroommc.groovyscript.documentation.linkgenerator.BasicLinkGenerator;

import static com.paneedah.mwc.ProjectConstants.ID;
import static com.paneedah.mwc.ProjectConstants.VERSION;

/**
 * @author yor42
 * @version 2024-12-04
 * @since 0.2
 */
public final class MWCLinkGenerator extends BasicLinkGenerator {

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected String domain() {
        return "https://github.com/Cubed-Development/Modern-Warfare-Cubed/";
    }

    @Override
    protected String version() {
        return VERSION;
    }

    @Override
    protected String defaultBranch() {
        return "next";
    }
}
