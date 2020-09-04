package com.o19s.tika.parser.spacy;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.ner.corenlp.CoreNLPNERecogniser;
import org.json.simple.JSONObject;
import org.junit.Test;

public class SpacyNERecogniserTest {
	
    @Test
    public void testBasic() throws Exception {
    	
    	Path pdfFile = Paths.get("src","test","resources","tarullo_extracted.txt");

        try (FileInputStream stream = new FileInputStream(pdfFile.toFile())) {
            String text = IOUtils.toString(stream);
            SpacyNERecogniser ner = new SpacyNERecogniser();
            Map<String, Set<String>> names = ner.recognise(text);
            JSONObject jNames = new JSONObject(names);
            System.out.println(jNames.toString());
        }
        
    };
}