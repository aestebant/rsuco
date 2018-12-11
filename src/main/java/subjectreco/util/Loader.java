package subjectreco.util;

import org.apache.commons.configuration2.Configuration;
import subjectreco.evaluator.IEvaluator;
import subjectreco.recommender.IRecommender;

import java.lang.reflect.InvocationTargetException;

/**
 * Manage a IRecommender instantiation
 *
 * @author Aurora Esteban Toscano
 */
public class Loader {
    //////////////////////////////////////////////
    // ----------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Instantiate a IRecommender given its configuration.
     */
    public static IRecommender instantiateRecommender(Configuration config, ModelManage mm) {
        // Get the name of the class of the IRecommender
        Class<? extends IRecommender> recoClass = null;
        try {
            recoClass = (Class<? extends IRecommender>) Class.forName(config.getString("recommender[@name]"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // Instantiate the given class recommender
        IRecommender recommender = null;
        try {
            assert recoClass != null;
            recommender = recoClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
            e1.printStackTrace();
        }

        assert recommender != null;
        recommender.setModelManage(mm);
        recommender.configure(config.subset("recommender"));

        return recommender;
    }

    public static IEvaluator instantiateEvaluator(Configuration config) {
        // Get the name of the class of the IRecommender
        Class<? extends IEvaluator> recoClass = null;
        try {
            recoClass = (Class<? extends IEvaluator>) Class.forName(config.getString("evaluator[@name]"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // Instantiate the given class recommender
        IEvaluator evaluator = null;
        try {
            assert recoClass != null;
            evaluator = recoClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
            e1.printStackTrace();
        }

        assert evaluator != null;
        evaluator.configure(config.subset("evaluator"));

        return evaluator;
    }
}
