package core;

import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import recommender.IRecommender;
import util.ConfigLoader;
import util.ModelManage;
import util.RecommenderLoader;

/**
 * Make n recommendations to all users given a recommender configuration
 * @author Aurora Esteban Toscano
 */
class Run {
	
	static int nRecommendations = 3;
	static long userID = -1;

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 2, "Use: <bd configuration.xml> <rs configuration.xml>");

		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));
		
		// Load recommender configuration
		Configuration configReco = ConfigLoader.XMLFile(new File(args[1]));

		// Load model management
		ModelManage mm = new ModelManage(configDM);
		
		// Instantiate the recommender
		IRecommender recommender = RecommenderLoader.instantiate(configReco, mm);
		
		DataModel model = mm.loadModel("ratings");
		
		recommender.execute(model);

		System.out.println("User | Recommendations");
		if (userID == -1) {
			LongPrimitiveIterator users = null;
			try {
				users = recommender.getRecommender().getDataModel().getUserIDs();
				while (users.hasNext()) {
					long id = users.nextLong();
					System.out.println(id + " --> " + recommender.getRecommender().recommend(id, nRecommendations));
				}
			} catch (TasteException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		else {
			try {
				System.out.println(userID + " --> " + recommender.getRecommender().recommend(userID, nRecommendations));
			} catch (TasteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}