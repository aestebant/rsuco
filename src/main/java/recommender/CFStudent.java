package recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.neighborhood.CachingUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import util.IConfiguration;

/**
 * Content based recommender for students: hybrid similarity with ratings,
 * grades and specialty
 * 
 * @author Aurora Esteban Toscano
 */
public class CFStudent extends ARecommender implements IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////

	// Scores related to users and their preferences
	private DataModel grades = null;
	private DataModel branches = null;

	private UserSimilarity similarity;
	private Configuration configSim;

	private UserNeighborhood neighborhood;
	private int neighOpt;
	private int neighSize;
	private double neighThres;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * @see recommender.ARecommender#execute()
	 */
	@Override
	public void execute(DataModel model) {
		super.execute(model);
		
		try {
			// Hybrid similarity with all the data
			similarity = new CachingUserSimilarity(new StudentSimilarity(normModel, grades, branches, configSim), model);
			
			log.info("Creating neighborhood");
			switch (neighOpt) {
			case 1:
				this.neighborhood = new CachingUserNeighborhood(new NearestNUserNeighborhood(neighSize, similarity, normModel), normModel);
				break;
			
			case 2:
				this.neighborhood = new CachingUserNeighborhood(new ThresholdUserNeighborhood(neighThres, similarity, normModel), normModel);
				break;
			
			default:
				System.err.println("Neighborhood option does not exists");
				System.exit(1);
			}
			
			log.info("Launching recommender");
			recommender = new CachingRecommender(new GenericUserBasedRecommender(model, neighborhood, similarity));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		// Standard configuration
		super.configure(config);
		
		log.info("Loading specific CFStudent configuration");
		
		// Load grades and branches of the users given by ratings
		Double useGrades = config.getDouble("similarity.gradesWeight");
		if (useGrades > 0.0) {
			log.info("Loading grades data model");
			grades = mm.loadModel("grades");
			
			if (normalize) {
				log.info("Normalizing grades");
				grades = mm.subtractiveNormalization(grades);
			}
		}
		
		Double useBranch = config.getDouble("similarity.branchWeight");
		if (useBranch > 0.0) {
			log.info("Loading branches data model");
			branches = mm.loadModel("branches");
		}
		
		this.neighOpt = config.getInt("neighborhood.option");
		if (neighOpt == 1)
			this.neighSize = config.getInt("neighborhood.size");
		else if (neighOpt == 2)
			this.neighThres = config.getDouble("neighborhood.threshold");
		
		configSim = config.subset("similarity");
	}

}
