package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.neighborhood.CachingUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import subjectreco.recommender.similarity.StudentSimilarity;
import subjectreco.util.ModelManage;

/**
 * Content based recommender for students: hybrid similarity with ratings, grades and specialty
 *
 * @author Aurora Esteban Toscano
 */
public class CFStudent extends BaseRS {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private DataModel ratings = null;
    private DataModel grades = null;
    private DataModel branches = null;

    private Configuration configSim;

    private UserNeighborhood neighborhood;
    private int neighborhoodMethod;
    private int topN;
    private double threshold;

    //////////////////////////////////////////////
    // ------------------------------ Constructor
    /////////////////////////////////////////////
    public CFStudent(Configuration configuration, ModelManage mm) {
        super(configuration, mm);

        log.info("Loading specific CFStudent configuration");

        boolean useRatings = configuration.getDouble("similarity.ratingsWeight") > 0d;
        if (useRatings) {
            log.info("Loading ratings data model");
            ratings = mm.loadModel("ratings");
            if (normalization) {
                log.info("Normalizing ratings");
                ratings = mm.subtractiveNormalization(ratings);
            }
        }

        boolean useGrades = configuration.getDouble("similarity.gradesWeight") > 0d;
        if (useGrades) {
            log.info("Loading grades data model");
            grades = mm.loadModel("grades");
            if (normalization) {
                log.info("Normalizing grades");
                grades = mm.subtractiveNormalization(grades);
            }
        }

        boolean useBranch = configuration.getDouble("similarity.branchWeight") > 0d;
        if (useBranch) {
            log.info("Loading branches data model");
            branches = mm.loadModel("branches");
        }

        this.neighborhoodMethod = configuration.getInt("neighborhood.option");
        if (neighborhoodMethod == 1)
            this.topN = configuration.getInt("neighborhood.size");
        else if (neighborhoodMethod == 2)
            this.threshold = configuration.getDouble("neighborhood.threshold");

        configSim = configuration.subset("similarity");
    }

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Logic corresponding to Student based recommender
     *
     * @param model DataModel
     */
    @Override
    public void execute(DataModel model) {
        super.execute(model);

        try {
            UserSimilarity similarity = new CachingUserSimilarity(new StudentSimilarity(ratings, grades, branches,
                    configSim), model);

            log.info("Creating neighborhood");
            switch (neighborhoodMethod) {
                case 1:
                    this.neighborhood = new CachingUserNeighborhood(new NearestNUserNeighborhood(topN, similarity,
                            baseForRecommendations), baseForRecommendations);
                    break;
                case 2:
                    this.neighborhood = new CachingUserNeighborhood(new ThresholdUserNeighborhood(threshold, similarity,
                            baseForRecommendations), baseForRecommendations);
                    break;
                default:
                    System.err.println("Neighborhood option does not exists");
                    System.exit(1);
            }

            log.info("Launching recommender system");
            delegate = new CachingRecommender(new GenericUserBasedRecommender(model, neighborhood, similarity));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
