package subjectreco.recommender.subjectSimilarity;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import subjectreco.util.IConfiguration;

/**
 * Item based similarity for subjects based in common teachers
 * 
 * @author Aurora Esteban Toscano
 */
public class ProfessorsSimilarity implements ItemSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel professors;

	private UserSimilarity professorsSim;
	private Class<? extends UserSimilarity> iProfessorsSim;

	// Threshold to consider two subjects similar
	private static final double THRESHOLD = 0.3;
	
	protected static final Logger log = LoggerFactory.getLogger(ProfessorsSimilarity.class);

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Initialize data model and similarity metric
	 * 
	 * @param professors
	 * @param config
	 */
	public ProfessorsSimilarity(DataModel professors, Configuration config) {
		configure(config);

		this.professors = professors;

		try {
			this.professorsSim = new CachingUserSimilarity(
					iProfessorsSim.getDeclaredConstructor(DataModel.class).newInstance(this.professors), this.professors);
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
	 * Compute a similarity based on boolean existence of commons teachers
	 * 
	 * @throws TasteException
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
		double similarity = professorsSim.userSimilarity(subject1, subject2);
		if (Double.isNaN(similarity))
			similarity = 0.0;

		//log.info("Similarity between {} and {} computed = {}", subject1, subject2, similarity);
		
		return similarity;
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		LongPrimitiveIterator allSubjects = professors.getUserIDs();

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
			this.iProfessorsSim = (Class<? extends UserSimilarity>) Class.forName(config.getString("professorsSimilarity"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

}