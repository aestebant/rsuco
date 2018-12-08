package subjectreco.recommender;

import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import subjectreco.util.IConfiguration;
import subjectreco.util.ModelManage;

/**
 * Common interface for recommenders in this library
 *
 * @author Aurora Esteban Toscano
 */
public interface IRecommender extends IConfiguration {
    void execute(DataModel model);

    void setModelManage(ModelManage mm);

    Recommender getRecommender();
}