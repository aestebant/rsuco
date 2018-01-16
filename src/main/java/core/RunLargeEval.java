package core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;
import org.apache.mahout.cf.taste.model.DataModel;

import com.google.common.base.Preconditions;

import evaluator.Evaluator;
import util.ConfigLoader;
import util.IConfiguration;
import util.ModelManage;

/**
 * Parallelized tests of a set of configurations of a recommender
 * 
 * @author Aurora Esteban Toscano
 */
class RunLargeEval {

	static String inputPath = "../../test/sub";
	static String output = "../../results/cbfsubject.csv";
	static int startConfs = 1;
	static int finConfs = 20;
	static int nThreads = 1;

	static long[] seed = { 10, 20, 30, 40, 50 };
	static Configuration evalConf;
	static DataModel model;

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 1, "Set configuration file");

		// Load evaluator configuration
		evalConf = ConfigLoader.XMLFile(args[0]);

		// Precompute ratings model because assuming it will be the same in all the
		// configurations
		Configuration configDM = ConfigLoader.XMLFile("configuration/Model.xml");
		ModelManage mm = new ModelManage();
		if (mm instanceof IConfiguration)
			((IConfiguration) mm).configure(configDM.subset("model"));
		model = mm.loadModel("ratings");

		/**
		 * Wrap the test method in order to parallelizer it
		 */
		class Test implements Callable<String> {
			String conf;

			public Test(String conf) {
				this.conf = conf;
			}

			@Override
			public String call() throws Exception {
				return test(conf);
			}
		}
		;

		// Save results of tests
		List<Future<String>> returns = new ArrayList<Future<String>>(finConfs-startConfs+1);
		// Parallelizer in n threads
		ExecutorService executor = Executors.newFixedThreadPool(nThreads);

		for (int i = startConfs; i <= finConfs; ++i) {
			String confFile = inputPath + i + ".xml";
			returns.add(executor.submit(new Test(confFile)));
		}

		// Write results in the output file
		File result = new File(output);
		String head = "Configuration;MAE;;RMSE;;F1-score;;Precission;;Recall;;Fall Out;;nDCG";
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(result));
			writer.write(head + "\n");

			for (Future<String> s : returns)
				try {
					writer.write(s.get() + "\n");
				} catch (InterruptedException | ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		executor.shutdown();
	}

	/**
	 * Test one configuration of recommender with each seed
	 * 
	 * @param recoPath
	 *            Path to the XML file with the configuration of the recommender
	 * @return average and standard deviation statistics of the recommender
	 */
	public static String test(String recoPath) {
		// Instantiate evaluator
		Evaluator evaluator = new Evaluator(model);

		// Configure recommender to evaluate
		evalConf.setProperty("evaluator.recommender", recoPath);

		evaluator.configure(evalConf.subset("evaluator"));

		// Initialize statistics
		RunningAverageAndStdDev mae = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev rmse = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev precision = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev recall = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev fallout = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev f1 = new FullRunningAverageAndStdDev();
		RunningAverageAndStdDev dcg = new FullRunningAverageAndStdDev();

		// For each seed execute an evaluation and store the result
		for (long s : seed) {
			evaluator.setSeed(s);

			evaluator.execute();

			mae.addDatum(evaluator.getMAE());
			rmse.addDatum(evaluator.getRMSE());
			precision.addDatum(evaluator.getStats().getPrecision());
			recall.addDatum(evaluator.getStats().getRecall());
			fallout.addDatum(evaluator.getStats().getFallOut());
			f1.addDatum(evaluator.getStats().getF1Measure());
			dcg.addDatum(evaluator.getStats().getNormalizedDiscountedCumulativeGain());
		}

		String solution = recoPath + ";" + mae.getAverage() + ";" + mae.getStandardDeviation() + ";" + rmse.getAverage()
				+ ";" + rmse.getStandardDeviation() + ";" + f1.getAverage() + ";" + f1.getStandardDeviation() + ";"
				+ precision.getAverage() + ";" + +precision.getStandardDeviation() + ";" + recall.getAverage() + ";"
				+ recall.getStandardDeviation() + ";" + fallout.getAverage() + ";" + fallout.getStandardDeviation()
				+ ";" + dcg.getAverage() + ";" + dcg.getStandardDeviation();

		System.out.println("Finish " + recoPath);

		return solution;
	}
}
