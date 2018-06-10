package evaluator;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;

import util.IConfiguration;
import util.ModelManage;

public interface IEvaluator extends IConfiguration {
	
	void setDataModel(DataModel model);
	
	void setRecommenderBuilder(File pathRecommender, ModelManage mm);
	void setRecommenderBuilder(Configuration configRecommender, ModelManage mm);
	
	void execute();
	void execute(Long seed);
	void execute(List<Long> seed);
	
	void setOrderedbyNPrefsSubjects(ModelManage mm);
	
	Map<String, Double[]> getResults();
}
