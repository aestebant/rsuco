package recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.neighborhood.CachingUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;

import util.IConfiguration;

/**
 * Content based recommender for students: hybrid similarity with ratings,
 * scores and specialty
 * 
 * @author Aurora Esteban Toscano
 */
public class CBFStudent extends ARecommender implements IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////

	// Scores related to users and their preferences
	private DataModel scores;
	private DataModel specialties;

	private StudentSimilarity similarity;

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
		
		DataModel filterScores = mm.filterModel(scores,model);
		if (normalize)
			filterScores = mm.subtractiveNormalization(filterScores);
				
		// Hybrid similarity with all the data
		similarity.execute(normModel, filterScores, specialties);

		try {
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
		
		// Load scores and specialties of the users given by ratings
		scores = mm.loadModel("scores");		
		specialties = mm.loadModel("specialties");
		
		this.neighOpt = config.getInt("neighborhood.option");
		if (neighOpt == 1)
			this.neighSize = config.getInt("neighborhood.size");
		else if (neighOpt == 2)
			this.neighThres = config.getDouble("neighborhood.threshold");
		
		similarity = new StudentSimilarity();
		similarity.configure(config.subset("similarity"));
		
		
	}

}
