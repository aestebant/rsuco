package white;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.junit.Test;

import evaluator.IREvaluator;
import util.ConfigLoader;
import util.ModelManage;

public class WhiteTest {

	private ModelManage mm;
	
	public void commonPreTest() {
		mm = new ModelManage();
		Configuration mmConfig = ConfigLoader.XMLFile("configuration/Model.xml");
		mm.configure(mmConfig.subset("model"));
		
	}
	@Test
	public final void testLoadModel() throws TasteException {
		commonPreTest();
		
		DataModel ratings = mm.loadModel("ratings");
		assertEquals(ratings.getNumUsers(), 63);
		assertEquals(ratings.getNumItems(), 58);
		
		DataModel scores = mm.loadModel("scores");
		assertEquals(scores.getNumUsers(), 63);
		assertEquals(scores.getNumItems(), 58);
		
		DataModel specialties = mm.loadModel("specialties");
		assertEquals(specialties.getNumUsers(), 65);
		assertEquals(specialties.getNumItems(), 4);
		
		DataModel departments = mm.loadModel("departments");
		assertEquals(departments.getNumUsers(), 63);
		assertEquals(departments.getNumItems(), 8);
	}

	@Test
	public final void testNormalization() throws TasteException {
		commonPreTest();
		
		DataModel ratings = mm.loadModel("ratings");
		DataModel norm = mm.subtractiveNormalization(ratings);
		
		assertEquals(ratings.getNumUsers(), norm.getNumUsers());
		assertEquals(ratings.getNumItems(), norm.getNumItems());
		
		assertTrue(ratings.getMaxPreference() > norm.getMaxPreference());
		assertTrue(ratings.getMinPreference() > norm.getMinPreference());
	}
	
	@Test
	public final void testFilter() throws TasteException {
		commonPreTest();
		
		int nUsers = 10;
		
		DataModel ratings = mm.loadModel("ratings");
		FastByIDMap<PreferenceArray> selection = new FastByIDMap<PreferenceArray>(nUsers);
		LongPrimitiveIterator it = ratings.getUserIDs();
		for (int i=0; i<nUsers; ++i) {
			long userID = it.nextLong();
			selection.put(userID, ratings.getPreferencesFromUser(userID));
		}
		DataModel selecRatings = new GenericDataModel(selection);
		
		DataModel scores = mm.loadModel("scores");
		DataModel selecScores = mm.filterModel(scores, selecRatings);
		
		assertEquals(selecRatings.getNumUsers(), selecScores.getNumUsers());
		assertEquals(selecRatings.getNumItems(), selecScores.getNumItems());
	}
	
	@Test
	public final void testEvaluator() {
		commonPreTest();
		DataModel ratings = mm.loadModel("ratings");
		IREvaluator iREvaluator = new IREvaluator(ratings);
		Configuration config = ConfigLoader.XMLFile("configuration/IREvaluator.xml");
		iREvaluator.configure(config.subset("evaluator"));
		iREvaluator.execute();
		
		assertTrue(iREvaluator.getStats().getPrecision() <= 1);
		assertTrue(iREvaluator.getStats().getRecall() <= 1);
		assertTrue(iREvaluator.getStats().getFallOut() <= 1);
		assertTrue(iREvaluator.getStats().getNormalizedDiscountedCumulativeGain() <= 1);
		assertTrue(iREvaluator.getStats().getF1Measure() <= 1);
	}
}
