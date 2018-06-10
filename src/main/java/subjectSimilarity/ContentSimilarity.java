package subjectSimilarity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.IConfiguration;
import util.PathLoader;

/**
 * Item based similarity for subjects It takes the common teachers of each pair
 * 
 * @author Aurora Esteban Toscano
 */
public class ContentSimilarity implements ItemSimilarity, IConfiguration {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	
	// Threshold to consider two subjects similar
	//private final static double THRESHOLD = 0.3;

	protected static final Logger log = LoggerFactory.getLogger(ContentSimilarity.class);
	
	private File database;
	private IndexReader reader;
	private IndexSearcher searcher;

	private Analyzer analyzer;
	//////////////////////////////////////////////
	// ---------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Previous calculations for this hybrid similarity. Initialize the data and
	 * compute professors-based similarity.
	 * 
	 * @param teaching
	 *            boolean dataModel with subjects as users and teachers as items
	 * @param departments
	 * 
	 */
	public ContentSimilarity(Configuration config) {
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
	public double[] itemSimilarities(long subject1, long[] others) throws TasteException {
		double[] result = new double[others.length];
		for (int i = 0; i < others.length; i++) {
			result[i] = itemSimilarity(subject1, others[i]);
		}
		return result;
	}

	/**
	 * Compute a similarity based on boolean existence of commons teachers and commons skills in two
	 * subjects, as well as benefit if they belong to the same department.
	 * @throws TasteException
	 */
	@Override
	public double itemSimilarity(long subject1, long subject2) throws TasteException {
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
			Map<String, Integer> frecuencies1 = getTermFrequencies(td1.scoreDocs[0].doc);
			Map<String, Integer> frecuencies2 = getTermFrequencies(td2.scoreDocs[0].doc);
			
			Set<String> terms = new HashSet<String>(frecuencies1.keySet());
			terms.addAll(frecuencies2.keySet());
			
			RealVector frec1 = toRealVector(frecuencies1, terms);
			RealVector frec2 = toRealVector(frecuencies2, terms);
			
			similarity = computeCosineSimilarity(frec1, frec2);
		}
		
		//log.info("Similarity between {} and {} computed = {}", subject1, subject2, similarity);
		return similarity;
	}

	@Override
	public long[] allSimilarItemIDs(long subject) throws TasteException {
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
		analyzerConfig.addAll(domainStopSet);

		// Create Spanish analyzer
		Analyzer analyzer = new SpanishAnalyzer(analyzerConfig);
		
		return analyzer;
	}
	
	/**
	 * Transform term vector to frequencies of an index
	 * 
	 * @param reader
	 * @param docId
	 * @return
	 * @throws IOException
	 */
	private Map<String, Integer> getTermFrequencies(int docId) {
		Terms vector = null;
		TermsEnum termsEnum = null;
		Map<String, Integer> frequencies = new HashMap<>();
		try {
			vector = reader.getTermVector(docId, "Content");
			termsEnum = vector.iterator();
			BytesRef text = null;
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
		
		//System.out.println(frequencies);
		return frequencies;
	}
	
	/**
	 * Transform a count of terms to its frequencies
	 * 
	 * @param map
	 * @param terms
	 *            must contain its terms and those of the other subject
	 * @return
	 */
	private RealVector toRealVector(Map<String, Integer> map, Set<String> terms) {
		RealVector vector = new ArrayRealVector(terms.size());
		int i = 0;
		for (String term : terms) {
			int value = map.containsKey(term) ? map.get(term) : 0;
			vector.setEntry(i++, value);
		}
		return (RealVector) vector.mapDivide(vector.getL1Norm());
	}

	/**
	 * Compute cosine similarity to a pair of vectors
	 * 
	 * @param v1
	 * @param v2
	 * @return
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