package subjectreco.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.commons.configuration2.Configuration;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.mahout.common.IOUtils;

public class createDocumentaryDB {

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
	
	private static String indexDestination = "documentaryDB";
	
	public static void main(String[] args) {
		
		Analyzer analyzer = createAnalyzer();
		
		// Load data model manager
		Configuration configDM = ConfigLoader.XMLFile(new File(args[0]));
		ModelManage mm = new ModelManage(configDM);
		
		// Open destination of the creation of document store
		Directory dbStore = null;
		try {
			dbStore = FSDirectory.open(Paths.get(indexDestination));
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(-1);
		}
		
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
		IndexWriter writer = null;
		try {
			writer = new IndexWriter(dbStore, iwc);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		Connection connection = null;
		Statement st = null;
		ResultSet rs = null;
		try {
			connection = mm.getDataSource().getConnection();
			st = connection.createStatement();
			rs = st.executeQuery("SELECT id, content, name FROM uco.uco_subject");

			while (rs.next()) {
				System.out.println("Indexing content of " + rs.getString(3) + " (id = " + rs.getString(1) + ")");

				// Index content
				Document doc = new Document();
				Field field1 = new Field("Content", rs.getString(2), TYPE_STORED);
				Field field2 = new Field("Id", rs.getString(1), TYPE_STORED);
				doc.add(field1);
				doc.add(field2);
				writer.addDocument(doc);
			}
			writer.close();
		} catch (SQLException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
		} finally {
			IOUtils.quietClose(rs, st, connection);
		}
		
		try {
			dbStore = FSDirectory.open(Paths.get(indexDestination));
		} catch (IOException e1) {
			e1.printStackTrace();
			System.exit(-1);
		}
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
}
