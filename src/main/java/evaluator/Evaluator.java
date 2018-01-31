package evaluator;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.recommender.Recommender;

import recommender.IRecommender;
import util.ConfigLoader;
import util.IConfiguration;
import util.RecommenderLoader;

/**
 * Evaluate a recommender
 * 
 * @author Aurora Esteban Toscano
 */
public class Evaluator extends AKFoldRecommenderEvaluator2 implements IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private RecommenderBuilder recommenderBuilder;

	// Difference based errors
	private RunningAverageAndStdDev mae;
	private RunningAverageAndStdDev rmse;
	
	// Number of folds in cross-validation
	private int nFolds;
	// Percentage of dataModel to compute difference
	private double compPrcnSc;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	public Evaluator(long seed) {
		super(seed);
		reset();

		// By default, the log level of Mahout evaluator is INFO
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);
	}

	/**
	 * Execute the evaluator by a given configuration
	 */
	public Map<String, Double> execute(DataModel model) {
		try {
			return evaluate(recommenderBuilder, null, model, nFolds, compPrcnSc);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Generate the RecommenderBuilder suitable for Recommender's class
	 * 
	 * @param evalConf
	 *            Configuration of the recommender
	 */
	public void setRecommenderBuilder(Configuration recommenderConf) {
		// Instantiate the recommender
		IRecommender recommender = RecommenderLoader.instantiate(recommenderConf);

		recommenderBuilder = new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel model) throws TasteException {
				recommender.execute(model);
				return recommender.getRecommender();
			}

		};
	}

	@Override
	protected void reset() {
		accuracy = new FullRunningAverageAndStdDev();
		precission = new FullRunningAverageAndStdDev();
		recall = new FullRunningAverageAndStdDev();
		
		mae = new FullRunningAverageAndStdDev();
		rmse = new FullRunningAverageAndStdDev();
	}

	@Override
	protected void processOneEstimate(float estimatedPreference, Preference realPref) {
		double diff = realPref.getValue() - estimatedPreference;
		mae.addDatum(Math.abs(diff));
		rmse.addDatum(diff*diff);
	}

	@Override
	protected Map<String, Double> computeFinalEvaluation() {
		Map<String, Double> result = new HashMap<String, Double>();
		
		result.put("mae", mae.getAverage());
		result.put("rmse", Math.sqrt(rmse.getAverage()));
		
		return result;
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	public void configure(Configuration config) {
		nFolds = config.getInt("error.nFolds");
		compPrcnSc = config.getDouble("error.compPercent");

		/*// If user doesn't specify a seed, a -1 value indicate to the algorithm that
		// must be random.
		seed = config.getLong("seed", -1);*/

		String confPathRecommender = config.getString("recommender");
		Configuration recommenderConf = ConfigLoader.XMLFile(confPathRecommender);

		// Instantiate recommender
		setRecommenderBuilder(recommenderConf);
	}
}
