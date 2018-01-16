package util;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
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

public class ContentSubjectManage {

	private Directory directory;
	private static final String CONTENT = "Content";
	private final List<String> stopwords = Arrays.asList("contenidos", "teóricos", "teórico", "práctica", "prácticos",
			"bloque", "unidad", "tema", "temático", "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "0", "1", "2",
			"3", "4", "5", "6", "7", "8", "9");
	/* Indexed, tokenized, stored. */
	private static final FieldType TYPE_STORED = new FieldType();
	static {
		TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		TYPE_STORED.setTokenized(true);
		TYPE_STORED.setStored(true);
		TYPE_STORED.setStoreTermVectors(true);
		TYPE_STORED.setStoreTermVectorPositions(true);
		TYPE_STORED.freeze();
	}

	private List<Long> mapSubject;
	private List<Map<String, Integer>> allFrecuencies;

	private double[][] similarities;

	public ContentSubjectManage() {
		directory = new RAMDirectory();
		createIndex();
		computeFrecuencies();
		computeSimilarities();
	}

	public double getContentSimilarity(long idSubject1, long idSubject2) {
		int i = mapSubject.indexOf(idSubject1);
		int j = mapSubject.indexOf(idSubject2);

		return similarities[i][j];
	}

	public List<Long> getMapSubject() {
		return mapSubject;
	}
	
	private void createIndex() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			Connection conexion = DriverManager.getConnection("jdbc:mysql://localhost:3306/uco", "root", "1234");

			Statement s = conexion.createStatement();
			ResultSet rs = s.executeQuery("SELECT id, content FROM uco.uco_subject");

			CharArraySet cas = SpanishAnalyzer.getDefaultStopSet();
			cas.addAll(stopwords);

			Analyzer analyzer = new SpanishAnalyzer(cas);
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
			IndexWriter writer = new IndexWriter(directory, iwc);

			mapSubject = new ArrayList<Long>();
			while (rs.next()) {
				mapSubject.add(rs.getLong(1));

				Document doc = new Document();
				Field field = new Field(CONTENT, rs.getString(2), TYPE_STORED);
				doc.add(field);
				writer.addDocument(doc);
			}

			writer.close();
			conexion.close();
		} catch (SQLException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void computeFrecuencies() {
		allFrecuencies = new ArrayList<Map<String, Integer>>(mapSubject.size());
		try {
			IndexReader reader = DirectoryReader.open(directory);
			for (int idx = 0; idx < mapSubject.size(); ++idx) {
				Map<String, Integer> frequencies = getTermFrequencies(reader, idx);
				allFrecuencies.add(frequencies);
			}
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
	}

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

	private void computeSimilarities() {
		similarities = new double[mapSubject.size()][mapSubject.size()];
		for (int i = 0; i < mapSubject.size(); ++i) {
			similarities[i][i] = 1.0;

			Set<String> termsi = allFrecuencies.get(i).keySet();

			for (int j = i + 1; j < mapSubject.size(); ++j) {
				Set<String> terms = new HashSet<String>(termsi);
				terms.addAll(allFrecuencies.get(j).keySet());

				RealVector vi = toRealVector(allFrecuencies.get(i), terms);
				RealVector vj = toRealVector(allFrecuencies.get(j), terms);

				similarities[i][j] = computeCosineSimilarity(vi, vj);
				similarities[j][i] = similarities[i][j];
			}
		}
	}

	private RealVector toRealVector(Map<String, Integer> map, Set<String> terms) {
		RealVector vector = new ArrayRealVector(terms.size());
		int i = 0;
		for (String term : terms) {
			int value = map.containsKey(term) ? map.get(term) : 0;
			vector.setEntry(i++, value);
		}
		return (RealVector) vector.mapDivide(vector.getL1Norm());
	}

	private double computeCosineSimilarity(RealVector v1, RealVector v2) {
		return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
	}
}
