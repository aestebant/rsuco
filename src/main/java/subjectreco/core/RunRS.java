package subjectreco.core;

import com.google.common.base.Preconditions;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import subjectreco.recommender.BaseRS;
import subjectreco.util.ClassInstantiator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;

import java.io.File;
import java.util.List;
import java.util.Scanner;

/**
 * Make n recommendations to all users given a recommender configuration
 *
 * @author Aurora Esteban Toscano
 */
class RunRS {

    public static void main(String[] args) {
        Preconditions.checkArgument(args.length == 2, "Use: <bd configuration.xml> <rs configuration.xml>");

        int expectedRecos = 3;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Insert user ID (-1 for list all users)");
        long userID = scanner.nextLong();

        // Load data model configuration
        Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));

        // Load recommender configuration
        Configuration configReco = ConfigLoader.XMLFile(new File(args[1]));

        // Load model management
        ModelManage mm = new ModelManage(configDM);

        // Instantiate the subjectreco.recommender
        Recommender rs = ClassInstantiator.instantiateRecommender(configReco, mm);

        DataModel model = mm.loadModel("ratings");

        long start = System.nanoTime();
        int obtainedRecos = 0;
        int nUsers = 0;

        ((BaseRS) rs).execute(model);
        //rs.execute(model);

        // Show recommendations
        System.out.println("User --> Recommendations");
        if (userID == -1) {
            LongPrimitiveIterator users;
            try {
                users = rs.getDataModel().getUserIDs();
                while (users.hasNext()) {
                    long id = users.nextLong();
                    List<RecommendedItem> recommendations = rs.recommend(id, expectedRecos);
                    obtainedRecos += recommendations.size();
                    nUsers++;
                    System.out.println(id + " --> " + recommendations);
                }
            } catch (TasteException e) {
                e.printStackTrace();
                System.exit(-1);
            }
        } else {
            try {
                System.out.println(userID + " --> " + rs.recommend(userID, expectedRecos));
                nUsers++;
            } catch (TasteException e) {
                e.printStackTrace();
            }
        }

        // Show statistics
        double time = (System.nanoTime() - start) * 1e-9;
        double reach = (double) obtainedRecos / (expectedRecos * nUsers) * 100.0;
        System.out.println();
        System.out.println("Needed time (s):\t" + time);
        System.out.println("Average time per user (s):\t" + time / nUsers);
        System.out.println("Reach of RS (%):\t" + reach);
    }
}