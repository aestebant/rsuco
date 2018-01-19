package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;

/**
 * Manage similarities based on counting of common terms in text documents
 * @author Aurora Esteban Toscano
 *
 */
public class ContentSubjectManage {

	//////////////////////////////////////////////
	// -------------------------------- Variables
	/////////////////////////////////////////////
	// Database params
	private static final String CONNECTION = "jdbc:mysql://localhost:3306/uco";
	private static final String QUERY = "SELECT id, content FROM uco.uco_subject";

	// Field of the indexation
	private static final String CONTENT = "Content";

	// Path to file with domain-aware stopwords
	private static final String STOPWORDS = "configuration/stopWords.txt";

	// Indexed, tokenized, stored type of indexing
	private static final FieldType TYPE_STORED = new FieldType();
	static {
		TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		TYPE_STORED.setTokenized(true);
		TYPE_STORED.setStored(true);
		TYPE_STORED.setStoreTermVectors(true);
		TYPE_STORED.setStoreTermVectorPositions(true);
		TYPE_STORED.freeze();
	}

	// Virtual directory where index documents
	private Directory directory;

	// Subjects id
	private List<Long> mapSubject;

	// Frecuencies of indexes for each subject
	private List<Map<String, Integer>> allFrequencies;

	// Symmetric matrix with similarities in [0,1] of each subject with all others
	private double[][] similarities;

	//////////////////////////////////////////////
	// ----------------------------------- Methods
	/////////////////////////////////////////////
	/**
	 * Generate similarities between subject's contents
	 */
	public ContentSubjectManage() {
		directory = new RAMDirectory();
		createIndex();
		computeFrequencies();
		computeSimilarities();
	}

	/**
	 * @return similarity for each pair of subjects
	 */
	public double getContentSimilarity(long idSubject1, long idSubject2) {
		int i = mapSubject.indexOf(idSubject1);
		int j = mapSubject.indexOf(idSubject2);

		return similarities[i][j];
	}

	/**
	 * @return list of subjects id
	 */
	public List<Long> getMapSubject() {
		return mapSubject;
	}

	/**
	 * Collect subjects contents from the database and index them
	 */
	private void createIndex() {
		// Check driver
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception e) {
			System.err.println("MySQL driver not found");
			System.exit(-1);
		}

		// Configure stop words
		CharArraySet analyzerConfig = SpanishAnalyzer.getDefaultStopSet();
		Path stopWordsFile = Paths.get(STOPWORDS);
		List<String> domainStopSet = null;
		try {
			domainStopSet = Files.readAllLines(stopWordsFile);
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(-1);
		}
		analyzerConfig.addAll(domainStopSet);

		// Create Spanish analyzer
		Analyzer analyzer = new SpanishAnalyzer(analyzerConfig);
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		IndexWriter writer = null;
		try {
			writer = new IndexWriter(directory, iwc);
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(-1);
		}

		try {
			// Open database connection
			Connection conexion = DriverManager.getConnection(CONNECTION, "root", "1234");
			Statement s = conexion.createStatement();
			// Query of subject id and content
			ResultSet rs = s.executeQuery(QUERY);

			mapSubject = new ArrayList<Long>();
			while (rs.next()) {
				// Relate subject id with index its the similarity matrix
				mapSubject.add(rs.getLong(1));

				// Index content
				Document doc = new Document();
				Field field = new Field(CONTENT, rs.getString(2), TYPE_STORED);
				doc.add(field);
				writer.addDocument(doc);
			}

			writer.close();
			conexion.close();
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}

	}

	/**
	 * Generate frequencies vector of each subject
	 */
	private void computeFrequencies() {
		allFrequencies = new ArrayList<Map<String, Integer>>(mapSubject.size());
		try {
			IndexReader reader = DirectoryReader.open(directory);
			for (int idx = 0; idx < mapSubject.size(); ++idx) {
				Map<String, Integer> frequencies = getTermFrequencies(reader, idx);
				allFrequencies.add(frequencies);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Transform term vector to frequencies of an index
	 * 
	 * @param reader
	 * @param docId
	 * @return
	 * @throws IOException
	 */
	private Map<String, Integer> getTermFrequencies(IndexReader reader, int docId) throws IOException {
		Terms vector = reader.getTermVector(docId, CONTENT);
		TermsEnum termsEnum = vector.iterator();
		Map<String, Integer> frequencies = new HashMap<>();
		BytesRef text = null;
		while ((text = termsEnum.next()) != null) {
			String term = text.utf8ToString();
			int freq = (int) termsEnum.totalTermFreq();
			frequencies.put(term, freq);
		}
		return frequencies;
	}

	/**
	 * Generate matrix of similarities
	 */
	private void computeSimilarities() {
		similarities = new double[mapSubject.size()][mapSubject.size()];
		for (int i = 0; i < mapSubject.size(); ++i) {
			similarities[i][i] = 1.0;

			Set<String> termsi = allFrequencies.get(i).keySet();

			for (int j = i + 1; j < mapSubject.size(); ++j) {
				// Total terms in each par of subjects
				Set<String> terms = new HashSet<String>(termsi);
				terms.addAll(allFrequencies.get(j).keySet());

				// Transform to real in order to apply similarity
				RealVector vi = toRealVector(allFrequencies.get(i), terms);
				RealVector vj = toRealVector(allFrequencies.get(j), terms);

				// Calculate similarity
				similarities[i][j] = computeCosineSimilarity(vi, vj);

				// Similarity is symmetric
				similarities[j][i] = similarities[i][j];
			}
		}
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
}
