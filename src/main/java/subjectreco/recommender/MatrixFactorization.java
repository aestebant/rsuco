package subjectreco.recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.*;
import org.apache.mahout.cf.taste.model.DataModel;


/**
 * Recommender that use matrix factorization of ratings for make recommendations
 *
 * @author Aurora Esteban Toscano
 */
public class MatrixFactorization extends BaseRS {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////
    private Factorizer factorizer;
    private int factOpt, nFeatures, nIterations, nEpochs;
    private double lambda;

    public MatrixFactorization(Configuration configuration) {
        super(configuration);
        factOpt = configuration.getInt("factorizer.option");

        this.nFeatures = configuration.getInt("factorizer.nFeatures");
        if (factOpt == 1 || factOpt == 3 || factOpt == 4)
            this.nIterations = configuration.getInt("factorizer.nIterations");
        if (factOpt == 1 || factOpt == 2)
            this.lambda = configuration.getDouble("factorizer.lambda");
        if (factOpt == 2)
            this.nEpochs = configuration.getInt("factorizer.nEpochs");
    }

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////

    /**
     * Configure the factorizer and launch the recommender
     *
     * @param model DataModel
     */
    @Override
    public void execute(DataModel model) {
        super.execute(model);

        try {
            switch (factOpt) {
                case 1:
                    this.factorizer = new ALSWRFactorizer(model, nFeatures, lambda, nIterations);
                    break;
                case 2:
                    this.factorizer = new ParallelSGDFactorizer(model, nFeatures, lambda, nEpochs);
                    break;

                case 3:
                    this.factorizer = new RatingSGDFactorizer(model, nFeatures, nIterations);
                    break;

                case 4:
                    this.factorizer = new SVDPlusPlusFactorizer(model, nFeatures, nIterations);
                    break;

                default:
                    System.err.println("Factorizer option does not exists");
                    System.exit(1);
            }
            delegate = new CachingRecommender(new SVDRecommender(model, factorizer));
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }
}