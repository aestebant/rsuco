package subjectSimilarity;

import java.util.Collection;
import java.util.List;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

import util.ContentSubjectManage;

/**
 * Item based similarity for subjects It takes the common teachers of each pair
 * 
 * @author Aurora Esteban Toscano
 */
public class ContentSimilarity implements ItemSimilarity {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	ContentSubjectManage similarities;
	
	private final static double THRESHOLD = 0.0; // Threshold to consider two subjects similar

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
	public ContentSimilarity(ContentSubjectManage similarities) {
		this.similarities = similarities;
	}

	@Override
	public double[] itemSimilarities(long subject1, long[] others) throws TasteException {
		double[] result = new double[others.length];
		for (int i = 0; i < others.length; i++) {
			result[i] = similarities.getContentSimilarity(subject1, others[i]);
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
		return similarities.getContentSimilarity(subject1, subject2);
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		List<Long> allSubjects = similarities.getMapSubject();

		for (long possiblySimilar : allSubjects) {
			if (subject != possiblySimilar) {
				double score = itemSimilarity(subject, possiblySimilar);
				if (score > THRESHOLD)
					similars.add(possiblySimilar);
			}
		}
		return similars.toArray();
	}

	@Override
	public void refresh(Collection<Refreshable> arg0) {
	}
}