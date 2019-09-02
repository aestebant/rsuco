package com.uco.rs.recommender;

import com.uco.rs.util.ClassInstantiator;
import com.uco.rs.util.ModelManage;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.neighborhood.CachingUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

/**
 * User-based collaborative filtering that only use ratings for make recommendations
 *
 * @author Aurora Esteban Toscano
 */
public class CFUser extends BaseRS {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private String similarityName;
    private UserNeighborhood neighborhood;
    private int neighborhoodMethod;
    private int topN;
    private double threshold;

    //////////////////////////////////////////////
    // ------------------------------ Constructor
    /////////////////////////////////////////////
    public CFUser(Configuration configuration, ModelManage mm) {
        super(configuration, mm);

        neighborhoodMethod = configuration.getInt("neighborhood.option");
        switch (neighborhoodMethod) {
            case 1:
                topN = configuration.getInt("neighborhood.size");
                break;
            case 2:
                threshold = configuration.getDouble("neighborhood.threshold");
                break;
            default:
                System.err.println("Neighborhood option does not exists");
                System.exit(1);
        }
        similarityName = configuration.getString("similarity");
    }

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Specific logic to load a basic CF recommender
     *
     * @param model DataModel
     */
    @Override
    public void execute(DataModel model) {
        super.execute(model);

        UserSimilarity similarity = ClassInstantiator.instantiateUserSimilarity(similarityName, model);
        try {
            switch (neighborhoodMethod) {
                case 1:
                    this.neighborhood = new CachingUserNeighborhood(new NearestNUserNeighborhood(topN, similarity,
                            baseForRecommendations), baseForRecommendations);
                    break;
                case 2:
                    this.neighborhood = new CachingUserNeighborhood(new ThresholdUserNeighborhood(threshold, similarity,
                            baseForRecommendations), baseForRecommendations);
                    break;
            }
            delegate = new CachingRecommender(new GenericUserBasedRecommender(model, neighborhood, similarity));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}