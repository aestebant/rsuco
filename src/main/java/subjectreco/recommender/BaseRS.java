package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.IDRescorer;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import subjectreco.util.ModelManage;

import java.util.Collection;
import java.util.List;

/**
 * Base class for the particular implementation of the Mahout Recommender
 *
 * @author Aurora Esteban Toscano
 */
public abstract class BaseRS implements Recommender {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    // Mahout logic for recommendations
    protected Recommender delegate;
    // Base relation between users and items, normally their ratings.
    protected DataModel baseForRecommendations;
    // Manage relations with the data
    protected static ModelManage mm;
    // Normalize base relation
    Boolean normalization;
    // Add log to the execution
    protected static final Logger log = LoggerFactory.getLogger(BaseRS.class);

    //////////////////////////////////////////////
    // ------------------------------ Constructor
    /////////////////////////////////////////////
    public BaseRS(Configuration configuration) {
        this(configuration, null);
    }

    public BaseRS(Configuration configuration, ModelManage mm) {
        log.info("Loading general recommender configuration");
        normalization = configuration.getBoolean("normalize", false);
        BaseRS.mm = mm;
    }

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

        if (normalization) {
            log.info("Normalizing ratings");
            baseForRecommendations = mm.subtractiveNormalization(model);

        } else
            baseForRecommendations = model;
    }

    @Override
    public DataModel getDataModel() {
        return baseForRecommendations;
    }

    @Override
    public List<RecommendedItem> recommend(long userID, int howMany) throws TasteException {
        return delegate.recommend(userID, howMany);
    }

    @Override
    public List<RecommendedItem> recommend(long userID, int howMany, IDRescorer rescorer) throws TasteException {
        return delegate.recommend(userID, howMany, rescorer);
    }

    @Override
    public float estimatePreference(long userID, long itemID) throws TasteException {
        return delegate.estimatePreference(userID, itemID);
    }

    @Override
    public void setPreference(long userID, long itemID, float value) throws TasteException {
        delegate.setPreference(userID, itemID, value);
    }

    @Override
    public void removePreference(long userID, long itemID) throws TasteException {
        delegate.removePreference(userID, itemID);
    }

    @Override
    public void refresh(Collection<Refreshable> alreadyRefreshed) {
        delegate.refresh(alreadyRefreshed);
    }
}