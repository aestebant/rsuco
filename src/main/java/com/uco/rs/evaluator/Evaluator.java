package com.uco.rs.evaluator;

import com.uco.rs.util.ModelManage;
import org.apache.commons.configuration2.Configuration;
import org.apache.mahout.cf.taste.model.DataModel;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Common interface for diference-based evaluations
 *
 * @author Aurora Esteban Toscano
 */
public interface Evaluator {

    void setDataModel(DataModel model);

    void setRecommenderBuilder(File pathRecommender, ModelManage mm);

    void setRecommenderBuilder(Configuration configRecommender, ModelManage mm);

    void execute();

    void execute(Long seed);

    void execute(List<Long> seed);

    void setOrderedbyNPrefsSubjects(ModelManage mm);

    Map<String, Double[]> getResults();

    void configure(Configuration configuration);
}
