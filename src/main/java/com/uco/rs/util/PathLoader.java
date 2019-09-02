package com.uco.rs.util;

import java.io.File;

/**
 * Give the route to configuration folder
 */
public class PathLoader {

    public static File getConfigPath(String name) {
        File configPath = new File(System.getProperty("user.dir"));

        return new File(configPath + File.separator + "configuration" + File.separator + name);
    }
}
