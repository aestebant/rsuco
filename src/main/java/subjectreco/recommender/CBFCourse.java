package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CachingItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.GenericItemSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import subjectreco.recommender.similarity.CourseSimilarity;
import subjectreco.util.ModelManage;

/**
 * Content based recommender for subjects that take a specific similarity metric based on professors, competences,
 * contents and knowledge area of subjects.
 *
 * @author Aurora Esteban Toscano
 */
public class CBFCourse extends BaseRS {
    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private DataModel professors;
    private DataModel areas;
    private DataModel competences;

    private Configuration configSim;

    //////////////////////////////////////////////
    // ------------------------------ Constructor
    /////////////////////////////////////////////
    public CBFCourse(Configuration configuration, ModelManage mm) {
        super(configuration, mm);

        log.info("Setting especific CBFSubject configuration");

        boolean useProfessors = configuration.getDouble("similarity.professorsWeight") > 0d;
        if (useProfessors) {
            professors = mm.loadModel("professors");
            log.info("Professors information loaded");
        }

        boolean useCompetences = configuration.getDouble("similarity.competencesWeight") > 0d;
        if (useCompetences) {
            competences = mm.loadModel("competences");
            log.info("Competences information loaded");
        }

        boolean useAreas = configuration.getDouble("similarity.areaWeight") > 0d;
        boolean useContent = configuration.getDouble("similarity.contentWeight") == 1d;
        if (useAreas || useContent) {
            areas = mm.loadModel("areas");
            log.info("Area information loaded");
        }

        configSim = configuration.subset("similarity");
    }

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Subject-relative logic of the recommender
     *
     * @param model DataModel
     */
    @Override
    public void execute(DataModel model) {
        super.execute(model);

        try {
            ItemSimilarity similarity = new CachingItemSimilarity(new GenericItemSimilarity(
                    new CourseSimilarity(professors, areas, competences, configSim), model), model);

            log.info("Launching recommender system");
            delegate = new CachingRecommender(new GenericItemBasedRecommender(model, similarity));
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }
}
