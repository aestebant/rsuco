package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CachingItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.GenericItemSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

import subjectreco.recommender.subjectSimilarity.MultiSimilarity;

/**
 * Content based subjectreco.recommender for subjects that take a specific similarity metric
 *  
 * @author Aurora Esteban Toscano
 */
public class CBFSubject extends ARecommender {
	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel professors;
	private DataModel areas;
	private DataModel competences;
	
	private ItemSimilarity similarity;
	private Configuration configSim;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * @see IRecommender#execute(DataModel)
	 */
	@Override
	public void execute(DataModel model) {
		try {
			similarity = new CachingItemSimilarity(new GenericItemSimilarity(new MultiSimilarity(professors, areas, competences, configSim), model), model);
			
			log.info("Launching recommender");
			recommender = new CachingRecommender(new GenericItemBasedRecommender(model, similarity));
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * @see subjectreco.util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		// Standard configuration
		super.configure(config);

		log.info("Setting especific CBFSubject configuration");
		
		/*Double useProfessors = config.getDouble("similarity.professorsWeight");
		if (useProfessors > 0.0) {
			professors = mm.loadModel("professors");
			log.info("Professors information loaded");
		}*/
		
		professors = mm.loadModel("professors");
		log.info("Professors information loaded");
		
		Double useCompetences = config.getDouble("similarity.competencesWeight");
		if (useCompetences > 0.0) {
			competences = mm.loadModel("competences");
			log.info("Competences information loaded");
		}
		
		Double useAreas = config.getDouble("similarity.areaWeight");
		if (useAreas > 0.0) {
			areas = mm.loadModel("areas");
			log.info("Area information loaded");
		}
		
		configSim = config.subset("similarity");
	}
}
