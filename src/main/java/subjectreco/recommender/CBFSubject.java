package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.CachingItemSimilarity;
import org.apache.mahout.cf.taste.impl.similarity.GenericItemSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import subjectreco.recommender.subjectSimilarity.MultiSimilarity;

/**
 * Content based recommender for subjects that take a specific similarity metric based on professors, competences,
 * contents and knowledge area of subjects.
 *
 * @author Aurora Esteban Toscano
 */
public class CBFSubject extends ARecommender {
    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private DataModel professors;
    private DataModel areas;
    private DataModel competences;

    private Configuration configSim;

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
        try {
            ItemSimilarity similarity = new CachingItemSimilarity(new GenericItemSimilarity(new MultiSimilarity(professors, areas, competences, configSim), model), model);

            log.info("Launching recommender");
            recommender = new CachingRecommender(new GenericItemBasedRecommender(model, similarity));
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Specific configuration of the subject based recommender
     *
     * @param config Configuration
     */
    @Override
    public void configure(Configuration config) {
        // Standard configuration
        super.configure(config);

        log.info("Setting especific CBFSubject configuration");

        double useProfessors = config.getDouble("similarity.professorsWeight");
        if (useProfessors > 0.0) {
            professors = mm.loadModel("professors");
            log.info("Professors information loaded");
        }

        double useCompetences = config.getDouble("similarity.competencesWeight");
        if (useCompetences > 0.0) {
            competences = mm.loadModel("competences");
            log.info("Competences information loaded");
        }

        double useAreas = config.getDouble("similarity.areaWeight");
        double useContent = config.getDouble("similarity.contentWeight");
        if (useAreas > 0.0 || useContent == 1.0) {
            areas = mm.loadModel("areas");
            log.info("Area information loaded");
        }

        configSim = config.subset("similarity");
    }
}
