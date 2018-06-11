package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import subjectreco.util.ModelManage;

/**
 * Basic class for the particular implementation of the Mahout Recommender
 * 
 * @author Aurora Esteban Toscano
 */
public abstract class ARecommender implements IRecommender {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	// Default implementation of a subjectreco.recommender
	protected Recommender recommender;
	protected static ModelManage mm = null;
	
	protected Boolean normalize;
	protected DataModel normModel;
	
	protected static final Logger log = LoggerFactory.getLogger(ARecommender.class);
	
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Before apply subjectreco.recommender specific logic, normalize the ratings
	 */
	public void execute(DataModel model) {
		Preconditions.checkNotNull(mm, "ModelManage not inicializated");
		log.info("Recommender execution starting");
		
		if (normalize) {
			log.info("Normalizing ratings");
			normModel = mm.subtractiveNormalization(model);
			
		}
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

	public void setModelManage(ModelManage mm) {
		ARecommender.mm = mm;
	}
	
	/**
	 * @see subjectreco.util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		log.info("Loading general recommender configuration");
		
		normalize = config.getBoolean("normalize",false);
		
		/*// Load configuration of the model manager
		Configuration configDM = ConfigLoader.XMLFile("configuration/Model.xml");
		if (mm == null) {
			mm = new ModelManage(configDM);
			log.info("ModelManage inizializated");
		}*/
	}
}