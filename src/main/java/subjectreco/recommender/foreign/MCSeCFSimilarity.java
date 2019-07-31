package subjectreco.recommender.foreign;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.similarity.TanimotoCoefficientSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import subjectreco.recommender.similarity.AdjustedCosineSimilarity;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Item based similarity for subjects using multiple criteria: teachers
 * department skills or their program contents
 *
 * @author Aurora Esteban Toscano
 */
public class MCSeCFSimilarity implements ItemSimilarity {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private static ThreadLocal<DataModel> ratings = new ThreadLocal<>();
    private static ThreadLocal<DataModel> grades = new ThreadLocal<>();

    // Single criteria similarities
    private static ItemSimilarity ratingsSim;
    private static ItemSimilarity gradesSim;
    private ItemSimilarity jaccard;
    // Importance of each criteria in final similarity in [0,1]
    private double wRatings = 0.5;
    private double wGrades = 0.5;

    // Threshold to consider two subjects similar
    private static final double THRESHOLD = 0.3;

    private final FastByIDMap<FastByIDMap<Double>> similarityMaps = new FastByIDMap<>();

    protected static final Logger log = LoggerFactory.getLogger(MCSeCFSimilarity.class);

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Initialize single criteria similarities
     */
    public MCSeCFSimilarity(DataModel ratings, DataModel grades) {

        MCSeCFSimilarity.ratings.set(ratings);
        MCSeCFSimilarity.grades.set(grades);

        try {
            if (wRatings > 0.0)
                ratingsSim = new AdjustedCosineSimilarity(MCSeCFSimilarity.ratings.get());
            if (wGrades > 0.0)
                gradesSim = new AdjustedCosineSimilarity(MCSeCFSimilarity.grades.get());
            jaccard = new TanimotoCoefficientSimilarity(ratings);
        } catch (TasteException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        log.info("Computing similarity based on subjects");
        computeFinalSimilarities();
    }

    private void computeFinalSimilarities() {
        LongPrimitiveIterator rows = getSubjects();

        class Wrapper implements Callable<Double[]> {
            private long subject1, subject2;

            private Wrapper(long sb1, long sb2) {
                this.subject1 = sb1;
                this.subject2 = sb2;
            }

            @Override
            public Double[] call() {
                return new Double[]{(double) subject2, computeSimilarity(subject1, subject2)};
            }
        }

        int count = 1;
        assert rows != null;
        while (rows.hasNext()) {
            long subject1 = rows.nextLong();
            LongPrimitiveIterator cols = getSubjects();
            assert cols != null;
            cols.skip(count);
            FastByIDMap<Double> map = new FastByIDMap<>();

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            Collection<Callable<Double[]>> collection = Lists.newArrayList();

            while (cols.hasNext()) {
                long subject2 = cols.next();
                collection.add(new Wrapper(subject1, subject2));
            }

            try {
                List<Future<Double[]>> futures = executor.invokeAll(collection);
                for (Future<Double[]> future : futures) {
                    Double[] result = future.get();
                    map.put(result[0].longValue(), result[1]);
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            similarityMaps.put(subject1, map);
            count++;
        }
    }

    /**
     * Compute the multi-criteria similarity of two subjects combining the simple
     * similarities of the subjects
     */
    private double computeSimilarity(long subject1, long subject2) {
        double sim1 = 0.0, sim2 = 0.0;
        double jac = 0;
        try {
            if (wRatings > 0.0)
                sim1 = ratingsSim.itemSimilarity(subject1, subject2);
            if (wGrades > 0.0)
                sim2 = gradesSim.itemSimilarity(subject1, subject2);
            jac = jaccard.itemSimilarity(subject1, subject2);
        } catch (TasteException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        double similarity = (wRatings * sim1 + wGrades * sim2) * jac;
        if (similarity > 1.0)
            similarity = 1.0;
        if (similarity < -1.0)
            similarity = -1.0;

        return similarity;
    }

    @Override
    public double itemSimilarity(long subject1, long subject2) {
        try {
            if (subject1 < subject2)
                return similarityMaps.get(subject1).get(subject2);
            else if (subject2 < subject1)
                return similarityMaps.get(subject2).get(subject1);
        } catch (Exception ignored) {
            return 0.0;
        }
        return 1.0;
    }

    @Override
    public double[] itemSimilarities(long subject, long[] others) {
        double[] result = new double[others.length];
        for (int i = 0; i < others.length; i++) {
            result[i] = itemSimilarity(subject, others[i]);
        }
        return result;
    }

    @Override
    public long[] allSimilarItemIDs(long subject) throws TasteException {
        FastIDSet similars = new FastIDSet();
        LongPrimitiveIterator allSubjects = ratings.get().getUserIDs();

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

    private LongPrimitiveIterator getSubjects() {
        try {
            if (ratings.get() != null)
                return ratings.get().getUserIDs();
            else if (grades.get() != null)
                return grades.get().getUserIDs();
        } catch (TasteException e) {
            e.printStackTrace();
        }
        return null;
    }
}