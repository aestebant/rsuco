package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.commons.math3.linear.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.*;

public class CosineDocumentSimilarity {

	public static final String CONTENT = "Content";

	private final Set<String> terms = new HashSet<>();
	private final RealVector v1;
	private final RealVector v2;
	private final List<String> stopwords = Arrays.asList("contenidos", "teóricos", "teórico", "práctica", "prácticos", "bloque", "unidad", "tema", "temático", 
			"0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

	public CosineDocumentSimilarity(Path p1, Path p2) throws IOException {
		Directory directory = createIndex(p1, p2);
		IndexReader reader = DirectoryReader.open(directory);
		Map<String, Integer> f1 = getTermFrequencies(reader, 0);
		Map<String, Integer> f2 = getTermFrequencies(reader, 1);
		reader.close();
		
		v1 = toRealVector(f1);
		v2 = toRealVector(f2);
	}

	Directory createIndex(Path p1, Path p2) throws IOException {
		Directory directory = new RAMDirectory();
		CharArraySet cas = SpanishAnalyzer.getDefaultStopSet();
		cas.addAll(stopwords);

		Analyzer analyzer = new SpanishAnalyzer(cas);
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		IndexWriter writer = new IndexWriter(directory, iwc);

		addDocument(writer, p1);
		addDocument(writer, p2);
		
		writer.close();
		return directory;
	}

	/* Indexed, tokenized, stored. */
	public static final FieldType TYPE_STORED = new FieldType();

	static {
		TYPE_STORED.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
		TYPE_STORED.setTokenized(true);
		TYPE_STORED.setStored(true);
		TYPE_STORED.setStoreTermVectors(true);
		TYPE_STORED.setStoreTermVectorPositions(true);
		TYPE_STORED.freeze();
	}

	void addDocument(IndexWriter writer, Path file) throws IOException {
		String content = new String(Files.readAllBytes(file));
		Document doc = new Document();
		Field field = new Field(CONTENT, content, TYPE_STORED);
		doc.add(field);
		writer.addDocument(doc);
	}

	public double getCosineSimilarity() {
		return (v1.dotProduct(v2)) / (v1.getNorm() * v2.getNorm());
	}

	public static double getCosineSimilarity(Path p1, Path p2) throws IOException {
		return new CosineDocumentSimilarity(p1, p2).getCosineSimilarity();
	}

	Map<String, Integer> getTermFrequencies(IndexReader reader, int docId) throws IOException {
		Terms vector = reader.getTermVector(docId, CONTENT);
		TermsEnum termsEnum = vector.iterator();
		Map<String, Integer> frequencies = new HashMap<>();
		BytesRef text = null;
		while ((text = termsEnum.next()) != null) {
			String term = text.utf8ToString();
			int freq = (int) termsEnum.totalTermFreq();
			frequencies.put(term, freq);
			terms.add(term);
		}
		return frequencies;
	}

	RealVector toRealVector(Map<String, Integer> map) {
		RealVector vector = new ArrayRealVector(terms.size());
		int i = 0;
		for (String term : terms) {
			int value = map.containsKey(term) ? map.get(term) : 0;
			vector.setEntry(i++, value);
		}
		return (RealVector) vector.mapDivide(vector.getL1Norm());
	}
}
