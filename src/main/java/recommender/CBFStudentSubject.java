package recommender;

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

import com.google.common.base.Preconditions;

import util.ConfigLoader;

/**
 * Recommender that combine both information of students and of subjects,
 * estimate preferences by both.
 * 
 * @author aurora
 */
public class CBFStudentSubject extends ARecommender {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private CBFStudent userReco;
	private CBFSubject itemReco;

	private float wUserReco;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * @see recommender.ARecommender#execute()
	 */
	@Override
	public void execute(DataModel model) {
		Preconditions.checkArgument(wUserReco >= 0 && wUserReco <= 1,
				"User recommender weight must be between 0 and 1");

		userReco.execute(model);
		itemReco.execute(model);

		// Combine two recommenders using Mahout Recommender interface
		setRecommender();
	}

	/**
	 * Instantiate the recommender using Mahout Recommender interface and combining
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

				// If a recommender can't estimate a preference, take it as 0
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
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		// Standard configuration
		super.configure(config);

		String configSt = config.getString("pathStudentConfig");
		String configSu = config.getString("pathSubjectConfig");
		wUserReco = config.getFloat("studentWeight");

		// Student content based recommender
		userReco = new CBFStudent();
		userReco.configure(ConfigLoader.XMLFile(configSt).subset("recommender"));

		// Subject content based recommender
		itemReco = new CBFSubject();
		itemReco.configure(ConfigLoader.XMLFile(configSu).subset("recommender"));
	}
}
