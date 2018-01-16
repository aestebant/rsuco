package black;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.junit.Test;

import recommender.IRecommender;
import recommender.MatrixFactorization;
import recommender.CBFStudent;
import recommender.CBFStudentSubject;
import recommender.CBFSubject;
import recommender.CFUser;
import util.ConfigLoader;
import util.ModelManage;

/**
 * JUnit based test for unitary tests of recommenders. All tests are based on
 * execute the recommender, ask N recommendations for an user and check that
 * recommender return N recommendations
 * 
 * @author Aurora Esteban Toscano
 */
public class BlackTests {

	private ModelManage mm;
	private DataModel model;
	private IRecommender recommender;

	/**
	 * Common loading for all recommenders
	 * 
	 * @param configurePath
	 *            String with the inputPath to configuration recommender file
	 * @return Configuration of the recommender
	 */
	private Configuration commonPreTest(String configurePath) {
		
		Configuration mmConfig = ConfigLoader.XMLFile("configuration/Model.xml");
		mm = new ModelManage();
		mm.configure(mmConfig.subset("model"));

		model = mm.loadModel("ratings");

		Configuration config = ConfigLoader.XMLFile(configurePath);
		return config;
	}
	
	/**
	 * Common check of all recommenders
	 * 
	 * @throws TasteException
	 */
	private void commonPostTest() throws TasteException {
		List<RecommendedItem> recommendations = recommender.getRecommender().recommend(4, 3);
		assertEquals(3, recommendations.size());
		for(RecommendedItem r : recommendations) {
			assertTrue((r.getValue() >= 1) && (r.getValue() <= 5));
		}
	}
	
	/**
	 * Test CFUser
	 * 
	 * @throws TasteException
	 */
	@Test
	public final void testCFUser() throws TasteException {
		Configuration config = commonPreTest("configuration/CFUser.xml");
		
		recommender = new CFUser();
		recommender.configure(config.subset("recommender"));
		recommender.execute(model);

		commonPostTest();
	}

	/**
	 * Test CBFStudent
	 * 
	 * @throws TasteException
	 */
	@Test
	public final void testCBFStudent() throws TasteException {
		Configuration config = commonPreTest("configuration/CBFStudent.xml");
		
		recommender = new CBFStudent();
		recommender.configure(config.subset("recommender"));
		recommender.execute(model);

		commonPostTest();
	}

	/**
	 * Test CBFSubject
	 * 
	 * @throws TasteException
	 */
	@Test
	public final void testCBFSubject() throws TasteException {
		Configuration config = commonPreTest("configuration/CBFSubject.xml");
		
		recommender = new CBFSubject();
		recommender.configure(config.subset("recommender"));
		recommender.execute(model);

		commonPostTest();
	}

	/**
	 * Test CBFStudentSubject
	 * 
	 * @throws TasteException
	 */
	@Test
	public final void testCBFStudentSubject() throws TasteException {
		Configuration config = commonPreTest("configuration/CBFStudentSubject.xml");
		
		recommender = new CBFStudentSubject();
		recommender.configure(config.subset("recommender"));
		recommender.execute(model);

		commonPostTest();
	}

	/**
	 * Test MatrixFactorization
	 * 
	 * @throws TasteException
	 */
	@Test
	public final void testMatrixFactorization() throws TasteException {
		Configuration config = commonPreTest("configuration/MatrixFactorization.xml");
		
		recommender = new MatrixFactorization();
		recommender.configure(config.subset("recommender"));
		recommender.execute(model);

		commonPostTest();
	}
}
