package recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.svd.Factorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.ParallelSGDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.RatingSGDFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDPlusPlusFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;


/**
 * Recommender that use MatrixFactorization matrix factorizer method
 * 
 * @author Aurora Esteban Toscano
 */
public class MatrixFactorization extends ARecommender {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	
	private Factorizer factorizer;
	private int factOpt, nFeatures, nIterations, nEpochs;
	private double lambda;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * @see recommender.ARecommender#execute()
	 */
	@Override
	public void execute(DataModel model) {		
		try {
			switch(factOpt){
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

			recommender = new CachingRecommender(new SVDRecommender(model, factorizer));
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@Override
	public void configure(Configuration config) {
		// Standard configuration
		super.configure(config);
		
		factOpt = config.getInt("factorizer.option");
		
		this.nFeatures = config.getInt("factorizer.nFeatures");
		if (factOpt == 1 || factOpt == 3 || factOpt == 4)
			this.nIterations = config.getInt("factorizer.nIterations");
		if (factOpt == 1 || factOpt == 2)
			this.lambda = config.getDouble("factorizer.lambda");
		if (factOpt == 2)
			this.nEpochs = config.getInt("factorizer.nEpochs");
	}
}