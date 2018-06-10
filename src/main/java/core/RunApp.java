package core;

import java.io.File;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;

import com.google.common.base.Preconditions;

import recommender.IRecommender;
import util.ConfigLoader;
import util.ModelManage;
import util.PathLoader;
import util.RecommenderLoader;

public class RunApp {
		
	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 4, "Uso: <configuracionBD.xml> <configuracionSR.xml> <id estudiante> <nº recomendaciones>");
		
		org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
		l.setLevel(org.apache.log4j.Level.WARN);
		
		Configuration weightsConfig = ConfigLoader.XMLFile(new File(args[1]));
		
		Configuration recoConfig = null;
		String recoName = weightsConfig.getString("recommender");
		switch (recoName) {
		case "CFUser":
			recoConfig = loadCFUser();
			break;
		case "CFStudent":
			recoConfig = loadCFStudent(weightsConfig);
			break;
		case "CBFSubject":
			recoConfig = loadCBFSubject(weightsConfig);
			break;
		case "HFStudentSubject":
			recoConfig = loadHFStudentSubject(weightsConfig);
			break;
		case "MatrixFactorization":
			recoConfig = loadMatrixFactorization();
			break;
		default:
			System.err.println("Nombre de algoritmo no reconodido");
			System.exit(1);
		}
		// Load data model configuration
		Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));
		// Load model management
		ModelManage mm = new ModelManage(configDM);
		IRecommender recommender = RecommenderLoader.instantiate(recoConfig, mm);
		
		DataModel model = mm.loadModel("ratings");
		
		recommender.execute(model);
		
		long userID = Long.parseLong(args[2]);
		int nRecommendations = Integer.parseInt(args[3]);
		List<RecommendedItem> result = null;
		try {
			result = recommender.getRecommender().recommend(userID, nRecommendations);
		} catch (TasteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		for (RecommendedItem r : result)
			System.out.println(r);
			
	}
	
	private static Configuration loadCFStudent(Configuration weightsConfig) {
			
		double ratingsWeight = weightsConfig.getDouble("ratingsWeight");
		double gradesWeight = weightsConfig.getDouble("gradesWeight");
		double branchWeight = weightsConfig.getDouble("branchWeight");
		
		Configuration configReco = ConfigLoader.XMLFile(PathLoader.getConfigPath("CFStudent.xml"));
		
		configReco.setProperty("recommender.similarity.ratingsWeight", ratingsWeight);
		configReco.setProperty("recommender.similarity.gradesWeight", gradesWeight);
		configReco.setProperty("recommender.similarity.branchWeight", branchWeight);
		
		return configReco;
	}
	
	private static Configuration loadCBFSubject(Configuration weightsConfig) {
		
		double professorsWeight = weightsConfig.getDouble("professorsWeight");
		double areaWeight = weightsConfig.getDouble("areaWeight");
		double competencesWeight = weightsConfig.getDouble("competencesWeight");
		double contentWeight = weightsConfig.getDouble("contentWeight");
		
		Configuration configReco = ConfigLoader.XMLFile(PathLoader.getConfigPath("CBFSubject.xml"));
		
		configReco.setProperty("recommender.similarity.professorsWeight", professorsWeight);
		configReco.setProperty("recommender.similarity.areaWeight", areaWeight);
		configReco.setProperty("recommender.similarity.competencesWeight", competencesWeight);
		configReco.setProperty("recommender.similarity.contentWeight", contentWeight);
		
		return configReco;
	}
	
	private static Configuration loadHFStudentSubject(Configuration weightsConfig) {
		
		double ratingsWeight = weightsConfig.getDouble("ratingsWeight");
		double gradesWeight = weightsConfig.getDouble("gradesWeight");
		double branchWeight = weightsConfig.getDouble("branchWeight");
		
		double professorsWeight = weightsConfig.getDouble("professorsWeight");
		double areaWeight = weightsConfig.getDouble("areaWeight");
		double competencesWeight = weightsConfig.getDouble("competencesWeight");
		double contentWeight = weightsConfig.getDouble("contentWeight");
		
		double studentWeight = weightsConfig.getDouble("studentWeight");
		
		Configuration configReco = ConfigLoader.XMLFile(PathLoader.getConfigPath("HFStudentSubject.xml"));
		
		configReco.setProperty("recommender.cfstudent.similarity.ratingsWeight", ratingsWeight);
		configReco.setProperty("recommender.cfstudent.similarity.gradesWeight", gradesWeight);
		configReco.setProperty("recommender.cfstudent.similarity.branchWeight", branchWeight);
		
		configReco.setProperty("recommender.cbfsubject.similarity.professorsWeight", professorsWeight);
		configReco.setProperty("recommender.cbfsubject.similarity.areaWeight", areaWeight);
		configReco.setProperty("recommender.cbfsubject.similarity.competencesWeight", competencesWeight);
		configReco.setProperty("recommender.cbfsubject.similarity.contentWeight", contentWeight);
		
		configReco.setProperty("recommender.studentWeight", studentWeight);
		
		return configReco;
	}

	private static Configuration loadCFUser() {
		
		Configuration configReco = ConfigLoader.XMLFile(PathLoader.getConfigPath("CFUser.xml"));
		return configReco;
	}
	
	private static Configuration loadMatrixFactorization() {

		Configuration configReco = ConfigLoader.XMLFile(PathLoader.getConfigPath("MatrixFactorization.xml"));
		return configReco;
	}
}
