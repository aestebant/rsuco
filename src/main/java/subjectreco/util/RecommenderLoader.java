package subjectreco.util;

import org.apache.commons.configuration2.Configuration;

import subjectreco.recommender.IRecommender;

/**
 * Manage a IRecommender instantiation
 * 
 * @author Aurora Esteban Toscano
 */
public class RecommenderLoader {
	
	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private static IRecommender recommender;
	
	//////////////////////////////////////////////
	// ----------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Instantiate a IRecommender given its configuration.
	 * @param config
	 * @return the IRecommender created
	 */
	@SuppressWarnings("unchecked")
	public static IRecommender instantiate(Configuration config, ModelManage mm) {
		// Get the name of the class of the IRecommender
		Class<? extends IRecommender> recoClass = null;
		try {
			recoClass = (Class<? extends IRecommender>) Class
					.forName(config.getString("recommender[@name]"));
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// Instantiate the given class subjectreco.recommender
		try {
			recommender = recoClass.newInstance();
		} catch (InstantiationException | IllegalAccessException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		recommender.setModelManage(mm);
		
		// Configure the subjectreco.recommender
		if (recommender instanceof IConfiguration) {
			((IConfiguration) recommender).configure(config.subset("recommender"));
		}
		return recommender;
	}
}
