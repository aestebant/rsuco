package subjectreco.recommender.subjectSimilarity;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.mahout.cf.taste.common.Refreshable;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import subjectreco.util.IConfiguration;
import subjectreco.util.PathLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Item based similarity for subjects based on common contents
 *
 * @author Aurora Esteban Toscano
 */
public class ContentSimilarity implements ItemSimilarity, IConfiguration {

    //////////////////////////////////////////////
    // -------------------------------- Variables
    /////////////////////////////////////////////

    protected static final Logger log = LoggerFactory.getLogger(ContentSimilarity.class);

    private File database;
    private IndexReader reader;
    private IndexSearcher searcher;

    private Analyzer analyzer;

    //////////////////////////////////////////////
    // ---------------------------------- Methods
    /////////////////////////////////////////////
    ContentSimilarity(Configuration config) {
        configure(config);

        // Access to the document store
        Directory dir = null;
        try {
            dir = FSDirectory.open(Paths.get(database.getAbsolutePath()));
        } catch (IOException e1) {
            e1.printStackTrace();
            System.exit(-1);
        }
        log.info("Loading documentary database from " + database.getAbsolutePath());

        // Create the index reader and search
        reader = null;
        try {
            reader = DirectoryReader.open(dir);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        searcher = new IndexSearcher(reader);
        analyzer = createAnalyzer();
    }

    @Override
    public double[] itemSimilarities(long subject1, long[] others) {
        double[] result = new double[others.length];
        for (int i = 0; i < others.length; i++) {
            result[i] = itemSimilarity(subject1, others[i]);
        }
        return result;
    }

    /**
     * Compute a similarity based on boolean existence of commons teachers and commons skills in two
     * subjects, as well as benefit if they belong to the same department.
     */
    @Override
    public double itemSimilarity(long subject1, long subject2) {
        QueryParser parser = new QueryParser("Id", analyzer);
        Query q1 = null, q2 = null;
        try {
            q1 = parser.parse(QueryParser.escape(String.valueOf(subject1)));
            q2 = parser.parse(QueryParser.escape(String.valueOf(subject2)));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        TopDocs td1 = null, td2 = null;
        try {
            td1 = searcher.search(q1, 1);
            td2 = searcher.search(q2, 1);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        double similarity = 0.0;
        if (td1.scoreDocs.length > 0 && td2.scoreDocs.length > 0) {
            Map<String, Integer> frequencies1 = getTermFrequencies(td1.scoreDocs[0].doc);
            Map<String, Integer> frequencies2 = getTermFrequencies(td2.scoreDocs[0].doc);

            Set<String> terms = new HashSet<>(frequencies1.keySet());
            terms.addAll(frequencies2.keySet());

            RealVector freq1 = toRealVector(frequencies1, terms);
            RealVector freq2 = toRealVector(frequencies2, terms);

            similarity = computeCosineSimilarity(freq1, freq2);
        }

        return similarity;
    }

    @Override
    public long[] allSimilarItemIDs(long subject) {
        return null;
    }

    @Override
    public void refresh(Collection<Refreshable> arg0) {
    }

    private static Analyzer createAnalyzer() {
        // Configure stop words
        CharArraySet analyzerConfig = SpanishAnalyzer.getDefaultStopSet();
        File stopWordsFile = PathLoader.getConfigPath("stopWords.txt");
        List<String> domainStopSet = null;
        try {
            domainStopSet = Files.readAllLines(stopWordsFile.toPath());
        } catch (IOException e2) {
            e2.printStackTrace();
        }
        assert domainStopSet != null;
        analyzerConfig.addAll(domainStopSet);

        // Create Spanish analyzer
        return new SpanishAnalyzer(analyzerConfig);
    }

    /**
     * Transform term vector to frequencies of an index
     */
    private Map<String, Integer> getTermFrequencies(int docId) {
        Terms vector;
        TermsEnum termsEnum;
        Map<String, Integer> frequencies = new HashMap<>();
        try {
            vector = reader.getTermVector(docId, "Content");
            termsEnum = vector.iterator();
            BytesRef text;
            while ((text = termsEnum.next()) != null) {
                String term = text.utf8ToString();
                // Filter numbers
                if (!NumberUtils.isNumber(term)) {
                    int freq = (int) termsEnum.totalTermFreq();
                    frequencies.put(term, freq);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return frequencies;
    }

    /**
     * Transform a count of terms to its frequencies
     */
    private RealVector toRealVector(Map<String, Integer> map, Set<String> terms) {
        RealVector vector = new ArrayRealVector(terms.size());
        int i = 0;
        for (String term : terms) {
            int value = map.getOrDefault(term, 0);
            vector.setEntry(i++, value);
        }
        return vector.mapDivide(vector.getL1Norm());
    }

    /**
     * Compute cosine similarity to a pair of vectors
     */
    private double computeCosineSimilarity(RealVector v1, RealVector v2) {
        return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
    }

    @Override
    public void configure(Configuration config) {
        database = PathLoader.getConfigPath(config.getString("documentaryDB"));
        if (!database.isDirectory()) {
            System.err.println("Documentary database doesn't exist in " + database.getAbsolutePath());
            System.exit(-1);
        }
    }
}