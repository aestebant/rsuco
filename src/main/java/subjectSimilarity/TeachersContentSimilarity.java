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

import util.ContentSubjectManage;
import util.IConfiguration;

/**
 * Item based similarity for subjects It takes the common teachers of each pair
 * 
 * @author Aurora Esteban Toscano
 */
public class TeachersContentSimilarity implements ItemSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel teaching;
	
	private UserSimilarity teachingSim;
	private Class<? extends UserSimilarity> iTeachingSim;
	
	private ContentSubjectManage similarities;
	
	private double threshold = 0.4; // Threshold to consider two subjects similar

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Previous calculations for this hybrid similarity. Initialize the data and
	 * compute professors-based similarity.
	 * 
	 * @param teaching
	 *            boolean dataModel with subjects as users and teachers as items
	 * @param departments
	 * 
	 */
	public TeachersContentSimilarity(DataModel teaching, ContentSubjectManage similarities, Configuration config) {
		configure(config);

		this.teaching = teaching;

		try {
			this.teachingSim = new CachingUserSimilarity(
					iTeachingSim.getDeclaredConstructor(DataModel.class).newInstance(this.teaching), this.teaching);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | TasteException e) {
			e.printStackTrace();
		}
		
		this.similarities = similarities;
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
	 * Compute a similarity based on boolean existence of commons teachers and commons skills in two
	 * subjects, as well as benefit if they belong to the same department.
	 * @throws TasteException
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
		double simTeaching = teachingSim.userSimilarity(subject1, subject2);
		
		if(Double.isNaN(simTeaching))
			simTeaching = .0;
		
		double similarity = 0.3*simTeaching + 0.7*similarities.getContentSimilarity(subject1, subject2);

		return similarity;
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		LongPrimitiveIterator allSubjects = teaching.getUserIDs();

		while (allSubjects.hasNext()) {
			long possiblySimilar = allSubjects.nextLong();
			double score = itemSimilarity(subject, possiblySimilar);
			if (score > threshold)
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
			this.iTeachingSim = (Class<? extends UserSimilarity>) Class.forName(config.getString("teachingSimilarity"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

}