package subjectreco.evaluator;

import java.util.ArrayList;
import java.util.Collections;
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

/**
 * Hold out validation of a recommender
 *
 * @author Aurora Esteban Toscano
 */
public class SplitEvaluator extends AEvaluator {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private double trainPercent;
    private FastByIDMap<PreferenceArray> trainPrefs;
    private FastByIDMap<PreferenceArray> testPrefs;

    /**
     * Base code for hold out evaluation of a recommender
     */
    public void execute() {
        super.execute();

        Preconditions.checkArgument(trainPercent > 0.0 && trainPercent < 1.0, "Invalid train percentage: " + trainPercent);

        reporter.addLog("Beginning evaluation using %f of %s", trainPercent, model);

        int numUsers = 0;
        try {
            numUsers = model.getNumUsers();
        } catch (TasteException e) {
            e.printStackTrace();
        }

        trainPrefs = new FastByIDMap<>(1 + (int) (dataPercent * numUsers));
        testPrefs = new FastByIDMap<>(1 + (int) (dataPercent * numUsers));

        LongPrimitiveIterator it = null;
        try {
            it = model.getUserIDs();
        } catch (TasteException e1) {
            e1.printStackTrace();
        }
        assert it != null;
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
            e.printStackTrace();
        }

        getEvaluation(testPrefs, recommender);

        reporter.addResults(results);

        if (singleExecution)
            reporter.finishExperiment();
    }

    /**
     * Take one user preferences and distribute them randomly between train and test.
     *
     * @param userID id that identifies the user in the dataModel
     */
    private void splitOneUserPrefsRandom(long userID) {
        List<Preference> oneUserTrainPrefs = new ArrayList<>();
        List<Preference> oneUserTestPrefs = new ArrayList<>();
        PreferenceArray prefs = null;
        try {
            prefs = model.getPreferencesFromUser(userID);
        } catch (TasteException e) {
            e.printStackTrace();
        }

        assert prefs != null;
        List<Preference> shuffledPrefs = new ArrayList<>(prefs.length());
        for (Preference pref : prefs) {
            shuffledPrefs.add(pref);
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

        trainPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainPrefs));
        testPrefs.put(userID, new GenericUserPreferenceArray(oneUserTestPrefs));
    }

    /**
     * Take one user preferences and distribute them between train and test paying attention to
     * keep subjects balanced according to the number of ratings that they have received
     *
     * @param userID id that identifies the user in the dataModel
     */
    private void splitOneUserPrefsOrdered(long userID) {
        Preconditions.checkNotNull(orderedSubjects, "Required first: setOrderedbyNPrefsSubjects");

        List<Preference> oneUserTrainPrefs = new ArrayList<>();
        List<Preference> oneUserTestPrefs = new ArrayList<>();
        PreferenceArray prefs = null;
        try {
            prefs = model.getPreferencesFromUser(userID);
        } catch (TasteException e) {
            e.printStackTrace();
        }
        assert prefs != null;
        List<Preference> orderedPrefs = new ArrayList<>(prefs.length());

        // Order user preferences by total number of valuations per subject
        for (Long itemID : orderedSubjects) {
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

        int limit = (int) Math.round(prefs.length() * (1-trainPercent)); // Get test percent of subjects
        // Distribute 2*limit first subjects between train and test
        for (int i = 0; i < 2 * limit; i += 2) {
            Preference newPref1 = new GenericPreference(userID, orderedPrefs.get(i).getItemID(), orderedPrefs.get(i).getValue());
            oneUserTrainPrefs.add(newPref1);
            Preference newPref2 = new GenericPreference(userID, orderedPrefs.get(i + 1).getItemID(), orderedPrefs.get(i + 1).getValue());
            oneUserTestPrefs.add(newPref2);
        }
        // Rest of subjects to train (those that are left over)
        for (int i = 2 * limit; i < prefs.length(); ++i) {
            Preference newPref = new GenericPreference(userID, orderedPrefs.get(i).getItemID(), orderedPrefs.get(i).getValue());
            oneUserTrainPrefs.add(newPref);
        }

        trainPrefs.put(userID, new GenericUserPreferenceArray(oneUserTrainPrefs));
        testPrefs.put(userID, new GenericUserPreferenceArray(oneUserTestPrefs));
    }

    /**
     * Hold out specific configuration
     *
     * @param config Configuration
     */
    @Override
    public void configure(Configuration config) {
        super.configure(config);
        trainPercent = config.getDouble("trainPercent");
    }
}
