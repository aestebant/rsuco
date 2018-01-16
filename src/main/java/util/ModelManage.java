package util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import org.apache.mahout.cf.taste.impl.model.jdbc.ConnectionPoolDataSource;
import org.apache.mahout.cf.taste.impl.model.jdbc.MySQLJDBCDataModel;
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
	private MysqlConnectionPoolDataSource dataSource;

	// Information about server's connection and MySQL data base
	private String server, user, pswd, db;

	// Configuration of all possible models to load
	private Configuration config;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Connect to a MySQL server
	 */
	public void connectMysql() {
		dataSource = new MysqlConnectionPoolDataSource();
		dataSource.setServerName(server);
		dataSource.setUser(user);
		dataSource.setPassword(pswd);
		dataSource.setDatabaseName(db);
	}

	/**
	 * Load a (boolean) model from a MySQL server
	 * 
	 * @param key
	 *            Key to locate the model in the Configuration file
	 * @return the loaded data model
	 */
	public DataModel loadModel(String key) {
		List<String> params = getParameters(key);

		DataModel model = null;

		// Load a data model from a file
		if (params.size() == 1) {
			try {
				model = new FileDataModel(new File(params.get(0)));
			} catch (IOException e) {
				e.printStackTrace();
			}
			return model;
		}

		if (dataSource == null)
			connectMysql();

		// Load a boolean data model
		if (params.get(3).equals("bool")) {
			// Load the model without the preference column (a dump value)
			model = new MySQLJDBCDataModel(new ConnectionPoolDataSource(dataSource), params.get(0), params.get(1),
					params.get(2), params.get(1), null);
			// Transform the model to boolean
			try {
				model = new GenericBooleanPrefDataModel(GenericBooleanPrefDataModel.toDataMap(model));
			} catch (TasteException e) {
				e.printStackTrace();
			}
		}
		// Load a generic data model
		else {
			model = new MySQLJDBCDataModel(new ConnectionPoolDataSource(dataSource), params.get(0), params.get(1),
					params.get(2), params.get(3), null);
		}

		return model;
	}

	/**
	 * Get configuration of a data model from the configuration file given a key
	 * identification of the model
	 * 
	 * @param key
	 *            identification of the data model in the configuration file
	 * @return a list with the parameters of configuration: table, user column, item
	 *         column...
	 */
	private List<String> getParameters(String key) {

		List<String> params = new ArrayList<String>();

		String sourceType = config.getString(key + "[@type]");

		if (sourceType == null) {
			System.err.println("The key " + key + " doesn't exist in model configuration file");
			System.exit(1);
		}

		switch (sourceType) {
		case "mysql":
			params.add(config.getString(key + ".table"));
			params.add(config.getString(key + ".user"));
			params.add(config.getString(key + ".item"));
			params.add(config.getString(key + ".preference"));
			break;

		case "file":
			params.add(config.getString(key + ".filename"));
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
		
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);

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
		
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);
		
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
	 * Configure parameters of the MySQL server where data models are allocated
	 * 
	 * @param config
	 *            Configuration file name (used to be Model.xml)
	 */
	@Override
	public void configure(Configuration config) {
		this.config = config;

		String sourceType = config.getString("source[@type]");
		switch (sourceType) {
		case "mysql":
			server = config.getString("source.server");
			user = config.getString("source.user");
			pswd = config.getString("source.password");
			db = config.getString("source.database");
			break;

		default:
			System.err.println("Cannot recognize source type");
			System.exit(1);
		}
	}
}
