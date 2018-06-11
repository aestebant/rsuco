package subjectreco.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import subjectreco.evaluator.IEvaluator;
import subjectreco.evaluator.SplitEvaluator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.IConfiguration;
import subjectreco.util.ModelManage;

public class RunEval {

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 3, "Use: <DB configuration.xml> <Evaluation configuration.xml> <RS configuration.xml>");
		
		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));
		
		// Load model management
		ModelManage mm = new ModelManage(configDM);
		
		// Load evaluation configuration
		Configuration config = ConfigLoader.XMLFile(new File(args[1]));

		DataModel model = mm.loadModel("ratings");
		IEvaluator eval = new SplitEvaluator();
		//IEvaluator eval = new KFoldEvaluator();
		if (eval instanceof IConfiguration)
			((IConfiguration) eval).configure(config.subset("subjectreco.evaluator"));
		
		eval.setRecommenderBuilder(new File(args[2]), mm);
		eval.setDataModel(model);
		
		eval.setOrderedbyNPrefsSubjects(mm);
		//eval.execute();
		List<Long> seeds = new ArrayList<>(5);
		seeds.add(10L);
		seeds.add(20L);
		seeds.add(30L);
		
		eval.execute(seeds);
	}

}
