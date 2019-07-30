package subjectreco.util;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;
import subjectreco.evaluator.IEvaluator;
import subjectreco.recommender.BaseRS;

import java.lang.reflect.InvocationTargetException;

/**
 * Utility class for instantiate classes in a generic way.
 *
 * @author Aurora Esteban Toscano
 */
public class ClassInstantiator {
    //////////////////////////////////////////////
    // ----------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Instantiate a Recommender
     */
    public static Recommender instantiateRecommender(Configuration config, ModelManage mm) {
        String className = config.getString("recommender[@name]");
        Class<? extends BaseRS> rs = null;
        try {
            rs = Class.forName(className).asSubclass(BaseRS.class);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Recommender instance = null;
        try {
            assert rs != null;
            instance = rs.getDeclaredConstructor(Configuration.class, ModelManage.class)
                    .newInstance(config.subset("recommender"), mm);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        return instance;
    }

    public static UserSimilarity instantiateUserSimilarity(String className, DataModel dataModel) {
        Class<? extends UserSimilarity> similarity = null;
        try {
            similarity = Class.forName(className).asSubclass(UserSimilarity.class);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        UserSimilarity instance = null;
        try {
            assert similarity != null;
            instance = new CachingUserSimilarity(similarity.getDeclaredConstructor(DataModel.class)
                    .newInstance(dataModel), dataModel);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | TasteException e) {
            e.printStackTrace();
        }

        return instance;
    }

    public static IEvaluator instantiateEvaluator(Configuration config) {
        String className = config.getString("evaluator[@name]");
        Class<? extends IEvaluator> evaluator = null;
        try {
            evaluator = Class.forName(className).asSubclass(IEvaluator.class);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // Instantiate the given class recommender
        IEvaluator instance = null;
        try {
            assert evaluator != null;
            instance = evaluator.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e1) {
            e1.printStackTrace();
        }

        assert instance != null;
        instance.configure(config.subset("evaluator"));

        return instance;
    }
}
