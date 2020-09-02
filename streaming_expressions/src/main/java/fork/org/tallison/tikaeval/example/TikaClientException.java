package fork.org.tallison.tikaeval.example;


public class TikaClientException extends Exception {

    public TikaClientException(String msg, Exception e) {
        super(msg, e);
    }

    public TikaClientException(String msg) {
        super(msg);
    }
}