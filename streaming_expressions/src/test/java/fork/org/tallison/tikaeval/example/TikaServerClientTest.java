package fork.org.tallison.tikaeval.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

public class TikaServerClientTest {
	
    @Test
    public void testBasic() throws Exception {
    	
    	Path pdfFile = Paths.get("src","test","resources","tarullo20100520a.pdf");
    	
   
        TikaServerClient client = new TikaServerClient(TikaServerClient.INPUT_METHOD.INPUTSTREAM,"http://localhost:9998/");
        
        TikaInputStream stream = TikaInputStream.get(pdfFile);
        List<Metadata> results = client.parse(pdfFile.toString(), stream);
        System.out.println(results);
        for (Metadata result: results) {
        	System.out.println(result);
        }   
    };
    
    @Test(expected = TikaClientException.class)
    public void testWhatHappensWithNoTikaServer() throws Exception {
    	
    	Path pdfFile = Paths.get("src","test","resources","tarullo20100520a.pdf");
    	
   
        TikaServerClient client = new TikaServerClient(TikaServerClient.INPUT_METHOD.INPUTSTREAM,"http://iamnotrealserver:9998/");
        
        TikaInputStream stream = TikaInputStream.get(pdfFile);
        
        List<Metadata> results = client.parse(pdfFile.toString(), stream);

    };    
    
    @Test(expected = TikaClientException.class)
    public void testWhatHappensWithTikaServerButNoFile() throws Exception {
    	
    	Path pdfFile = Paths.get("src","test","resources");
    	
   
        TikaServerClient client = new TikaServerClient(TikaServerClient.INPUT_METHOD.INPUTSTREAM,"http://iamnotrealserver:9998/");
        
        TikaInputStream stream = TikaInputStream.get(pdfFile);
        
        List<Metadata> results = client.parse(pdfFile.toString(), stream);

    };        

}
