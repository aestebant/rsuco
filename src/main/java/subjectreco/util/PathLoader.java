package subjectreco.util;

import java.io.File;

public class PathLoader {
	public static File getConfigPath(String name) {
		File configPath = new File(System.getProperty("user.dir"));
				
		File result = new File(configPath + File.separator + "configuration" + File.separator + name);
		
		return result;
	}
}
