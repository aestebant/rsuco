package subjectreco.recommender;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;

import subjectreco.util.IConfiguration;
import subjectreco.util.ModelManage;

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
	 * Execute the subjectreco.recommender
	 * 
	 * @param model
	 *            DataModel that relate users and items
	 */
	public void execute(DataModel model);
	
	public void setModelManage(ModelManage mm);

	/**
	 * @return desencapsulated Mahout Recommender
	 */
	public Recommender getRecommender();
}