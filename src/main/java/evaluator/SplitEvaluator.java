package evaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;

import com.google.common.base.Preconditions;

public class SplitEvaluator extends AEvaluator {

	private double trainPercent;
	private FastByIDMap<PreferenceArray> trainPrefs;
	private FastByIDMap<PreferenceArray> testPrefs;
	
	public void execute() {
		super.execute();
		
		Preconditions.checkArgument(trainPercent > 0.0 && trainPercent < 1.0, "Invalid train percentage: " + trainPercent);
		
		log.info("Beginning evaluation using {} of {}", trainPercent, model);
		String info = "Train percentage, " + trainPercent;
		reporter.addLog(info);
		
		Integer numUsers = null;
		try {
			numUsers = model.getNumUsers();
		} catch (TasteException e) {
			e.printStackTrace();
		}
		
		trainPrefs = new FastByIDMap<PreferenceArray>(1 + (int) (dataPercent * numUsers));
		testPrefs = new FastByIDMap<PreferenceArray>(1 + (int) (dataPercent * numUsers));

		LongPrimitiveIterator it = null;
		try {
			it = model.getUserIDs();
		} catch (TasteException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
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

		DataModel trainModel = new GenericDataModel(trainPrefs);
		
		Recommender recommender = null;
		try {
			recommender = recoBuilder.buildRecommender(trainModel);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		getEvaluation(testPrefs, recommender);
		
		reporter.addResults(results);
		log.info("Execution results:\n" + printResults());
		
		if (singleExecution)
			reporter.finishExperiment();
	}

	@Override
	public void configure(Configuration config) {
		super.configure(config);
		trainPercent = config.getDouble("trainPercent");
	}

	
	private void splitOneUserPrefsRandom(long userID) {
		List<Preference> oneUserTrainPrefs = new ArrayList<>();
		List<Preference> oneUserTestPrefs = new ArrayList<>();
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

		int limit = (int) Math.round(prefs.length() * trainPercent);
		
		// Shuffle the items
		Collections.shuffle(shuffledPrefs, random);
		
		for (int i = 0; i < limit; ++i) {
			Preference newPref = new GenericPreference(userID, shuffledPrefs.get(i).getItemID(),
					shuffledPrefs.get(i).getValue());
			oneUserTrainPrefs.add(newPref);
		}
		for (int i = limit; i < prefs.length(); ++i) {
			Preference newPref = new GenericPreference(userID, shuffledPrefs.get(i).getItemID(),
					shuffledPrefs.get(i).getValue());
			oneUserTestPrefs.add(newPref);
		}
		
		if (oneUserTrainPrefs != null) {
			trainPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainPrefs));
			if (oneUserTestPrefs != null) {
				testPrefs.put(userID, new GenericUserPreferenceArray(oneUserTestPrefs));
			}
		}
	}

	private void splitOneUserPrefsOrdered(long userID) {
		Preconditions.checkNotNull(orderedSubjects, "Required first: setOrderedbyNPrefsSubjects");

		List<Preference> oneUserTrainPrefs = new ArrayList<>();
		List<Preference> oneUserTestPrefs = new ArrayList<>();
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
		
		int limit = (int) Math.round(1 - (prefs.length() * trainPercent));
		for (int i = 0; i < 2*limit; i+=2) {
			Preference newPref1 = new GenericPreference(userID, orderedPrefs.get(i).getItemID(), orderedPrefs.get(i).getValue());
			oneUserTrainPrefs.add(newPref1);
			Preference newPref2 = new GenericPreference(userID, orderedPrefs.get(i+1).getItemID(), orderedPrefs.get(i+1).getValue());
			oneUserTestPrefs.add(newPref2);
		}
		for (int i = 2*limit; i < prefs.length(); ++i) {
			Preference newPref = new GenericPreference(userID, orderedPrefs.get(i).getItemID(), orderedPrefs.get(i).getValue());
			oneUserTrainPrefs.add(newPref);
		}
	}
}
