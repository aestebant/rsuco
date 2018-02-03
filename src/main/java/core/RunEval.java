package core;

import java.util.Map;

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
		ModelManage mm = new ModelManage(configDM);

		DataModel model = mm.loadModel("ratings");
		
		Evaluator evaluator = new Evaluator(13);
		if (evaluator instanceof IConfiguration)
			((IConfiguration) evaluator).configure(config.subset("evaluator"));
			
		Map<String, Double> results = evaluator.execute(model, mm);
		
		System.out.println("Configuration tested: " + config.getString("evaluator.recommender"));
		
		for (String key : results.keySet())
			System.out.println(key + "-> " + results.get(key));
	}
}
