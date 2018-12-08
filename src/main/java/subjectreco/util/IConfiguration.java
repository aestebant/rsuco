package subjectreco.util;

import org.apache.commons.configuration2.Configuration;

/**
 * Interface that classes configurated via XML must implement.
 *
 * @author Aurora Esteban Toscano
 */

public interface IConfiguration {
    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////
    void configure(Configuration config);
}
