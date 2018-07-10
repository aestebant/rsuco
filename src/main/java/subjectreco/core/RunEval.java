package subjectreco.core;

import com.google.common.base.Preconditions;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;
import subjectreco.evaluator.IEvaluator;
import subjectreco.evaluator.KFoldEvaluator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;

import java.io.File;

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

        //IEvaluator eval = new SplitEvaluator();
        IEvaluator eval = new KFoldEvaluator();

        eval.configure(config.subset("evaluator"));

        eval.setRecommenderBuilder(new File(args[2]), mm);
        eval.setDataModel(model);

        eval.setOrderedbyNPrefsSubjects(mm);
        eval.execute((long) 123456);

		/*List<Long> seeds = new ArrayList<>(5);
		seeds.add(10L);
		seeds.add(20L);
		seeds.add(30L);
		eval.execute(seeds);*/
    }

}
