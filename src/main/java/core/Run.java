package core;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import recommender.IRecommender;
import util.ConfigLoader;
import util.IConfiguration;
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
		Preconditions.checkArgument(args.length == 1, "Must set a configuration file");

		// By default, the log level of Mahout evaluator is INFO
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);

		// Load recommender configuration
		Configuration configReco = ConfigLoader.XMLFile(args[0]);

		// Instantiate the recommender
		IRecommender recommender = RecommenderLoader.instantiate(configReco);

		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile("configuration/Model.xml");

		// Load model management
		ModelManage mm = new ModelManage();
		// Configure the ModelManage
		if (mm instanceof IConfiguration)
			((IConfiguration) mm).configure(configDM.subset("model"));
		
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
				// TODO Auto-generated catch block
				e.printStackTrace();
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