package subjectreco.recommender.similarity;

import com.google.common.collect.Lists;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import subjectreco.util.ClassInstantiator;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * Item based similarity for subjects using multiple criteria: teachers
 * department skills or their program contents
 *
 * @author Aurora Esteban Toscano
 */
public class CourseSimilarity implements ItemSimilarity {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private static ThreadLocal<DataModel> professors = new ThreadLocal<>();
    private static ThreadLocal<DataModel> competences = new ThreadLocal<>();
    private static DataModel areas;

    private String professorsSimilarityName;
    private String competencesSimilarityName;

    // Single criteria similarities
    private static UserSimilarity professorSimilarity;
    private static UserSimilarity competenceSimilarity;
    private static ItemSimilarity contentSimilarity;

    // Importance of each criteria in final similarity in [0,1]
    private double wProfessors;
    private double wArea;
    private double wCompetences;
    private double wContent;

    // Threshold to consider two subjects similar
    private static final double THRESHOLD = 0.3;

    private final FastByIDMap<FastByIDMap<Double>> similarityMaps = new FastByIDMap<>();

    protected static final Logger log = LoggerFactory.getLogger(CourseSimilarity.class);

    //////////////////////////////////////////////
    // ------------------------------ Constructor
    /////////////////////////////////////////////
    public CourseSimilarity(DataModel professors, DataModel areas, DataModel competences, Configuration config) {
        configure(config);

        CourseSimilarity.professors.set(professors);
        CourseSimilarity.competences.set(competences);
        CourseSimilarity.areas = areas;

        if (wProfessors > 0d)
            professorSimilarity = ClassInstantiator.instantiateUserSimilarity(professorsSimilarityName,
                    CourseSimilarity.professors.get());
        if (wCompetences > 0d)
            competenceSimilarity = ClassInstantiator.instantiateUserSimilarity(competencesSimilarityName,
                    CourseSimilarity.competences.get());
        if (wContent > 0.0)
            contentSimilarity = new ContentSimilarity(config);

        log.info("Computing similarity based on subjects");
        computeFinalSimilarities();
    }

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////
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
        double sim1 = 0.0, sim2 = 0.0, sim3 = 0.0, sim4 = 0.0;
        try {
            if (wProfessors > 0.0)
                sim1 = professorSimilarity.userSimilarity(subject1, subject2);
            if (wContent > 0.0)
                sim2 = contentSimilarity.itemSimilarity(subject1, subject2);
            if (wCompetences > 0.0)
                sim4 = competenceSimilarity.userSimilarity(subject1, subject2);
        } catch (TasteException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        if (wArea > 0.0)
            sim3 = areaSimilarity(subject1, subject2);

        double similarity = wProfessors * sim1 + wContent * sim2 + wArea * sim3 + wCompetences * sim4;
        if (similarity > 1.0)
            similarity = 1.0;
        if (similarity < -1.0)
            similarity = -1.0;

        return similarity;
    }

    /**
     * Compute similarity between two subjects based on their areas in common
     * Since each one belongs to one department, expected similarity is 0 or 1
     */
    private double areaSimilarity(long subject1, long subject2) {
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
    public double itemSimilarity(long subject1, long subject2) {

        if (subject1 < subject2)
            return similarityMaps.get(subject1).get(subject2);
        else if (subject2 < subject1)
            return similarityMaps.get(subject2).get(subject1);
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
        LongPrimitiveIterator allSubjects = professors.get().getUserIDs();

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
            if (professors.get() != null)
                return professors.get().getUserIDs();
            else if (competences.get() != null)
                return competences.get().getUserIDs();
            else if (areas != null)
                return areas.getUserIDs();
        } catch (TasteException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void configure(Configuration config) {
        wProfessors = config.getDouble("professorsWeight");
        wContent = config.getDouble("contentWeight");
        wArea = config.getDouble("areaWeight");
        wCompetences = config.getDouble("competencesWeight");

        double checkSum = wProfessors + wContent + wArea + wCompetences;
        if (checkSum < 0.9999 || checkSum > 1.0001) {
            System.err.println("Total weigth of the criteria for Subject similarity must sum 1 (current " + checkSum + ")");
            System.exit(-1);
        }

        professorsSimilarityName = config.getString("professorsSimilarity");
        competencesSimilarityName = config.getString("competencesSimilarity");
    }
}