package subjectreco.recommender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.PreferenceInferrer;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import subjectreco.util.IConfiguration;

/**
 * Content based similarity for users based on their grades, ratings and branches
 *
 * @author Aurora Esteban Toscano
 */
public class StudentSimilarity implements UserSimilarity, IConfiguration {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private static ThreadLocal<DataModel> ratings = new ThreadLocal<>();
    private static ThreadLocal<DataModel> grades = new ThreadLocal<>();
    private static DataModel branches;

    private static UserSimilarity ratingsSim;
    private Class<? extends UserSimilarity> iRatingsSim;

    private static UserSimilarity gradesSim;
    private Class<? extends UserSimilarity> iGradesSim;

    // Ratings importance in the face of grades (between 0 and 1)
    private double wRatings;
    private double wGrades;
    private double wBranch;

    private final FastByIDMap<FastByIDMap<Double>> similarityMaps = new FastByIDMap<>();

    protected static final Logger log = LoggerFactory.getLogger(StudentSimilarity.class);

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Initialize data models and compute separated similarities
     *
     * @param ratings  DataModel
     * @param grades   DataModel
     * @param branches DataModel
     * @param config   Configuration
     */
    StudentSimilarity(DataModel ratings, DataModel grades, DataModel branches, Configuration config) {
        configure(config);

        StudentSimilarity.ratings.set(ratings);
        StudentSimilarity.grades.set(grades);
        StudentSimilarity.branches = branches;

        try {
            ratingsSim = new CachingUserSimilarity(
                    iRatingsSim.getDeclaredConstructor(DataModel.class).newInstance(StudentSimilarity.ratings.get()), StudentSimilarity.ratings.get());

            if (wGrades > 0.0) {
                gradesSim = new CachingUserSimilarity(
                        iGradesSim.getDeclaredConstructor(DataModel.class).newInstance(StudentSimilarity.grades.get()), StudentSimilarity.grades.get());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        log.info("Computing similarities based on student");
        computeFinalSimilarities1();
    }

    /**
     * Initialize the triangular matrix of similarities between students
     */
    private void computeFinalSimilarities1() {

        LongPrimitiveIterator students = null;
        try {
            students = ratings.get().getUserIDs();
        } catch (TasteException e) {
            e.printStackTrace();
        }

        // Make the calculation of one similarity suitable of parallelize
        class Wrapper implements Callable<Double[]> {
            private long student1, student2;

            private Wrapper(long st1, long st2) {
                this.student1 = st1;
                this.student2 = st2;
            }

            @Override
            public Double[] call() {
                return new Double[]{(double) student2, computeSimilarity(student1, student2)};
            }
        }

        int count = 1;
        assert students != null;
        while (students.hasNext()) {
            long student1 = students.nextLong();
            // Get all students
            LongPrimitiveIterator others = null;
            try {
                others = ratings.get().getUserIDs();
            } catch (TasteException e) {
                e.printStackTrace();
                System.exit(-1);
            }
            // Only compute similarities in upper diagonal
            others.skip(count);

            // Store similarities of student1 with all others in upper diagonal
            FastByIDMap<Double> map = new FastByIDMap<>();

            // Parallelize computing of similarities
            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            Collection<Callable<Double[]>> collection = Lists.newArrayList();
            while (others.hasNext()) {
                long student2 = others.next();
                collection.add(new Wrapper(student1, student2));
            }

            // Launch parallelization and store result
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

            similarityMaps.put(student1, map);
            count++;
        }
    }

    private void computeFinalSimilarities2() {
        // Make the calculation of one similarity suitable of parallelize
        class Wrapper implements Callable<FastByIDMap<Double>> {
            private long student1;
            private LongPrimitiveIterator others;

            private Wrapper(long st1, LongPrimitiveIterator others) {
                this.student1 = st1;
                this.others = others;
            }

            @Override
            public FastByIDMap<Double> call() {
                FastByIDMap<Double> result = new FastByIDMap<>();
                for (LongPrimitiveIterator it = others; it.hasNext(); ) {
                    long student2 = it.next();
                    double similarity = computeSimilarity(student1, student2);
                    result.put(student2, similarity);
                }
                return result;
            }

            public long getStudent() {
                return student1;
            }
        }

        // Parallelize computing of similarities
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Collection<Callable<FastByIDMap<Double>>> collection = Lists.newArrayList();

        int count = 1;
        LongPrimitiveIterator students = null;
        try {
            students = ratings.get().getUserIDs();
        } catch (TasteException e) {
            e.printStackTrace();
        }
        assert students != null;
        while (students.hasNext()) {
            long student1 = students.nextLong();
            // Get all students
            LongPrimitiveIterator others = null;
            try {
                others = ratings.get().getUserIDs();
            } catch (TasteException e) {
                e.printStackTrace();
                System.exit(-1);
            }
            // Only compute similarities in upper diagonal
            others.skip(count);

            collection.add(new Wrapper(student1, others));
        }

        // Launch parallelization and store result
        try {
            List<Future<FastByIDMap<Double>>> futures = executor.invokeAll(collection);
            for (Future<FastByIDMap<Double>> future : futures) {
                FastByIDMap<Double> result = future.get();
                similarityMaps.put(student1, result);
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



    }

    /**
     * Given a pair of students, mix their similarities in preferences and
     * grades as well as benefit if they belong to the same specialty
     *
     * @param student1 ID of one student
     * @param student2 ID of another student
     * @return computed similarity, in range [-1,1]
     */
    private double computeSimilarity(long student1, long student2) {
        double simRatings = 0.0, simGrades = 0.0, simBranch = 0.0;

        try {
            simRatings = ratingsSim.userSimilarity(student1, student2);
            if (wGrades > 0.0)
                simGrades = gradesSim.userSimilarity(student1, student2);
        } catch (TasteException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        if (wBranch > 0.0)
            simBranch = branchSim(student1, student2);

        // Similarities are combined whit a given weight between 0 and 1
        double similarity = simRatings * wRatings + simGrades * wGrades + simBranch * wBranch;
        if (similarity > 1.0)
            similarity = 1.0;
        if (similarity < -1.0)
            similarity = -1.0;

        //log.info("Similarity between {} and {} computed = {}", student1, student2, similarity);

        return similarity;
    }

    /**
     * Mold relationships between branches of two given students
     *
     * @param student1 ID of one student
     * @param student2 ID of another student
     * @return specialty based similarity between users
     */
    private double branchSim(long student1, long student2) {
        FastIDSet branch1 = null, branch2 = null;
        try {
            branch1 = branches.getItemIDsFromUser(student1);
            branch2 = branches.getItemIDsFromUser(student2);
        } catch (TasteException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        int interSize = branch1.intersectionSize(branch2);
        int unionSize = branch1.size() + branch2.size() - interSize;

        return (double) interSize / (double) unionSize;
    }

    /**
     * Return the final similarity previously computed
     *
     * @param student1 ID of one student
     * @param student2 ID of another student
     * @return final similarity
     */
    @Override
    public double userSimilarity(long student1, long student2) {
        if (student1 < student2)
            return similarityMaps.get(student1).get(student2);
        else if (student2 < student1)
            return similarityMaps.get(student2).get(student1);
        return 1.0;
    }

    @Override
    public void refresh(Collection<Refreshable> arg0) {
    }

    @Override
    public void setPreferenceInferrer(PreferenceInferrer arg0) {
    }

    /**
     * Load specific configuration of the similarity
     *
     * @param config Configuration
     */
    @SuppressWarnings("unchecked")
    @Override
    public void configure(Configuration config) {
        log.info("Loading configuration of student based similarity");

        this.wRatings = config.getDouble("ratingsWeight");
        this.wGrades = config.getDouble("gradesWeight");
        this.wBranch = config.getDouble("branchWeight");

        double checkSum = wRatings + wGrades + wBranch;
        if (checkSum < 0.9999 || checkSum > 1.0001) {
            System.err.println("Total weigth of the criterias must sum 1 (current " + checkSum + ")");
            System.exit(-1);
        }
        try {
            this.iRatingsSim = (Class<? extends UserSimilarity>) Class.forName(config.getString("ratingsSimilarity"));
            if (this.wGrades > 0.0)
                this.iGradesSim = (Class<? extends UserSimilarity>) Class.forName(config.getString("gradesSimilarity"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
