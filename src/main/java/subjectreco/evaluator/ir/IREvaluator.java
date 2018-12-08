package subjectreco.evaluator.ir;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.IRStatistics;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderIRStatsEvaluator;
import org.apache.mahout.cf.taste.model.DataModel;

import subjectreco.recommender.IRecommender;
import subjectreco.util.IConfiguration;
import subjectreco.util.ModelManage;
import subjectreco.util.RecommenderLoader;

import java.util.Random;

/**
 * Evaluate information retrieval stats of a recommender
 *
 * @author Aurora Esteban Toscano
 */
public class IREvaluator implements IConfiguration {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private RecommenderBuilder recommenderBuilder;
    private DataModel model;

    // Statistics of the recommender
    private RecommenderIRStatsEvaluator evalStats;
    // Mahout interface for save statistics
    private IRStatistics stats;

    // Minimum value of preference to consider a item relevant for a user
    private double threshold;
    // Number of items to evaluate the relevance
    private int at;
    // Percentage of dataModel to compute difference
    private double compPrcnSt;

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////
    public IREvaluator(DataModel model) {
        this.model = model;

        // By default, the log level of Mahout subjectreco.evaluator is INFO
        org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
        l.setLevel(org.apache.log4j.Level.WARN);
    }

    /**
     * Base code for run the evaluation and compute the stats
     *
     * @param seed Get deterministic solutions.
     */
    public void execute(long seed) {
        Random random = new Random(seed);
        evalStats = new GenericRecommenderIRStatsEvaluator(random);
        setStats();
    }

    /**
     * Base code for run the evaluation and compute the stats
     */
    public void execute() {
        Random random = new Random();
        evalStats = new GenericRecommenderIRStatsEvaluator(random);
        setStats();
    }


    /**
     * Configure suitable constructor of the recommender given by its configuration
     *
     * @param recommenderConf Configuration of the recommender
     * @param mm              ModelManage
     */
    public void setRecommenderBuilder(Configuration recommenderConf, ModelManage mm) {

        final IRecommender recommender = RecommenderLoader.instantiate(recommenderConf, mm);

        // Lambda constructor for recommenderBuilder
        recommenderBuilder = model -> {
            recommender.execute(model);
            return recommender.getRecommender();
        };
    }

    /**
     * Compute information retrieval stats for the evaluation
     */
    private void setStats() {
        try {
            stats = evalStats.evaluate(recommenderBuilder, null, model, null, at, threshold, compPrcnSt);
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Obtain the computed stats
     *
     * @return Mahout IRStatics
     */
    public IRStatistics getStats() {
        return stats;
    }

    /**
     * Obtain the configuration for the evaluation
     *
     * @param config Configuration
     */
    public void configure(Configuration config) {
        at = config.getInt("at");
        compPrcnSt = config.getDouble("dataPercent");
        threshold = config.getDouble("threshold", Double.NaN);
    }
}
