package core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import util.ContentSubjectManage;
import util.CosineDocumentSimilarity;

public class Pru {

	/*public static void main(String[] args) {
		Path p1 = Paths.get("../pru1.txt");
		Path p2 = Paths.get("../pru2.txt");
		try {
			CosineDocumentSimilarity cds = new CosineDocumentSimilarity(p1, p2);
			System.out.println(cds.getCosineSimilarity());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}*/
	
	public static void main(String[] args) {
		ContentSubjectManage manager = new ContentSubjectManage();
		
		System.out.println(manager.getContentSimilarity(1, 6));
	}

}
