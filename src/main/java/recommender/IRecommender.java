package recommender;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;

import util.IConfiguration;

/**
 * General interface for the particular use in the problem of Mahout Recommender
 * 
 * @author Aurora Esteban Toscano
 */
public interface IRecommender extends IConfiguration {
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Execute the recommender
	 * 
	 * @param model
	 *            DataModel that relate users and items
	 */
	public void execute(DataModel model);

	/**
	 * @return desencapsulated Mahout Recommender
	 */
	public Recommender getRecommender();
}