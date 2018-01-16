package core;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import evaluator.Evaluator;
import util.ConfigLoader;
import util.IConfiguration;
import util.ModelManage;

/**
 * Evaluate a recommender configuration showing MAE, RMSE, precision and recall
 * @author Aurora Esteban Toscano
 */
class RunEval {
	
	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 1, "Must set a configuration file");
		
		// Load recommender configuration
		Configuration config = ConfigLoader.XMLFile(args[0]);
		
		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile("configuration/Model.xml");

		// Load model management
		ModelManage mm = new ModelManage();
		// Configure the ModelManage
		if (mm instanceof IConfiguration)
			((IConfiguration) mm).configure(configDM.subset("model"));

		DataModel model = mm.loadModel("ratings");
		
		Evaluator evaluator = new Evaluator(model);
		if (evaluator instanceof IConfiguration)
			((IConfiguration) evaluator).configure(config.subset("evaluator"));
			
		evaluator.execute();
		
		System.out.println("Configuration tested: " + config.getString("evaluator.recommender"));
		
		System.out.println("----Deviation----");
		System.out.println("RMSE:\t\t" + evaluator.getRMSE());
		System.out.println("MAE:\t\t" + evaluator.getMAE());
		
		System.out.println("----Statistics----");
		System.out.println("Precision:\t" + evaluator.getStats().getPrecision());
		System.out.println("Recall:\t\t" + evaluator.getStats().getRecall());
		System.out.println("F1-score:\t" + evaluator.getStats().getF1Measure());
		System.out.println("Fall-out:\t" + evaluator.getStats().getFallOut());
		System.out.println("nGCD:\t\t" + evaluator.getStats().getNormalizedDiscountedCumulativeGain());
		System.out.println("Reach:\t\t" + evaluator.getStats().getReach());
	}
}
