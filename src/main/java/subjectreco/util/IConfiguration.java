package subjectreco.util;

import org.apache.commons.configuration2.Configuration;

/**
 * Make available configuration in a class
 * 
 * @author Rafael Barbudo Lunar
 */

public interface IConfiguration {
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Load the configuration given in a class
	 * 
	 * @param config
	 *            the configuration class
	 */
	public void configure(Configuration config);
}
