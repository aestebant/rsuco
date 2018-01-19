package subjectSimilarity;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import util.IConfiguration;

/**
 * Item based similarity for subjects based on common teachers
 * 
 * @author Aurora Esteban Toscano
 */
public class SkillSimilarity implements ItemSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel skills;
	private UserSimilarity skillSim;
	private Class<? extends UserSimilarity> iSkillSim;

	// Threshold to consider two subjects similar
	private static final double THRESHOLD = 0.3;
	private static final String CONFIGLABEL = "skillSimilarity";

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Initialize data model and similarity metric
	 * 
	 * @param skills
	 * @param config
	 */
	public SkillSimilarity(DataModel skills, Configuration config) {
		configure(config);

		this.skills = skills;

		try {
			this.skillSim = new CachingUserSimilarity(
					iSkillSim.getDeclaredConstructor(DataModel.class).newInstance(this.skills), this.skills);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | TasteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public double[] itemSimilarities(long subject1, long[] others) throws TasteException {
		double[] result = new double[others.length];
		for (int i = 0; i < others.length; i++) {
			result[i] = itemSimilarity(subject1, others[i]);
		}
		return result;
	}

	/**
	 * Compute a similarity based on boolean existence of commons skills in two
	 * subjects
	 * 
	 * @throws TasteException
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
		double similarity = skillSim.userSimilarity(subject1, subject2);
		if (Double.isNaN(similarity))
			similarity = 0.0;

		return similarity;
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		LongPrimitiveIterator allSubjects = skills.getUserIDs();

		while (allSubjects.hasNext()) {
			long possiblySimilar = allSubjects.nextLong();
			double score = itemSimilarity(subject, possiblySimilar);
			if (score > THRESHOLD)
				similars.add(possiblySimilar);
		}
		return similars.toArray();
	}

	@Override
	public void refresh(Collection<Refreshable> arg0) {
	}

	@SuppressWarnings("unchecked")
	@Override
	public void configure(Configuration config) {
		try {
			this.iSkillSim = (Class<? extends UserSimilarity>) Class.forName(config.getString(CONFIGLABEL));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

}