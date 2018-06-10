package evaluator;

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

import recommender.IRecommender;
import util.ConfigLoader;
import util.ModelManage;
import util.RecommenderLoader;
import util.Reporter;

public abstract class AEvaluator implements IEvaluator {
	
	protected static final Logger log = LoggerFactory.getLogger(AEvaluator.class);
	
	protected RecommenderBuilder recoBuilder;
	private Boolean recoFromFile = false;
	protected File recoPath;
	protected static Reporter reporter;
	
	protected Boolean computeRMSE;
	protected Boolean computeMAE;
	protected Boolean computeBinaryClassif;
	protected Boolean useRandomSplit;
	protected Random random;
	protected List<Long> orderedSubjects;
	protected Boolean useSpecificThreshold;
	protected DataModel model;
	protected double dataPercent;

	protected HashMap<Long, Double> usersThresh;
	
	// Difference based errors
	private RunningAverage runMae;
	private RunningAverage runRmse;
	
	// Classification metrics
	private int tp;
	private int tn;
	private int fp;
	private int fn;
	
	protected Boolean singleExecution = true;
	
	Map<String, Double[]> results;
	
	public AEvaluator() {
		reporter = new Reporter();
	}
	
	/**
	 * Generate the RecommenderBuilder suitable for Recommender's class
	 * 
	 * @param evalConf
	 *            Configuration of the recommender
	 */
	public void setRecommenderBuilder(File pathRecommender, ModelManage mm) {
		log.info("Extracting recommender configuration from {}", pathRecommender.getPath());
		
		recoPath = pathRecommender;
		Configuration recoConfig = ConfigLoader.XMLFile(recoPath);
		
		recoFromFile = true;
		
		setRecommenderBuilder(recoConfig, mm);
	}
	
	public void setRecommenderBuilder(Configuration configRecommender, ModelManage mm) {
		log.info("Setting the recommender configuration");
		
		// Instantiate the recommender
		final IRecommender recommender = RecommenderLoader.instantiate(configRecommender, mm);

		recoBuilder = new RecommenderBuilder() {
			@Override
			public Recommender buildRecommender(DataModel model) throws TasteException {
				recommender.execute(model);
				return recommender.getRecommender();
			}
		};
	}
	
	public void setDataModel(DataModel model) {
		this.model = model;
		
		if (computeBinaryClassif)
			setUsersThresholds();
	}
	
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
			log.info("Beginning execution with seed {}/{} ({})", i+1, seeds.size(), seeds.get(i));
			reporter.addLog("Beginning execution with seed " + (i+1) + "/" + seeds.size() + " (" + seeds.get(i) + ")");
			
			execute(seeds.get(i));
			
