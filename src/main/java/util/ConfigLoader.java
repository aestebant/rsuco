package util;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.fluent.XMLBuilderParameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

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
	 * Constructor that sets the configuration file
	 * 
	 * @param inputPath
	 *            inputPath of the file with the configuration to load
	 */
	public static Configuration XMLFile(String path) {
		Parameters params = new Parameters();
		
		XMLBuilderParameters px = params.xml();
		
		FileBasedConfigurationBuilder<XMLConfiguration> builder = new FileBasedConfigurationBuilder<XMLConfiguration>(
				XMLConfiguration.class);
		builder.configure(px.setFileName(path));
		
		try {
			configuration = builder.getConfiguration();
		} catch (ConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return configuration;
	}
}