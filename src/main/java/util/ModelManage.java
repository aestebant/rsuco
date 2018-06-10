package util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.FullRunningAverage;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.common.RunningAverage;
import org.apache.mahout.cf.taste.impl.model.GenericBooleanPrefDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.impl.model.file.FileDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

/**
 * Group in a class main model's functions used in the library
 * 
 * @author Aurora Esteban Toscano
 */
public class ModelManage implements IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private static MysqlConnectionPoolDataSource ds;

	// Information about dburl's connection and MySQL data base
	private String dburl, dbuser, dbpswd;

	// Configuration of all possible models to load
	private Configuration config;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	public ModelManage(Configuration config) {
		configure(config);
		createPool();
	}

	/**
	 * Connect to a MySQL server
	 */
	public void createPool() {
		// Check driver
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception e) {
			System.err.println("MySQL driver not found");
			System.exit(-1);
		}

		ds = new MysqlConnectionPoolDataSource();
		ds.setURL(dburl);
		ds.setUser(dbuser);
		ds.setPassword(dbpswd);
	}
	
	public DataSource getDataSource() {
		return ds;
	}

	/**
	 * Load a (boolean) model from a MySQL database
	 * 
	 * @param key
	 *            Key to locate the model in the Configuration file
	 * @return the loaded data model
	 */
	public DataModel loadModel(String key) {
		//org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		//l.setLevel(org.apache.log4j.Level.ERROR);

		Map<String, String> params = getParameters(key);

		DataModel model = null;

		// Load a data model from a file
		if (params.containsKey("filename")) {
			try {
				model = new FileDataModel(new File(params.get("filename")));
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(-1);
			}
			return model;
		}

		// Load a boolean data model
		if (params.get("preference").equals("bool")) {
			// Load the model without the preference column (a dump value)
			model = new MySQLJDBCDataModel(ds, params.get("table"), params.get("user"), params.get("item"),
					params.get("item"), null);
			// Transform the model to boolean
			try {
				model = new GenericBooleanPrefDataModel(GenericBooleanPrefDataModel.toDataMap(model));
			} catch (TasteException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		// Load a generic data model
		else {
			model = new MySQLJDBCDataModel(ds, params.get("table"), params.get("user"), params.get("item"),
					params.get("preference"), null);
		}

		return model;
	}

	/**
	 * Get configuration of a data model from the configuration file given a key
	 * identification of the model
	 * 
	 * @param key
	 *            identification of the data model in the configuration file
	 * @return map with necessary fields to load a model: table, user, item and
	 *         preference columns...
	 */
	private Map<String, String> getParameters(String key) {

		Map<String, String> params = new HashMap<String, String>();

		String sourceType = config.getString(key + "[@type]");

		if (sourceType == null) {
			System.err.println("The key " + key + " doesn't exist in model configuration file");
			System.exit(1);
		}

		switch (sourceType) {
		case "mysql":
			String[] fields = { "table", "user", "item", "preference" };
			for (String f : fields) {
				params.put(f, config.getString(key + "." + f));
			}
			break;

		case "file":
			params.put("filename", config.getString(key + ".filename"));
			break;

		default:
			System.err.println("Cannot recognize source type " + sourceType);
			System.exit(1);

		}
		return params;
	}

	/**
	 * Filter a data model removing users and items that don't appear in another
	 * data model of reference
	 * 
	 * @param origin
	 *            DataModel to be filtered
	 * @param reference
	 *            DataModel with users and items of reference
	 * @return the filtered DataModel
	 * @throws TasteException
	 */
	public DataModel filterModel(DataModel origin, DataModel reference) {

		//org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		//l.setLevel(org.apache.log4j.Level.WARN);

		FastByIDMap<PreferenceArray> preferences = new FastByIDMap<PreferenceArray>();

		LongPrimitiveIterator users = null;
		try {
			users = reference.getUserIDs();
		} catch (TasteException e) {
			e.printStackTrace();
		}
		while (users.hasNext()) {
			long user_id = users.nextLong();

			PreferenceArray userPrefs = null;
			FastIDSet refItems = null;
			try {
				userPrefs = origin.getPreferencesFromUser(user_id);
				refItems = reference.getItemIDsFromUser(user_id);
			} catch (TasteException e) {
				e.printStackTrace();
			}

			PreferenceArray filtered = new GenericUserPreferenceArray(refItems.size());
			// If a item is rated by the user in reference DataModel, its rating
			// in origin DataModel is added to filtered DataModel
			int j = 0;
			for (int i = 0; i < userPrefs.length(); i++) {
				if (refItems.contains(userPrefs.getItemID(i))) {
					filtered.set(j, userPrefs.get(i));
					j++;
				}
			}
			preferences.put(user_id, filtered);
		}

		return new GenericDataModel(preferences);
	}

	/**
	 * Remove global effects of rating by subtract to all the preferences the
	 * average of the model, the average of it item and the average of it user
	 * 
	 * @param model
	 * @return normalized model
	 */
	public DataModel subtractiveNormalization(DataModel model) {

		// Weight of each average in the normalization
		double wAvgAll = 1. / 3, wAvgUser = 1. / 3, wAvgItem = 1. / 3;

		int nUsers = 0;
		int nItems = 0;
		try {
			nUsers = model.getNumUsers();
			nItems = model.getNumItems();

		} catch (TasteException e) {
			e.printStackTrace();
		}

		ArrayList<RunningAverage> avgUsers = new ArrayList<RunningAverage>(nUsers);
		ArrayList<RunningAverage> avgItems = new ArrayList<RunningAverage>(nItems);
		RunningAverage avgAll = new FullRunningAverage();

		LongPrimitiveIterator users = null;
		try {
			users = model.getUserIDs();
		} catch (TasteException e) {
			e.printStackTrace();
		}
		int i = 0;
		while (users.hasNext()) {
			long user_id = users.nextLong();
			FastIDSet valoredItems = null;
			LongPrimitiveIterator items = null;
			try {
				valoredItems = model.getItemIDsFromUser(user_id);
				items = model.getItemIDs();
			} catch (TasteException e) {
				e.printStackTrace();
			}

			avgUsers.add(new FullRunningAverage());

			int j = 0;
			while (items.hasNext()) {
				long item_id = items.nextLong();

				if (i == 0) {
					avgItems.add(new FullRunningAverage());
				}
				if (valoredItems.contains(item_id)) {
					double preference = 0.;
					try {
						preference = model.getPreferenceValue(user_id, item_id);
					} catch (TasteException e) {
						e.printStackTrace();
					}

					avgAll.addDatum(preference);
					avgUsers.get(i).addDatum(preference);
					avgItems.get(j).addDatum(preference);
				}
				j++;
			}
			i++;
		}

		// Create a PreferenceArray with original data normalized
		FastByIDMap<PreferenceArray> normalized = new FastByIDMap<PreferenceArray>();

		LongPrimitiveIterator users3 = null;
		try {
			users3 = model.getUserIDs();
		} catch (TasteException e) {
			e.printStackTrace();
		}
		i = 0;
		while (users3.hasNext()) {
			long user_id = users3.nextLong();
			FastIDSet valoredItems = null;
			try {
				valoredItems = model.getItemIDsFromUser(user_id);
			} catch (TasteException e) {
				e.printStackTrace();
			}

			PreferenceArray prefsForUser = null;
			try {
				prefsForUser = new GenericUserPreferenceArray(model.getPreferencesFromUser(user_id).length());
			} catch (TasteException e) {
				e.printStackTrace();
			}
			prefsForUser.setUserID(0, user_id);

			LongPrimitiveIterator items3 = null;
			try {
				items3 = model.getItemIDs();
			} catch (TasteException e) {
				e.printStackTrace();
				System.exit(-1);
			}
			int j = 0;
			int k = 0;
			while (items3.hasNext()) {
				long item_id = items3.nextLong();

				if (valoredItems.contains(item_id)) {
					double preference = 0.;
					try {
						preference = model.getPreferenceValue(user_id, item_id);
					} catch (TasteException e) {
						e.printStackTrace();
						System.exit(-1);
					}
					// Apply the normalization subtracting averages multiplied
					// by a coefficient to the preference
					preference -= (wAvgAll * avgAll.getAverage() + wAvgUser * avgUsers.get(i).getAverage()
							+ wAvgItem * avgItems.get(j).getAverage());

					prefsForUser.setItemID(k, item_id);
					prefsForUser.setValue(k, (float) preference);
					k++;
				}
				j++;
			}
			normalized.put(user_id, prefsForUser);
			i++;
		}
		return new GenericDataModel(normalized);
	}

	/**
	 * Configure parameters of the MySQL database where data models are allocated
	 * 
	 * @param config
	 *            Configuration file name (used to be Model.xml)
	 */
	@Override
	public void configure(Configuration config) {
		this.config = config.subset("model");

		String sourceType = this.config.getString("source[@type]");
		switch (sourceType) {
		case "mysql":
			dburl = this.config.getString("source.url");
			dbuser = this.config.getString("source.user");
			dbpswd = this.config.getString("source.password");
			break;

		default:
			System.err.println("Cannot recognize source type");
			System.exit(-1);
		}
	}
}
