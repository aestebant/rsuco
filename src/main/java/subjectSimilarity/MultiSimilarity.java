package subjectSimilarity;

import java.util.Collection;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;

import util.ContentSubjectManage;
import util.IConfiguration;

/**
 * Item based similarity for subjects using multiple criteria: teachers
 * department skills or their program contents
 * 
 * @author Aurora Esteban Toscano
 */
public class MultiSimilarity implements ItemSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	private DataModel teaching;

	// Single criteria similarities
	private TeachingSimilarity teachingSim;
	private DepartmentSimilarity departmentSim;
	private SkillSimilarity skillSim;
	private ContentSimilarity contentSim;

	// Importance of each criteria in final similarity in [0,1]
	private double wTeaching;
	private double wDepartment;
	private double wSkill;
	private double wContent;

	// Threshold to consider two subjects similar
	private static final double THRESHOLD = 0.3;

	// Labels to use in experiment configuration files
	private static final String WTEACHINGLABEL = "teachingWeight";
	private static final String WDEPARTMENTLABEL = "departmentWeight";
	private static final String WSKILLLABEL = "skillWeight";
	private static final String WCONTENTLABEL = "contentWeight";

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Initialize single criteria similarities
	 */
	public MultiSimilarity(DataModel teaching, DataModel departments, DataModel skills, ContentSubjectManage contents,
			Configuration config) {
		configure(config);

		teachingSim = new TeachingSimilarity(teaching, config);
		skillSim = new SkillSimilarity(skills, config);
		departmentSim = new DepartmentSimilarity(departments);
		contentSim = new ContentSimilarity(contents);

		this.teaching = teaching;
	}

	@Override
	public double[] itemSimilarities(long subject1, long[] others) throws TasteException {
		double[] result = new double[others.length];
		for (int i = 0; i < others.length; i++) {
			result[i] = itemSimilarity(subject1, others[i]);
		}
		return result;
	}

	/**
	 * Compute the multi-criteria similarity of two subjects combining the simple
	 * similarities of the subjects
	 * 
	 * @throws TasteException
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
		double sim1 = teachingSim.itemSimilarity(subject1, subject2);
		double sim2 = contentSim.itemSimilarity(subject1, subject2);
		double sim3 = departmentSim.itemSimilarity(subject1, subject2);
		double sim4 = skillSim.itemSimilarity(subject1, subject2);

		return wTeaching * sim1 + wContent * sim2 + wDepartment * sim3 + wSkill * sim4;
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
		FastIDSet similars = new FastIDSet();
		LongPrimitiveIterator allSubjects = teaching.getUserIDs();

		while (allSubjects.hasNext()) {
			long possiblySimilar = allSubjects.nextLong();
			double score = itemSimilarity(subject, possiblySimilar);
			if (score > THRESHOLD)
				similars.add(possiblySimilar);
		}
		return similars.toArray();
	}

	@Override
	public void refresh(Collection<Refreshable> arg0) {
	}

	@Override
	public void configure(Configuration config) {
		this.wTeaching = config.getDouble(WTEACHINGLABEL);
		this.wContent = config.getDouble(WCONTENTLABEL);
		this.wDepartment = config.getDouble(WDEPARTMENTLABEL);
		this.wSkill = config.getDouble(WSKILLLABEL);

		double checkSum = wTeaching + wContent + wDepartment + wSkill;
		if (checkSum < 0.9999 || checkSum > 1.0001) {
			System.err.println("Total weigth of the criterias must sum 1 (current " + checkSum + ")");
			System.exit(-1);
		}
	}

}