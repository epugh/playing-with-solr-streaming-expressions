package fork.org.tallison.tikaeval.example;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.junit.Test;
import org.apache.tika.metadata.Metadata;

public class TikaServerClientTest {

	@Test
	public void test() {
		fail("Not yet implemented");
	}
	
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

}
