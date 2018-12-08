package subjectreco.util;

import org.apache.commons.configuration2.Configuration;
import subjectreco.recommender.IRecommender;

import java.lang.reflect.InvocationTargetException;

/**
 * Manage a IRecommender instantiation
 *
 * @author Aurora Esteban Toscano
 */
public class RecommenderLoader {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private static IRecommender recommender;

    //////////////////////////////////////////////
    // ----------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Instantiate a IRecommender given its configuration.
     */
    @SuppressWarnings("unchecked")
    public static IRecommender instantiate(Configuration config, ModelManage mm) {
        // Get the name of the class of the IRecommender
        Class<? extends IRecommender> recoClass = null;
        try {
            recoClass = (Class<? extends IRecommender>) Class.forName(config.getString("recommender[@name]"));
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }
        // Instantiate the given class subjectreco.recommender
        try {
            assert recoClass != null;
            recommender = recoClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
            e1.printStackTrace();
        }

        recommender.setModelManage(mm);

        // Configure the recommender
        if (recommender != null) {
            recommender.configure(config.subset("recommender"));
        }
        return recommender;
    }
}
