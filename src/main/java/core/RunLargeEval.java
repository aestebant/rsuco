package core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	static String inputPath = "../test/cbf";
	static String output = "../results/cbf7.csv";
	static int startConfs = 116;
	static int finConfs = 136;
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
		String head = "Configuration;RMSE;;MAE;;Accuracy;;Precission;;Recall";
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
		Map<String, RunningAverageAndStdDev> finalEval = new HashMap<String, RunningAverageAndStdDev>();

		// For each seed execute an evaluation and store the result
		for (long s : seed) {
			// Instantiate evaluator
			Evaluator evaluator = new Evaluator(s);
			// Configure recommender to evaluate
			evalConf.setProperty("evaluator.recommender", recoPath);
			evaluator.configure(evalConf.subset("evaluator"));
			
			Map<String, Double> results = evaluator.execute(model);
			for (String key : results.keySet()) {
				if (s == seed[0])
					finalEval.put(key, new FullRunningAverageAndStdDev());
				finalEval.get(key).addDatum(results.get(key));
			}
		}

		//TODO Arreglar inconsistencia con cabecera
		String solution = recoPath;
		for (String key : finalEval.keySet()) {
			solution.concat(";" + finalEval.get(key).getAverage() + ";" + finalEval.get(key).getStandardDeviation());
		}

		System.out.println("Finish " + recoPath);

		return solution;
	}
}
