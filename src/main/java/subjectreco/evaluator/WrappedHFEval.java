package subjectreco.evaluator;

import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;
import subjectreco.util.ClassInstantiator;
import subjectreco.util.ConfigLoader;
import subjectreco.util.ModelManage;
import subjectreco.util.PathLoader;

import java.util.Map;

/**
 * Encapsulate all the needed logic to evaluate a recommender from an external algorithm
 *
 * @author Aurora Esteban Toscano
 */
public class WrappedHFEval {

    private Configuration recoConfig;
    private static ModelManage mm;
    private Evaluator evaluator;

    public WrappedHFEval() {
        org.apache.log4j.Logger l = org.apache.log4j.LogManager.getRootLogger();
        l.setLevel(org.apache.log4j.Level.WARN);

        Configuration evalConfig = ConfigLoader.XMLFile(PathLoader.getConfigPath("JCLECEvaluator.xml"));
        evaluator = ClassInstantiator.instantiateEvaluator(evalConfig);

        recoConfig = ConfigLoader.XMLFile(PathLoader.getConfigPath("JCLECRecommender.xml"));

        Configuration configDM = ConfigLoader.XMLFile(PathLoader.getConfigPath("Model.xml"));
        mm = new ModelManage(configDM);
        DataModel ratings = mm.loadModel("ratings");

        evaluator.setDataModel(ratings);
    }

    public void setStudentWeight(double wStudent) {
        recoConfig.setProperty("recommender.studentWeight", wStudent);
    }

    public void setRatingsWeight(double wRatings) {
        recoConfig.setProperty("recommender.cfstudent.similarity.ratingsWeight", wRatings);
    }

    public void setGradesWeight(double wGrades) {
        recoConfig.setProperty("recommender.cfstudent.similarity.gradesWeight", wGrades);
    }

    public void setBranchWeight(double wBranch) {
        recoConfig.setProperty("recommender.cfstudent.similarity.branchWeight", wBranch);
    }

    public void setRatingsSim(String sRatings) {
        recoConfig.setProperty("recommender.cfstudent.similarity.ratingsSimilarity", sRatings);
    }

    public void setGradesSim(String sGrades) {
        recoConfig.setProperty("recommender.cfstudent.similarity.gradesSimilarity", sGrades);
    }

    public void setNeighborhood(int size) {
        recoConfig.setProperty("recommender.cfstudent.neighborhood.size", size);
    }

    public void setProfessorsWeight(double wProfessors) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.professorsWeight", wProfessors);
    }

    public void setCompetencesWeight(double wCompetences) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.competencesWeight", wCompetences);
    }

    public void setAreaWeight(double wArea) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.areaWeight", wArea);
    }

    public void setContentWeight(double wContent) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.contentWeight", wContent);
    }

    public void setProfessorsSim(String sProfessors) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.professorsSimilarity", sProfessors);
    }

    public void setCompetencesSim(String sCompetences) {
        recoConfig.setProperty("recommender.cbfsubject.similarity.competencesSimilarity", sCompetences);
    }

    public double execute() {
        evaluator.setRecommenderBuilder(recoConfig, mm);

        evaluator.execute(50L);

        Map<String, Double[]> results = evaluator.getResults();

        return results.get("RMSE")[0];
    }
}
