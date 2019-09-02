package com.uco.rs.core;

import com.uco.rs.util.WrappedHFEval;

public class Pru {

    public static void main(String[] args) {
        WrappedHFEval eval = new WrappedHFEval();

        eval.setStudentWeight(0.5);

        eval.setRatingsWeight(0.5);
        eval.setGradesWeight(0.4);
        eval.setBranchWeight(0.1);

        eval.setRatingsSim("org.apache.mahout.cf.taste.impl.similarity.EuclideanDistanceSimilarity");
        eval.setGradesSim("org.apache.mahout.cf.taste.impl.similarity.EuclideanDistanceSimilarity");

        eval.setNeighborhood(15);

        eval.setProfessorsWeight(0.25);
        eval.setAreaWeight(0.25);
        eval.setCompetencesWeight(0.25);
        eval.setContentWeight(0.25);

        eval.setCompetencesSim("org.apache.mahout.cf.taste.impl.similarity.LogLikelihoodSimilarity");
        eval.setProfessorsSim("org.apache.mahout.cf.taste.impl.similarity.LogLikelihoodSimilarity");

        double rmse = eval.execute();
        System.out.println(rmse);
    }

}
