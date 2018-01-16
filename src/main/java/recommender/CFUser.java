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

/**
 * A particular recommender based in user
 * 
 * @author Aurora Esteban Toscano
 */
public class CFUser extends ARecommender {
	
	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	
	private UserSimilarity similarity;
	private Class<? extends UserSimilarity> iSimilarity;
	
	private UserNeighborhood neighborhood;
	private int neighOpt;
	private int neighSize;
	private double neighThres;
	
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * (non-Javadoc)
	 * @see recommender.ARecommender#engine()
	 */
	@Override
	public void execute(DataModel model) {
		super.execute(model);
		
		try {
			similarity = new CachingUserSimilarity(iSimilarity.getDeclaredConstructor(DataModel.class).newInstance(normModel), normModel);
			
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
	 * (non-Javadoc)
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void configure(Configuration config) {
		// Standard configuration
		super.configure(config);
		
		this.neighOpt = config.getInt("neighborhood.option");
		if (neighOpt == 1) // NNearest
			this.neighSize = config.getInt("neighborhood.size");
		else if (neighOpt == 2) // Threshold
			this.neighThres = config.getDouble("neighborhood.threshold");
		
		try {
			this.iSimilarity = (Class<? extends UserSimilarity>) Class.forName(config.getString("similarity"));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}