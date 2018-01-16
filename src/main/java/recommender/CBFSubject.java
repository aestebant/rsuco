package recommender;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.similarity.GenericItemSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;

import subjectSimilarity.ContentSimilarity;
import subjectSimilarity.TeachersContentSimilarity;
import subjectSimilarity.TeachersDepartmentSkillsSimilarity;
import util.ContentSubjectManage;

/**
 * Content based recommender for subjects that take a specific similarity metric
 *  
 * @author Aurora Esteban Toscano
 */
public class CBFSubject extends ARecommender {
	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel teaching;
	private DataModel departments;
	private DataModel skills;
	
	ContentSubjectManage contentSimilarities;
	
	private GenericItemSimilarity similarity;
	private Configuration configSim;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * @see IRecommender#execute(DataModel)
	 */
	@Override
	public void execute(DataModel model) {
		try {
			similarity = new GenericItemSimilarity(new TeachersContentSimilarity(teaching,contentSimilarities,configSim), model);
				
			recommender = new CachingRecommender(new GenericItemBasedRecommender(model, similarity));
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

		contentSimilarities = new ContentSubjectManage();
		// Load data model that relate subjects with teachers
		teaching = mm.loadModel("teaching");
		/*
		// Load data model that relate subjects with skills
		skills = mm.loadModel("skills");
		
		// Load departments of subjects
		departments = mm.loadModel("departments");*/
		
		configSim = config.subset("similarity");
	}
}
