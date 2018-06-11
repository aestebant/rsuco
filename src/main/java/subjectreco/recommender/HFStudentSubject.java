package subjectreco.recommender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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


/**
 * Recommender that combine both information of students and of subjects,
 * estimate preferences by both.
 * 
 * @author aurora
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
	 * @see subjectreco.recommender.ARecommender#execute()
	 */
	@Override
	public void execute(DataModel model) {

		userReco.execute(model);
		itemReco.execute(model);

		// Combine two recommenders using Mahout Recommender interface
		setRecommender();
	}

	/**
	 * Instantiate the subjectreco.recommender using Mahout Recommender interface and combining
	 * user and item based recommenders
	 */
	private void setRecommender() {
		recommender = new Recommender() {

			@Override
			public void refresh(Collection<Refreshable> alreadyRefreshed) {
				// TODO Auto-generated method stub
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

				ArrayList<RecommendedItem> possibles = new ArrayList<RecommendedItem>(nItems - nPrefs);
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

				// Order estimations by value of preference and return the
				// highest
				List<RecommendedItem> result = possibles.subList(0, nItems - nPrefs);
				Collections.sort(result, ByValueRecommendedItemComparator.getInstance());
				return result.subList(0, howMany);
			}

			@Override
			public float estimatePreference(long userID, long itemID) throws TasteException {
				float userEst = userReco.recommender.estimatePreference(userID, itemID);
				float itemEst = itemReco.recommender.estimatePreference(userID, itemID);

				// If a subjectreco.recommender can't estimate a preference, take it as 0
				userEst = Float.isNaN(userEst) ? 0f : userEst;
				itemEst = Float.isNaN(itemEst) ? 0f : itemEst;

				// Combine estimations from both recommenders with a given
				// weight in [0,1]
				return (float) (userEst * wUserReco + itemEst * (1 - wUserReco));
			}

			@Override
			public void setPreference(long userID, long itemID, float value) throws TasteException {
				userReco.recommender.setPreference(userID, itemID, value);
				itemReco.recommender.setPreference(userID, itemID, value);
			}

			@Override
			public void removePreference(long userID, long itemID) throws TasteException {
				userReco.recommender.removePreference(userID, itemID);
				itemReco.recommender.removePreference(userID, itemID);
			}

			@Override
			public DataModel getDataModel() {
				return userReco.recommender.getDataModel();
			}

		};
	}

	/**
	 * @see subjectreco.util.IConfiguration#configure(Configuration)
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

		// Student content based subjectreco.recommender
		userReco = new CFStudent();
		// Subject content based subjectreco.recommender
		itemReco = new CBFSubject();
		
		userReco.configure(config.subset("cfstudent"));
		itemReco.configure(config.subset("cbfsubject"));
	}
}
