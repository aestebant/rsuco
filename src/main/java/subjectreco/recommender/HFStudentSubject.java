package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.recommender.ByValueRecommendedItemComparator;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * Recommender that combine both information of students and of subjects at estimation level for making recommendations
 *
 * @author Aurora Esteban Toscano
 */
public class HFStudentSubject extends ARecommender {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private CFStudent userReco;
    private CBFSubject itemReco;

    private float wUserReco;

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Execute CFStudent and CBFSubject recommenders and combine their estimations.
     *
     * @param model DataModel
     */
    @Override
    public void execute(DataModel model) {
        super.execute(model);

        if (wUserReco > 0.0)
            userReco.execute(model);
        if (wUserReco < 1.0)
            itemReco.execute(model);

        // Combine two recommenders using Mahout Recommender interface
        setRecommender();
    }

    /**
     * Instantiate the recommender using Mahout Recommender interface and combining student and course based recommenders
     */
    private void setRecommender() {
        recommender = new Recommender() {

            @Override
            public void refresh(Collection<Refreshable> alreadyRefreshed) {
            }

            @Override
            public List<RecommendedItem> recommend(long userID, int howMany) throws TasteException {
                return recommend(userID, howMany, null);
            }

            @Override
            public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer)
                    throws TasteException {
                LongPrimitiveIterator items = getDataModel().getItemIDs();

                // Extract already rated items of the candidates
                PreferenceArray preferences = getDataModel().getPreferencesFromUser(userID);
                preferences.sortByItem();

                int nItems = getDataModel().getNumItems();
                int nPrefs = preferences.length();

                ArrayList<RecommendedItem> possibles = new ArrayList<>(nItems - nPrefs);
                // Index that always point to a already rated item, it controls that
                // recommendations only contain unknown items
                // Assume that item lists of preferences and possibles are ordered by item id
                int current = 0;
                while (items.hasNext()) {
                    long itemID = items.nextLong();
                    if (current < nPrefs && itemID == preferences.getItemID(current)) {
                        // Known item
                        current++;
                    } else {
                        // Unknown item
                        float estimation = estimatePreference(userID, itemID);

                        double rescored = rescorer == null ? estimation : rescorer.rescore(itemID, estimation);

                        possibles.add(new GenericRecommendedItem(itemID, (float) rescored));
                    }
                }

                // Order estimations by value of preference and return the  highest
                List<RecommendedItem> result = possibles.subList(0, nItems - nPrefs);
                result.sort(ByValueRecommendedItemComparator.getInstance());
                return result.subList(0, howMany);
            }

            @Override
            public float estimatePreference(long userID, long itemID) throws TasteException {
                float userEst = Float.NaN;
                float itemEst = Float.NaN;

                if (wUserReco > 0.0)
                    userEst = userReco.recommender.estimatePreference(userID, itemID);
                if (wUserReco < 1.0)
                    itemEst = itemReco.recommender.estimatePreference(userID, itemID);

                // If a recommender can't estimate a preference, take it as 0
                userEst = Float.isNaN(userEst) ? 0f : userEst;
                itemEst = Float.isNaN(itemEst) ? 0f : itemEst;

                // Combine estimations from both recommenders with a given weight in [0,1]
                return userEst * wUserReco + itemEst * (1 - wUserReco);
            }

            @Override
            public void setPreference(long userID, long itemID, float value) throws TasteException {
                if (wUserReco > 0.0)
                    userReco.recommender.setPreference(userID, itemID, value);
                if (wUserReco < 1.0)
                    itemReco.recommender.setPreference(userID, itemID, value);
            }

            @Override
            public void removePreference(long userID, long itemID) throws TasteException {
                if (wUserReco > 0.0)
                    userReco.recommender.removePreference(userID, itemID);
                if (wUserReco < 1.0)
                    itemReco.recommender.removePreference(userID, itemID);
            }

            @Override
            public DataModel getDataModel() {
                return normModel;
            }

        };
    }

    /**
     * Load specific configuration of this recommender
     *
     * @param config Configuration
     */
    @Override
    public void configure(Configuration config) {
        // Standard configuration
        super.configure(config);

        wUserReco = config.getFloat("studentWeight");

        if (wUserReco < 0.0 || wUserReco > 1.0) {
            System.err.println("Student weight in HFStudentSubject must be in [0,1] (current " + wUserReco + ")");
            System.exit(-1);
        }

        if (wUserReco > 0.0) {
            // Student content based subjectreco.recommender
            userReco = new CFStudent();
            userReco.configure(config.subset("cfstudent"));
        }
        if (wUserReco < 1.0) {
            // Subject content based subjectreco.recommender
            itemReco = new CBFSubject();
            itemReco.configure(config.subset("cbfsubject"));
        }
    }
}