			for(String key : results.keySet()) {
				if (i == 0)
					foldsResults.put(key, new FullRunningAverageAndStdDev());
				foldsResults.get(key).addDatum(results.get(key)[0]);
			}
		}
		for(String key : results.keySet()) {
			Double[] total = {foldsResults.get(key).getAverage(), foldsResults.get(key).getStandardDeviation()};
			results.put(key, total);
		}
		
		log.info("All seeds result:\n" + printResults());
		reporter.addLog("All seeds result");
		reporter.addResults(results);
		
		reporter.finishExperiment();
	}
	
	public void execute(Long seed) {
		random = new Random(seed);
		execute();
	}
	
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
	 * Create a subjects list in descending order by the number of preferences each
	 * one has
	 * 
	 * @param mm
	 */
	public void setOrderedbyNPrefsSubjects(ModelManage mm) {
		Connection conn = null;
	    PreparedStatement stmt = null;
	    ResultSet rs = null;
		
		try {
			// Query of subject id and content
			conn = mm.getDataSource().getConnection();
			stmt = conn.prepareStatement("SELECT s.id, count(r.rating) as cuenta"
					+ " from uco.uco_punctuated_subject r, uco.uco_subject s"
					+ " where s.id = r.subject_id group by r.subject_id order by cuenta desc;");
			rs = stmt.executeQuery();
			orderedSubjects = new ArrayList<Long>();
			while (rs.next()) {
				orderedSubjects.add(rs.getLong(1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			IOUtils.quietClose(rs, stmt, conn);
		}
	}
	
	private void setUsersThresholds() {
		usersThresh = new HashMap<Long, Double>();
		LongPrimitiveIterator it = null;
		try {
			it = model.getUserIDs();
			while(it.hasNext()) {
				long userID = it.nextLong();
				// Set generic threshold to 3 (assume preferences in [1,5])
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
		return  result[0] ;
	}
	
	protected void getEvaluation(FastByIDMap<PreferenceArray> testPrefs, Recommender recommender) {

		Collection<Callable<Void>> estimateCallables = Lists.newArrayList();
		AtomicInteger noEstimateCounter = new AtomicInteger();
		for (Map.Entry<Long, PreferenceArray> entry : testPrefs.entrySet()) {
			estimateCallables.add(
					new PreferenceEstimateCallable(recommender, entry.getKey(), entry.getValue(), noEstimateCounter));
		}
		
		runMae = new FullRunningAverage();
		runRmse = new FullRunningAverage();
		tp = 0;
		tn = 0;
		fp = 0;
		fn = 0;
		
		log.info("Beginning evaluation of {} users", estimateCallables.size());
		
		RunningAverageAndStdDev timing = new FullRunningAverageAndStdDev();
		try {
			deploy(estimateCallables, noEstimateCounter, timing);
		} catch (TasteException e) {
			e.printStackTrace();
		}
		
		results = new HashMap<>();
		
		if (computeBinaryClassif) {
			Double[] acc = {(double) (tp+tn) / (tp+tn+fp+fn), 0.0};
			results.put("Accuracy", acc);
			Double[] prec = {(double)(tp) / (tp+fp), 0.0};
			results.put("Precision", prec);
			Double[] rec = {(double)(tp) / (tp+fn), 0.0};
			results.put("Recall", rec);
		}
		if (computeRMSE) {
			Double[] rmse = {Math.sqrt(runRmse.getAverage()), 0.0};
			results.put("RMSE", rmse);
		}
		if (computeMAE) {
			Double[] mae = {runMae.getAverage(), 0.0};
			results.put("MAE", mae);
		
		}
	}

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
	
	private final class PreferenceEstimateCallable implements Callable<Void> {

		private final Recommender recommender;
		private final long testUserID;
		private final PreferenceArray prefs;
		private final AtomicInteger noEstimateCounter;
		
		public PreferenceEstimateCallable(Recommender recommender, long testUserID, PreferenceArray prefs,
				AtomicInteger noEstimateCounter) {
			this.recommender = recommender;
			this.testUserID = testUserID;
			this.prefs = prefs;
			this.noEstimateCounter = noEstimateCounter;
		}

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
					// which case
					// NSEE will be thrown. Just ignore it and move on.
					log.info("User exists in test data but not training data: {}", testUserID);
				} catch (NoSuchItemException nsie) {
					log.info("Item exists in test data but not training data: {}", realPref.getItemID());
				}
				if (Float.isNaN(estimatedPref)) {
					noEstimateCounter.incrementAndGet();
				} else {
					if (computeMAE) {
						double diff = realPref.getValue() - estimatedPref;
						runMae.addDatum(Math.abs(diff));
					}
					if (computeRMSE) {
						double diff = realPref.getValue() - estimatedPref;
						runRmse.addDatum(diff * diff);
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
	
	public Map<String, Double[]> getResults() {
		return results;
	}
	
	public String printResults() {
		String res = "";
		for (String key : results.keySet()) {
			res = res + key + ": " + results.get(key)[0] + " +/- " + results.get(key)[1] + "\n";
		}
		return res;
	}
	
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
