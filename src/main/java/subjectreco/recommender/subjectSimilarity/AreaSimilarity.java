package subjectreco.recommender.subjectSimilarity;

import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Item based similarity for subjects based on common department
 *
 * @author Aurora Esteban Toscano
 */
public class AreaSimilarity implements ItemSimilarity {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private DataModel areas;

    // Threshold to consider two subjects similar
    private final static double THRESHOLD = 0.3;

    protected static final Logger log = LoggerFactory.getLogger(AreaSimilarity.class);

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Initialize data model
     */
    AreaSimilarity(DataModel departments) {
        this.areas = departments;
    }

    @Override
    public double[] itemSimilarities(long subject1, long[] others) {
        double[] result = new double[others.length];
        for (int i = 0; i < others.length; i++) {
            result[i] = itemSimilarity(subject1, others[i]);
        }
        return result;
    }

    /**
     * Compute similarity between two subjects based on their areas in common
     * Since each one belongs to one department, expected similarity is 0 or 1
     */
    @Override
    public double itemSimilarity(long subject1, long subject2) {
        FastIDSet area1 = null, area2 = null;
        try {
            area1 = areas.getItemIDsFromUser(subject1);
            area2 = areas.getItemIDsFromUser(subject2);
        } catch (TasteException e) {
            e.printStackTrace();
        }
        assert area1 != null;
        assert area2 != null;
        int interSize = area1.intersectionSize(area2);
        int unionSize = area1.size() + area2.size() - interSize;

        return (double) interSize / (double) unionSize;
    }

    @Override
    public long[] allSimilarItemIDs(long subject) throws TasteException {
        FastIDSet similars = new FastIDSet();
        LongPrimitiveIterator allSubjects = areas.getUserIDs();

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