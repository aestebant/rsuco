package subjectreco.evaluator;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.NoSuchItemException;
import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import subjectreco.recommender.IRecommender;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;
import subjectreco.util.RecommenderLoader;
import subjectreco.util.Reporter;

/**
 * Base code for the difference-based evaluation
 * @author Aurora Esteban Toscano
 */
public abstract class AEvaluator implements IEvaluator {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    protected static final Logger log = LoggerFactory.getLogger(AEvaluator.class);
    static Reporter reporter;

    RecommenderBuilder recoBuilder;
    private Boolean recoFromFile = false;
    private File recoPath;

    protected DataModel model;

    // Evaluation options
    private Boolean computeRMSE;
    private Boolean computeMAE;
    private Boolean computeBinaryClassif;
    Boolean useRandomSplit;
    private Boolean useSpecificThreshold;
    Boolean singleExecution = true;

    Random random;
    List<Long> orderedSubjects;
    double dataPercent;
    private HashMap<Long, Double> usersThresh;

    // Difference based errors
    private RunningAverage runMAE;
    private RunningAverage runRMSE;

    // Classification metrics
    private int tp;
    private int tn;
    private int fp;
    private int fn;

    Map<String, Double[]> results;

    AEvaluator() {
        reporter = new Reporter();
    }

    /**
     * Generate the RecommenderBuilder suitable for Recommender's class
     *
     * @param pathRecommender Path to the XML file with recommender configuration
     * @param mm              ModelManage instance
     */
    public void setRecommenderBuilder(File pathRecommender, ModelManage mm) {
        log.info("Extracting subjectreco.recommender configuration from {}", pathRecommender.getPath());

        recoPath = pathRecommender;
        Configuration recoConfig = ConfigLoader.XMLFile(recoPath);

        recoFromFile = true;

        setRecommenderBuilder(recoConfig, mm);
    }

    /**
     * Generate the RecommenderBuilder suitable for Recommender's class
     *
     * @param configRecommender Configuration of the recommender
     * @param mm                ModelManage instance
     */
    public void setRecommenderBuilder(Configuration configRecommender, ModelManage mm) {
        log.info("Setting the subjectreco.recommender configuration");

        // Instantiate the recommender
        final IRecommender recommender = RecommenderLoader.instantiate(configRecommender, mm);

        // Lambda constructor for RecommenderBuilder
        recoBuilder = model -> {
            recommender.execute(model);
            return recommender.getRecommender();
        };
    }

    /**
     * Configure the datamodel with users and prefs used in evaluation
     *
     * @param model DataModel
     */
    public void setDataModel(DataModel model) {
        this.model = model;

        // Compute threshold between positive and negative prefs for users.
        if (computeBinaryClassif)
            setUsersThresholds();
    }

    /**
     * Main class in execution when there are several evaluations
     *
     * @param seeds Seeds for random generations in each execution
     */
    public void execute(List<Long> seeds) {
        singleExecution = false;

        reporter.startExperiment();
        String starting = "Starting experiment\n"
                + "Random split, " + useRandomSplit + "\n"
                + "Specific threshold, " + useSpecificThreshold + "\n"
                + "Percentage of data, " + dataPercent;

        log.info(starting);
        reporter.addLog(starting);
        if (recoFromFile)
            reporter.addLog("Recommender configuration, " + recoPath.getAbsolutePath());
        else
            reporter.addLog("Recommender configuration, automatically generated");

        Map<String, RunningAverageAndStdDev> foldsResults = new HashMap<>();

        for (int i = 0; i < seeds.size(); ++i) {
            log.info("Beginning execution with seed {}/{} ({})", i + 1, seeds.size(), seeds.get(i));
            reporter.addLog("Beginning execution with seed " + (i + 1) + "/" + seeds.size() + " (" + seeds.get(i) + ")");

            execute(seeds.get(i));

            for (String key : results.keySet()) {
                if (i == 0)
                    foldsResults.put(key, new FullRunningAverageAndStdDev());
                foldsResults.get(key).addDatum(results.get(key)[0]);
            }
        }
        for (String key : results.keySet()) {
            Double[] total = {foldsResults.get(key).getAverage(), foldsResults.get(key).getStandardDeviation()};
            results.put(key, total);
        }

        log.info("All seeds result:\n" + printResults());
        reporter.addLog("All seeds result");
        reporter.addResults(results);

        reporter.finishExperiment();
    }

    /**
     * Main class in execution when there is one execution
     *
     * @param seed Seed for random generations
     */
    public void execute(Long seed) {
        random = new Random(seed);
        execute();
    }

