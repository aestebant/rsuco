package evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverageAndStdDev;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;

import com.google.common.base.Preconditions;

public class KFoldEvaluator extends AEvaluator {
	
	private int k;
	private List<FastByIDMap<PreferenceArray>> folds;
	private Map<String, RunningAverageAndStdDev> foldsRes;
	
	public void execute() {
		super.execute();
		
		Preconditions.checkArgument(k >= 2, "Invalid number of folds: " + k);
		
		log.info("Beginning evaluation using {} folds of {}", k, model);
		String info = "Number of folds, " + k;
		reporter.addLog(info);
		
		Integer numUsers = null;
		try {
			numUsers = model.getNumUsers();
		} catch (TasteException e) {
			e.printStackTrace();
		}
		
		// Initialize buckets for the number of folds
		folds = new ArrayList<FastByIDMap<PreferenceArray>>();
		for (int i = 0; i < k; i++) {
			folds.add(new FastByIDMap<PreferenceArray>(1 + (int) (i / k * numUsers)));
		}

		// Split the dataModel into K folds per user
		LongPrimitiveIterator it = null;
		try {
			it = model.getUserIDs();
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while (it.hasNext()) {
			long userID = it.nextLong();
			if (random.nextDouble() < dataPercent) {
				if (useRandomSplit)
					splitOneUserPrefsRandom(userID);
				else
					splitOneUserPrefsOrdered(userID);
			}
		}
		
		foldsRes = new HashMap<String, RunningAverageAndStdDev>();
		// Rotate the folds. Each time only one is used for testing and the rest
		// k-1 folds are used for training
		for (int k = 0; k < this.k; k++) {
			log.info("Beginning evaluation of fold {}/{}", k+1, this.k);
			String info2 = "Beginning evaluation of fold " + (k+1) + "/" + this.k;
			reporter.addLog(info2);
			
			FastByIDMap<PreferenceArray> trainPrefs = new FastByIDMap<PreferenceArray>(
					1 + (int) (dataPercent * numUsers));
			FastByIDMap<PreferenceArray> testPrefs = new FastByIDMap<PreferenceArray>(
					1 + (int) (dataPercent * numUsers));

			for (int i = 0; i < folds.size(); i++) {

				// The testing fold
				testPrefs = folds.get(k);

				// Build the training set from the remaining folds
				if (i != k) {
					for (Entry<Long, PreferenceArray> entry : folds.get(i).entrySet()) {
						if (!trainPrefs.containsKey(entry.getKey())) {
							trainPrefs.put(entry.getKey(), entry.getValue());
						} else {
							List<Preference> userPreferences = new ArrayList<Preference>();
							PreferenceArray existingPrefs = trainPrefs.get(entry.getKey());
							for (int j = 0; j < existingPrefs.length(); j++) {
								userPreferences.add(existingPrefs.get(j));
							}

							PreferenceArray newPrefs = entry.getValue();
							for (int j = 0; j < newPrefs.length(); j++) {
								userPreferences.add(newPrefs.get(j));
							}
							trainPrefs.remove(entry.getKey());
							trainPrefs.put(entry.getKey(), new GenericUserPreferenceArray(userPreferences));
						}
					}
				}
			}
			DataModel trainModel = new GenericDataModel(trainPrefs);

			Recommender recommender = null;
			try {
				recommender = recoBuilder.buildRecommender(trainModel);
			} catch (TasteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			getEvaluation(testPrefs, recommender);
			
			log.info("One fold results:\n" + printResults());
			reporter.addResults(results);
			
			for (String key : results.keySet()) {
				if (k == 0)
					foldsRes.put(key, new FullRunningAverageAndStdDev());
				foldsRes.get(key).addDatum(results.get(key)[0]);
			}
		}
		for (String key : foldsRes.keySet()) {
			Double[] res = {foldsRes.get(key).getAverage(), foldsRes.get(key).getStandardDeviation()};
			results.put(key, res);
		}
		log.info("All folds results:\n" + printResults());
		reporter.addLog("All folds results");
		reporter.addResults(results);
		
		if (singleExecution)
			reporter.finishExperiment();
	}
	
	/**
	 * Take one user preferences and distribute into the k folds paying attention to
	 * keep balanced subjects per fold according to number of valuations they have
	 * 
	 * @param userID
	 * @param dataModel
	 * @throws TasteException
	 */
	private void splitOneUserPrefsOrdered(long userID) {
		Preconditions.checkNotNull(orderedSubjects, "Required first: setOrderedbyNPrefsSubjects");

		List<List<Preference>> oneUserPrefs = new ArrayList<List<Preference>>(k);
		for (int i = 0; i < k; i++)
			oneUserPrefs.add(new ArrayList<Preference>());

		PreferenceArray prefs = null;
		try {
			prefs = model.getPreferencesFromUser(userID);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<Preference> orderedPrefs = new ArrayList<Preference>(prefs.length());

		// Order user preferences by total number of valuations per subject
		Iterator<Long> it = orderedSubjects.iterator();
		while (it.hasNext()) {
			long itemID = it.next();
			if (prefs.hasPrefWithItemID(itemID)) {
				int idx = 0;
				for (int i = 0; i < prefs.length(); ++i) {
					if (prefs.getItemID(i) == itemID)
						break;
					idx++;
				}
				orderedPrefs.add(prefs.get(idx));
			}
		}

		// Starting by a random fold, assign each preference to a fold in consecutive
		// order
		int currentBucket = random.nextInt(k);
		for (int i = 0; i < prefs.length(); i++) {

			Preference newPref = new GenericPreference(userID, orderedPrefs.get(i).getItemID(),
					orderedPrefs.get(i).getValue());

			if (oneUserPrefs.get(currentBucket).isEmpty()) {
				oneUserPrefs.set(currentBucket, new ArrayList<Preference>());
			}
			oneUserPrefs.get(currentBucket).add(newPref);

			currentBucket = (currentBucket + 1) % k;
		}

		// Assign each partition to its general fold
		for (int i = 0; i < k; i++) {
			if (oneUserPrefs.get(i) != null) {
				folds.get(i).put(userID, new GenericUserPreferenceArray(oneUserPrefs.get(i)));
			}
		}
	}

	/**
	 * Take one user preferences and distribute into the k folds randomly
	 * 
	 * @param userID
	 * @param dataModel
	 * @throws TasteException
	 */
	private void splitOneUserPrefsRandom(long userID) {

		List<List<Preference>> oneUserPrefs = new ArrayList<List<Preference>>(k);
		for (int i = 0; i < k; i++)
			oneUserPrefs.add(new ArrayList<Preference>());

		PreferenceArray prefs = null;
		try {
			prefs = model.getPreferencesFromUser(userID);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		List<Preference> shuffledPrefs = new ArrayList<>();
		Iterator<Preference> it = prefs.iterator();
		while (it.hasNext()) {
			shuffledPrefs.add(it.next());
		}

		// Shuffle the items
		Collections.shuffle(shuffledPrefs, random);

		// Assign each element in the permutation to a fold starting by the first
		int currentBucket = 0;
		for (int i = 0; i < prefs.length(); i++) {

			Preference newPref = new GenericPreference(userID, shuffledPrefs.get(i).getItemID(),
					shuffledPrefs.get(i).getValue());

			if (oneUserPrefs.get(currentBucket).isEmpty()) {
				oneUserPrefs.set(currentBucket, new ArrayList<Preference>());
			}
			oneUserPrefs.get(currentBucket).add(newPref);

			currentBucket = (currentBucket + 1) % k;
		}

		// Assign each partition to its general fold
		for (int i = 0; i < k; i++) {
			if (oneUserPrefs.get(i) != null) {
				folds.get(i).put(userID, new GenericUserPreferenceArray(oneUserPrefs.get(i)));
			}
		}
	}
	
	public void configure(Configuration config) {
		super.configure(config);
		k = config.getInt("foldsNumber");
	}
}
