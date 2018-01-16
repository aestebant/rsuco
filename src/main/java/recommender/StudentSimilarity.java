package recommender;

import java.util.Collection;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.similarity.CachingUserSimilarity;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.similarity.PreferenceInferrer;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import com.google.common.base.Preconditions;

import util.IConfiguration;

/**
 * Content based similarity for users that uses their scores, ratings and
 * specialties are specific from Computer Engineering Grade of UCO
 * 
 * @author Aurora Esteban Toscano
 */
public class StudentSimilarity implements UserSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	// Data models with subjects releated information
	private DataModel ratings;
	private DataModel scores;
	private DataModel specialties;

	private UserSimilarity ratingSim;
	private Class<? extends UserSimilarity> iRatingSim;
	
	private UserSimilarity scoreSim;
	private Class<? extends UserSimilarity> iScoreSim;

	// Ratings importance in the face of scores (between 0 and 1)
	private double wRatings;

	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Previous calculations for this hybrid similarity. Initialize the data and
	 * compute separate similarity metrics of each model.
	 * 
	 * @param ratings
	 * @param scores
	 * @param computacion
	 * @param software
	 * @param computadores
	 */
	public void execute(DataModel ratings, DataModel scores, DataModel specialties) {
		Preconditions.checkArgument(wRatings >= 0 && wRatings <= 1, "Ratings weight must be between 0 and 1");
		
		this.ratings = ratings;
		this.scores = scores;
		this.specialties = specialties;

		try {
			this.ratingSim = new CachingUserSimilarity(
					iRatingSim.getDeclaredConstructor(DataModel.class).newInstance(this.ratings), this.ratings);
			this.scoreSim = new CachingUserSimilarity(
					iScoreSim.getDeclaredConstructor(DataModel.class).newInstance(this.scores), this.scores);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void refresh(Collection<Refreshable> arg0) {
	}

	@Override
	public void setPreferenceInferrer(PreferenceInferrer arg0) {
	}

	/**
	 * Given a pair of students, mix their similarities in preferences and
	 * scores as well as benefit if they belong to the same specialty
	 */
	@Override
	public double userSimilarity(long student1, long student2) throws TasteException {
		double simRatings = ratingSim.userSimilarity(student1, student2);
		double simScores = scoreSim.userSimilarity(student1, student2);
		
		// Similarities are combined whit a given weight between 0 and 1
		double similarity = simRatings * (wRatings) + simScores * (1 - wRatings);

		similarity += bonusSpeciality(student1, student2);

		// Enclose the result
		if (similarity > 1)
			similarity = 1;
		if (similarity < -1)
			similarity = -1;

		return similarity;
	}

	/**
	 * Mold relationships between specialties of two given students
	 * 
	 * @param student1
	 * @param student2
	 * @return an extra value to the similarity between users
	 */
	private double bonusSpeciality(long student1, long student2) {
		double bonus = 0.;
		try {
			FastIDSet specialty1 = specialties.getItemIDsFromUser(student1);
			FastIDSet specialty2 = specialties.getItemIDsFromUser(student2);
			for (long sp : specialty1) {
				if (specialty2.contains(sp))
					bonus += 0.1;
			}
			// TODO Bonus between different specialties?
			
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return bonus;
	}

	/**
	 * @see util.IConfiguration#configure(Configuration)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void configure(Configuration config) {
		try {
			this.iRatingSim = (Class<? extends UserSimilarity>) Class
					.forName(config.getString("ratingSimilarity"));
			this.iScoreSim = (Class<? extends UserSimilarity>) Class
					.forName(config.getString("scoreSimilarity"));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		this.wRatings = config.getDouble("ratingsWeight");
	}
}