    /**
     * Base of execution
     */
    public void execute() {
        Preconditions.checkNotNull(recoBuilder, "ERROR: RecommenderBuilder doesn't been configurate");
        Preconditions.checkNotNull(model, "ERROR: DataModel doesn't been configurate");

        if (singleExecution) {
            reporter.startExperiment();
            String starting = "Starting experiment\n"
                    + "Random split, " + useRandomSplit + "\n"
                    + "Specific threshold, " + useSpecificThreshold + "\n"
                    + "Percentage of data, " + dataPercent;

            log.info(starting);
            reporter.addLog(starting);
            if (recoFromFile)
                reporter.addLog("Recommender configuration, " + recoPath.getAbsolutePath());
            else
                reporter.addLog("Recommender configuration, automatically generated");
        }

        if (random == null)
            random = new Random();
    }

    /**
     * Create a subjects list in descending order by the number of preferences each one has.
     *
     * @param mm A ModelManage instance to obtain the information of the subjects
     */
    public void setOrderedbyNPrefsSubjects(ModelManage mm) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // Query of subject id and content
            conn = mm.getDataSource().getConnection();
            stmt = conn.prepareStatement("SELECT s.id, count(r.rating) as cuenta"
                    + " from uco_punctuated_subject r, uco_subject s"
                    + " where s.id = r.subject_id group by r.subject_id order by cuenta desc;");
            rs = stmt.executeQuery();
            orderedSubjects = new ArrayList<>();
            while (rs.next()) {
                orderedSubjects.add(rs.getLong(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            IOUtils.quietClose(rs, stmt, conn);
        }
    }

    /**
     * Set up threshold between good and bad ratings for users. By default is 3 (assume rate scale in [1, 5].
     * If the option useSpecificThreshold is set, threshold will be computed for each user on his ratings.
     */
    private void setUsersThresholds() {
        usersThresh = new HashMap<>();
        LongPrimitiveIterator it;
        try {
            it = model.getUserIDs();
            while (it.hasNext()) {
                long userID = it.nextLong();
                // Set generic threshold to 3 (assume ratings in [1,5])
                double threshold = 3;
                if (useSpecificThreshold) {
                    PreferenceArray prefs = model.getPreferencesFromUser(userID);
                    threshold = computeThreshold(prefs);
                }
                usersThresh.put(userID, threshold);
            }
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compute the threshold for a specific user.
     *
     * @param prefs Preferences of the user
     * @return Threshold
     */
    private double computeThreshold(PreferenceArray prefs) {
        if (prefs.length() < 2) {
            // Not enough data points -- return a threshold that allows everything
            return Double.NEGATIVE_INFINITY;
        }
        RunningAverageAndStdDev stdDev = new FullRunningAverageAndStdDev();
        int size = prefs.length();
        for (int i = 0; i < size; i++) {
            stdDev.addDatum(prefs.getValue(i));
        }
        double[] result = new double[2];
        result[0] = stdDev.getAverage();
        result[1] = stdDev.getStandardDeviation();
        return result[0];
    }

    /**
     * Compute the evaluation itself
     *
     * @param testPrefs   Percentage of the dataModel used for test
     * @param recommender Recommender
     */
    void getEvaluation(FastByIDMap<PreferenceArray> testPrefs, Recommender recommender) {

        // Make evaluation parallelizable by users
        Collection<Callable<Void>> estimateCallables = Lists.newArrayList();
        AtomicInteger noEstimateCounter = new AtomicInteger();
        for (Map.Entry<Long, PreferenceArray> entry : testPrefs.entrySet()) {
            estimateCallables.add(
                    new PreferenceEstimateCallable(recommender, entry.getKey(), entry.getValue(), noEstimateCounter));
        }

        // Intermediate results
        runMAE = new FullRunningAverage();
        runRMSE = new FullRunningAverage();
        tp = 0;
        tn = 0;
        fp = 0;
        fn = 0;

        log.info("Beginning evaluation of {} users", estimateCallables.size());

        RunningAverageAndStdDev timing = new FullRunningAverageAndStdDev();
        // Parallel execution
        try {
            deploy(estimateCallables, noEstimateCounter, timing);
        } catch (TasteException e) {
            e.printStackTrace();
        }

        // Obtain final results
        results = new HashMap<>();
        if (computeBinaryClassif) {
            Double[] acc = {(double) (tp + tn) / (tp + tn + fp + fn), 0.0};
            results.put("Accuracy", acc);
            Double[] prec = {(double) (tp) / (tp + fp), 0.0};
            results.put("Precision", prec);
            Double[] rec = {(double) (tp) / (tp + fn), 0.0};
            results.put("Recall", rec);
        }
        if (computeRMSE) {
            Double[] rmse = {Math.sqrt(runRMSE.getAverage()), 0.0};
            results.put("RMSE", rmse);
        }
        if (computeMAE) {
            Double[] mae = {runMAE.getAverage(), 0.0};
            results.put("MAE", mae);

        }
    }

    /**
     * Manage the parallelization of the evaluation.
     *
     * @param callables         a list with all potencial estimations by user
     * @param noEstimateCounter count the times that recommender can't obtain a recommendation for a user
     * @param timing            count time employed
     * @throws TasteException Mahout exception
     */
    private static void deploy(Collection<Callable<Void>> callables, AtomicInteger noEstimateCounter,
                               RunningAverageAndStdDev timing) throws TasteException {

        Collection<Callable<Void>> wrappedCallables = wrapWithStatsCallables(callables, noEstimateCounter, timing);
        int numProcessors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numProcessors);

        log.info("Starting timing of {} tasks in {} threads", wrappedCallables.size(), numProcessors);

        try {
            List<Future<Void>> futures = executor.invokeAll(wrappedCallables);
            // Go look for exceptions here, really
            for (Future<Void> future : futures) {
                future.get();
            }
        } catch (InterruptedException ie) {
            throw new TasteException(ie);
        } catch (ExecutionException ee) {
            throw new TasteException(ee.getCause());
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new TasteException(e.getCause());
        }
    }

    /**
     * Add the stats to the computation of the estimation of ratings
     *
     * @param callables         a list with all potencial estimations by user
     * @param noEstimateCounter count the times that recommender can't obtain a recommendation for a user
     * @param timing            count time employed
     * @return Collection with the stats
     */
    private static Collection<Callable<Void>> wrapWithStatsCallables(Iterable<Callable<Void>> callables,
                                                                     AtomicInteger noEstimateCounter, RunningAverageAndStdDev timing) {
        Collection<Callable<Void>> wrapped = Lists.newArrayList();
        int count = 0;
        for (Callable<Void> callable : callables) {
            boolean logStats = count++ % 1000 == 0; // log every 1000 or so iterations
            wrapped.add(new StatsCallable(callable, logStats, timing, noEstimateCounter, reporter));
        }
        return wrapped;
    }

    /**
     * Class for make the estimation of ratings for an user parallelizable.
     */
    private final class PreferenceEstimateCallable implements Callable<Void> {

        private final Recommender recommender;
        private final long testUserID;
        private final PreferenceArray prefs;
        private final AtomicInteger noEstimateCounter;

        private PreferenceEstimateCallable(Recommender recommender, long testUserID, PreferenceArray prefs,
                                           AtomicInteger noEstimateCounter) {
            this.recommender = recommender;
            this.testUserID = testUserID;
            this.prefs = prefs;
            this.noEstimateCounter = noEstimateCounter;
        }

        /**
         * Obtain estimation for an user and compare with real preferences.
         *
         * @return Void
         * @throws TasteException Mahout exception
         */
        @Override
        public Void call() throws TasteException {
            double threshold = -1;
            if (computeBinaryClassif)
                threshold = usersThresh.get(testUserID);
            for (Preference realPref : prefs) {
                float estimatedPref = Float.NaN;
                try {
                    estimatedPref = recommender.estimatePreference(testUserID, realPref.getItemID());
                } catch (NoSuchUserException nsue) {
                    // It's possible that an item exists in the test data but not training data in
                    // which case NSEE will be thrown. Just ignore it and move on.
                    log.info("User exists in test data but not training data: {}", testUserID);
                } catch (NoSuchItemException nsie) {
                    log.info("Item exists in test data but not training data: {}", realPref.getItemID());
                }
                if (Float.isNaN(estimatedPref)) {
                    noEstimateCounter.incrementAndGet();
                } else {
                    if (computeMAE) {
                        double diff = realPref.getValue() - estimatedPref;
                        runMAE.addDatum(Math.abs(diff));
                    }
                    if (computeRMSE) {
                        double diff = realPref.getValue() - estimatedPref;
                        runRMSE.addDatum(diff * diff);
                    }
                    if (computeBinaryClassif) {
                        if (estimatedPref >= threshold && realPref.getValue() >= threshold)
                            tp++;
                        else if (estimatedPref >= threshold && realPref.getValue() < threshold)
                            fp++;
                        else if (estimatedPref < threshold && realPref.getValue() >= threshold)
                            fn++;
                        else
                            tn++;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Return map with all computed metrics
     *
     * @return Map<metric   ,       result>
     */
    public Map<String, Double[]> getResults() {
        return results;
    }

    String printResults() {
        StringBuilder res = new StringBuilder();
        for (String key : results.keySet()) {
            res.append(key).append(": ").append(results.get(key)[0]).append(" +/- ").append(results.get(key)[1]).append("\n");
        }
        return res.toString();
    }

    /**
     * Obtain configuration of base params for the evaluation
     * @param config Configuration
     */
    public void configure(Configuration config) {
        computeRMSE = config.getBoolean("computeRMSE");
        computeMAE = config.getBoolean("computeMAE");
        computeBinaryClassif = config.getBoolean("computeBinaryClassification");
        useRandomSplit = config.getBoolean("useRandomSplit");
        useSpecificThreshold = config.getBoolean("useSpecificThreshold");
        dataPercent = config.getDouble("dataPercent");

        reporter.configure(config.subset("reporter"));
    }
}
