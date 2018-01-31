package evaluator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.DataModelBuilder;
import org.apache.mahout.cf.taste.eval.RecommenderBuilder;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public abstract class AKFoldRecommenderEvaluator2 extends AbstractDifferenceRecommenderEvaluator {
	
	private int k;
	private List<FastByIDMap<PreferenceArray>> folds;

	private Map<String, RunningAverage> foldsResults;
	
	private static final String CONNECTION = "jdbc:mysql://localhost:3306/uco";
	private static final String QUERY = "SELECT s.id, count(r.rating) as cuenta" + 
			" from uco.uco_punctuated_subject r, uco.uco_subject s" + 
			" where s.id = r.subject_id group by r.subject_id order by cuenta desc;";
	private List<Long> orderedSubjects;
	
	private static final Logger log = LoggerFactory.getLogger(AKFoldRecommenderEvaluator2.class);

	public AKFoldRecommenderEvaluator2(long seed) {
		super(seed);
		
		getOrderedbyNPrefsSubjects();
	}

	
	@Override
	public Map<String,Double> evaluate(RecommenderBuilder recommenderBuilder, DataModelBuilder dataModelBuilder,
			DataModel dataModel, double nFolds, double evaluationPercentage) throws TasteException {
		Preconditions.checkNotNull(recommenderBuilder);
		Preconditions.checkNotNull(dataModel);
		Preconditions.checkArgument(nFolds >= 2, "Invalid number of folds: " + nFolds);
		Preconditions.checkArgument(evaluationPercentage >= 0.0 && evaluationPercentage <= 1.0,
				"Invalid evaluationPercentage: " + evaluationPercentage
						+ ". Must be: 0.0 <= evaluationPercentage <= 1.0");
		
		log.info("Beginning evaluation using {} of {}", nFolds, dataModel);

		getUsersThresholds(dataModel);
		
		int numUsers = dataModel.getNumUsers();

		// Get the number of folds
		k = (int) nFolds;

		// Initialize buckets for the number of folds
		folds = new ArrayList<FastByIDMap<PreferenceArray>>();
		for (int i = 0; i < k; i++) {
			folds.add(new FastByIDMap<PreferenceArray>(1 + (int) (i / k * numUsers)));
		}

		// Split the dataModel into K folds per user
		LongPrimitiveIterator it = dataModel.getUserIDs();
		while (it.hasNext()) {
			long userID = it.nextLong();
			if (random.nextDouble() < evaluationPercentage) {
				this.splitOneUsersPrefsOrdered(userID, dataModel);
			}
		}

		foldsResults = new HashMap<String, RunningAverage>();
		// Rotate the folds. Each time only one is used for testing and the rest
		// k-1 folds are used for training
		for (int k = 0; k < this.k; k++) {
			FastByIDMap<PreferenceArray> trainingPrefs = new FastByIDMap<PreferenceArray>(
					1 + (int) (evaluationPercentage * numUsers));
			FastByIDMap<PreferenceArray> testPrefs = new FastByIDMap<PreferenceArray>(
					1 + (int) (evaluationPercentage * numUsers));

			for (int i = 0; i < folds.size(); i++) {

				// The testing fold
				testPrefs = folds.get(k);

				// Build the training set from the remaining folds
				if (i != k) {
					for (Entry<Long, PreferenceArray> entry : folds.get(i).entrySet()) {
						if (!trainingPrefs.containsKey(entry.getKey())) {
							trainingPrefs.put(entry.getKey(), entry.getValue());
						} else {
							List<Preference> userPreferences = new ArrayList<Preference>();
							PreferenceArray existingPrefs = trainingPrefs.get(entry.getKey());
							for (int j = 0; j < existingPrefs.length(); j++) {
								userPreferences.add(existingPrefs.get(j));
							}

							PreferenceArray newPrefs = entry.getValue();
							for (int j = 0; j < newPrefs.length(); j++) {
								userPreferences.add(newPrefs.get(j));
							}
							trainingPrefs.remove(entry.getKey());
							trainingPrefs.put(entry.getKey(), new GenericUserPreferenceArray(userPreferences));
						}
					}
				}
			}

			DataModel trainingModel = dataModelBuilder == null ? new GenericDataModel(trainingPrefs)
					: dataModelBuilder.buildDataModel(trainingPrefs);

			Recommender recommender = recommenderBuilder.buildRecommender(trainingModel);

			Map<String,Double> foldRes = getEvaluation(testPrefs, recommender);
			for (String key : foldRes.keySet()) {
				if (k == 0)
					foldsResults.put(key, new FullRunningAverage());
				foldsResults.get(key).addDatum(foldRes.get(key));
			}

			log.info("Evaluation result from fold {} : {}", k, foldRes);
		}

		
		Map<String, Double> result = new HashMap<String, Double>();
		for (String key : foldsResults.keySet())
			result.put(key, foldsResults.get(key).getAverage());

		log.info("Average Evaluation result: {} ", result);

		return result;
	}

	/**
	 *  
	 * @param k
	 * @param folds
	 * @param userID
	 * @param dataModel
	 * @throws TasteException
	 */
	private void splitOneUsersPrefsOrdered(long userID, DataModel dataModel)
			throws TasteException {

		List<List<Preference>> oneUserPrefs = new ArrayList<List<Preference>>(k);
		for (int i = 0; i < k; i++)
			oneUserPrefs.add(new ArrayList<Preference>());

		PreferenceArray prefs = dataModel.getPreferencesFromUser(userID);
		
		List<Preference> orderedPrefs = new ArrayList<>(prefs.length());
		
		Iterator<Long> it = orderedSubjects.iterator();
		while (it.hasNext()) {
			long itemID = it.next();
			if (prefs.hasPrefWithItemID(itemID)) {
				int idx = 0;
				for (int i = 0; i<prefs.length(); ++i) {
					if (prefs.getItemID(i) == itemID)
						break;
					idx++;
				}
				orderedPrefs.add(prefs.get(idx));
			}
		}

		int currentBucket = random.nextInt(k);
		for (int i = 0; i < prefs.length(); i++) {

			Preference newPref = new GenericPreference(userID, orderedPrefs.get(i).getItemID(),
					orderedPrefs.get(i).getValue());

			if (oneUserPrefs.get(currentBucket).isEmpty()) {
				oneUserPrefs.set(currentBucket, new ArrayList<Preference>());
			}
			oneUserPrefs.get(currentBucket).add(newPref);
			
			currentBucket = (currentBucket+1) % k;
		}
		for (int i = 0; i < k; i++) {
			if (oneUserPrefs.get(i) != null) {
				folds.get(i).put(userID, new GenericUserPreferenceArray(oneUserPrefs.get(i)));
			}
		}
	}
	
	@SuppressWarnings("unused")
	private void splitOneUsersPrefsRandom(long userID, DataModel dataModel)
			throws TasteException {

		List<List<Preference>> oneUserPrefs = new ArrayList<List<Preference>>(k);
		for (int i = 0; i < k; i++)
			oneUserPrefs.add(new ArrayList<Preference>());

		PreferenceArray prefs = dataModel.getPreferencesFromUser(userID);

		List<Preference> shuffledPrefs = new ArrayList<>();
		Iterator<Preference> it = prefs.iterator();
		while (it.hasNext()) {
			shuffledPrefs.add(it.next());
		}

		// Shuffle the items
		Collections.shuffle(shuffledPrefs, random);

		int currentBucket = 0;
		for (int i = 0; i < prefs.length(); i++) {
			if (currentBucket == k) {
				currentBucket = 0;
			}

			Preference newPref = new GenericPreference(userID, shuffledPrefs.get(i).getItemID(),
					shuffledPrefs.get(i).getValue());

			if (oneUserPrefs.get(currentBucket).isEmpty()) {
				oneUserPrefs.set(currentBucket, new ArrayList<Preference>());
			}
			oneUserPrefs.get(currentBucket).add(newPref);
			currentBucket++;
		}

		for (int i = 0; i < k; i++) {
			if (oneUserPrefs.get(i) != null) {
				folds.get(i).put(userID, new GenericUserPreferenceArray(oneUserPrefs.get(i)));
			}
		}

	}
	
	private void getOrderedbyNPrefsSubjects() {
		// Open database connection
		Connection connection;
		try {
			connection = DriverManager.getConnection(CONNECTION, "root", "1234");
			Statement s = connection.createStatement();
			// Query of subject id and content
			ResultSet rs = s.executeQuery(QUERY);
			orderedSubjects = new ArrayList<Long>();
			while (rs.next()) {
				orderedSubjects.add(rs.getLong(1));
			}
			connection.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
