package recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;

import util.ConfigLoader;
import util.ModelManage;

/**
 * Basic class for the particular implementation of the Mahout Recommender
 * 
 * @author Aurora Esteban Toscano
 */
public abstract class ARecommender implements IRecommender {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	// Default implementation of a recommender
	protected Recommender recommender;
	protected ModelManage mm;
	
	protected Boolean normalize;
	protected DataModel normModel;
	
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Before apply recommender specific logic, normalize the ratings
	 */
	public void execute(DataModel model) {
		if (normalize)
			normModel = mm.subtractiveNormalization(model);
		else
			normModel = model;
	}
	
	/**
	 * By default will have the same behavior of delegate, but undo the
	 * normalization in estimations
	 */
	public Recommender getRecommender() {
		if (recommender == null) {
			System.err.println("Recommender not executed, use execute(DataModel) before");
			return null;
		}
		else
			return recommender;
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		normalize = config.getBoolean("normalize",false);
		
		// Load configuration of the model manager
		Configuration configDM = ConfigLoader.XMLFile("configuration/Model.xml");
		mm = new ModelManage(configDM);
	}
}