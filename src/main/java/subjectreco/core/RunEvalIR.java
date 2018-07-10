package subjectreco.core;

import java.io.File;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import subjectreco.evaluator.IREvaluator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;

/**
 * Evaluate a subjectreco.recommender configuration by Information Retrieval stats
 * @author Aurora Esteban Toscano
 */
class RunEvalIR {
	
	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 3, "Use: <DB configuration.xml> <Evaluation configuration.xml> <RS configuration.xml>");
		
		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));

		// Load model management
		ModelManage mm = new ModelManage(configDM);

		DataModel model = mm.loadModel("ratings");
		
		// Load recommender configuration
		Configuration config = ConfigLoader.XMLFile(new File(args[1]));
		
		IREvaluator evaluator = new IREvaluator(model);
		evaluator.configure(config.subset("irstats"));

		// Load recommender
		Configuration configRS = ConfigLoader.XMLFile(new File(args[2]));
		evaluator.setRecommenderBuilder(configRS, mm);
			
		evaluator.execute();
		
		IRStatistics results = evaluator.getStats();
		
		System.out.println("nDCG:\t" + results.getNormalizedDiscountedCumulativeGain());
	}
}
