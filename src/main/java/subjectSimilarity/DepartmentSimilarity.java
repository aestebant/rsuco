package subjectSimilarity;

import java.util.Collection;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

/**
 * Item based similarity for subjects based on common department
 * 
 * @author Aurora Esteban Toscano
 */
public class DepartmentSimilarity implements ItemSimilarity {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel departments;

	// Threshold to consider two subjects similar
	private final static double THRESHOLD = 0.3;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Initialize data model
	 * 
	 * @param departments
	 */
	public DepartmentSimilarity(DataModel departments) {
		this.departments = departments;
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
	 * Compute similarity between two subjects based on their departments in common
	 * Since each one belongs to one department, expected similarity is 0 or 1
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
		FastIDSet department1 = null, department2 = null;
		try {
			department1 = departments.getItemIDsFromUser(subject1);
			department2 = departments.getItemIDsFromUser(subject2);
		} catch (TasteException e) {
			e.printStackTrace();
		}
		int interSize = department1.intersectionSize(department2);

		return (double) interSize / (double) department1.size();
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		LongPrimitiveIterator allSubjects = departments.getUserIDs();

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
}