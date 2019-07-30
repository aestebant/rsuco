package subjectreco.core;

import com.google.common.base.Preconditions;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import subjectreco.recommender.BaseRS;
import subjectreco.util.ClassInstantiator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;

import java.io.File;
import java.util.List;

/**
 * Encapsulate the logic to run any recommender from a external application and return the recommendations in an
 * specific format.
 *
 * @author Aurora Esteban Toscano
 */
public class WrappedRS {

    public static void main(String[] args) {
        Preconditions.checkArgument(args.length == 4, "Uso: <configuracionBD.xml> <configuracionSR.xml> <id estudiante> <nº recomendaciones>");

        org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
        l.setLevel(org.apache.log4j.Level.WARN);

        // Load data model configuration
        Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));
        // Load model management
        ModelManage mm = new ModelManage(configDM);

        DataModel model = mm.loadModel("ratings");

        // Load recommender configuration
        Configuration recoConfig = ConfigLoader.XMLFile(new File(args[1]));
        // Instantiate the RS
        Recommender recommender = ClassInstantiator.instantiateRecommender(recoConfig, mm);

        ((BaseRS)recommender).execute(model);

        // Obtain the recommendations for the specified user
        long userID = Long.parseLong(args[2]);
        int nRecommendations = Integer.parseInt(args[3]);
        List<RecommendedItem> result = null;
        try {
            result = recommender.recommend(userID, nRecommendations);
        } catch (TasteException e) {
            e.printStackTrace();
        }

        // Print the recommendations in specified format
        assert result != null;
        for (RecommendedItem r : result)
            System.out.println(r);
    }
}
