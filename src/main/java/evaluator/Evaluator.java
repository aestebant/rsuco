package evaluator;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderIRStatsEvaluator;
import org.apache.mahout.cf.taste.model.DataModel;
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
public class Evaluator implements IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private RecommenderBuilder recommenderBuilder;
	private DataModel model;

	private long seed; // Get deterministic solutions

	// Difference based errors
	private AverageAbsoluteDifferenceRecommenderEvaluator evalMAE;
	private RMSRecommenderEvaluator evalRMSE;
	private double mae, rmse;

	// Training percentage => 1 - train -> test
	private double trainPrcn;
	// Percentage of dataModel to compute difference
	private double compPrcnSc;

	// Statistics of the recommender
	private RecommenderIRStatsEvaluator evalStats;
	// Minimum value of preference to consider a item relevant for a user
	private double threshold; // Automatic threshold
	private int at; // Number of items to evaluate the relevance
	private double compPrcnSt; // Percentage of dataModel to compute difference
	private IRStatistics stats; // Mahout interface for save statistics

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	public Evaluator(DataModel model) {
		this.model = model;

		// By default, the log level of Mahout evaluator is INFO
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);
	}

	/**
	 * Execute the evaluator by a given configuration
	 */
	public void execute() {
		evalMAE = new AverageAbsoluteDifferenceRecommenderEvaluator(seed);
		evalRMSE = new RMSRecommenderEvaluator(seed);
		evalStats = new GenericRecommenderIRStatsEvaluator(seed);

		setMAE();
		setRMSE();
		setStats();
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

	/**
	 * Evaluate Mean Average Error
	 */
	private void setMAE() {
		try {
			mae = evalMAE.evaluate(recommenderBuilder, null, model, trainPrcn, compPrcnSc);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public double getMAE() {
		return mae;
	}

	/**
	 * Evaluate Root Mean Squared Error
	 */
	private void setRMSE() {
		try {
			rmse = evalRMSE.evaluate(recommenderBuilder, null, model, trainPrcn, compPrcnSc);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public double getRMSE() {
		return rmse;
	}

	/**
	 * Get the statistics of the recommender
	 */
	private void setStats() {
		try {
			stats = evalStats.evaluate(recommenderBuilder, null, model, null, at, threshold, compPrcnSt);
		} catch (TasteException e) {
			e.printStackTrace();
		}
	}

	public IRStatistics getStats() {
		return stats;
	}

	public void setSeed(long seed) {
		this.seed = seed;
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	public void configure(Configuration config) {
		trainPrcn = config.getDouble("error.trainPercent");
		compPrcnSc = config.getDouble("error.compPercent");

		at = config.getInt("stats.at");
		compPrcnSt = config.getDouble("stats.compPercent");
		threshold = config.getDouble("stats.threshold", Double.NaN);

		// If user doesn't specify a seed, a -1 value indicate to the algorithm that
		// must be random.
		seed = config.getLong("seed", -1);

		String confPathRecommender = config.getString("recommender");
		Configuration recommenderConf = ConfigLoader.XMLFile(confPathRecommender);

		// Instantiate recommender
		setRecommenderBuilder(recommenderConf);
	}
}
