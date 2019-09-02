package com.uco.rs.util;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.XMLBuilderParameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;

/**
 * Load configuration from a XML file
 *
 * @author Aurora Esteban Toscano
 */

public class ConfigLoader {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private static Configuration configuration;

    //////////////////////////////////////////////
    // ----------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Encapsulate logic of loading XML file
     */
    public static Configuration XMLFile(File path) {

        Parameters params = new Parameters();

        XMLBuilderParameters px = params.xml();

        FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<>(XMLConfiguration.class);
        builder.configure(px.setFile(path));

        try {
            configuration = builder.getConfiguration();
        } catch (ConfigurationException e) {
            e.printStackTrace();
        }

        return configuration;
    }
}