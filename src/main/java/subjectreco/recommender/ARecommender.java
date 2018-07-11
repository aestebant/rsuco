package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import subjectreco.util.ModelManage;

/**
 * Basic class for the particular implementation of the Mahout Recommender
 *
 * @author Aurora Esteban Toscano
 */
public abstract class ARecommender implements IRecommender {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    protected Recommender recommender;
    protected static ModelManage mm = null;

    Boolean normalize;
    DataModel normModel;

    protected static final Logger log = LoggerFactory.getLogger(ARecommender.class);

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Base execution for a Recommender (normalization of rating matrix)
     *
     * @param model DataModel
     */
    public void execute(DataModel model) {
        Preconditions.checkNotNull(mm, "ModelManage not inicializated");
        log.info("Recommender execution starting");

        if (normalize) {
            log.info("Normalizing ratings");
            normModel = mm.subtractiveNormalization(model);

        } else
            normModel = model;

    }

    /**
     * Obtain the recommender generated
     *
     * @return Mahout Recommender
     */
    public Recommender getRecommender() {
        if (recommender == null) {
            System.err.println("Recommender not executed, use execute(DataModel) before");
            return null;
        } else
            return recommender;
    }

    /**
     * Configure the ModelManage used for connect with database
     *
     * @param mm ModelManage
     */
    public void setModelManage(ModelManage mm) {
        ARecommender.mm = mm;
    }

    /**
     * Base configuration for the recommender
     *
     * @param config Configuration
     */
    @Override
    public void configure(Configuration config) {
        log.info("Loading general recommender configuration");

        normalize = config.getBoolean("normalize", false);
    }
}