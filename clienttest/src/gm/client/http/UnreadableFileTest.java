package gm.client.http;

import gm.dto.UploadResultDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A file that cannot be read never reaches the network, and is reported as what it is rather than as
 * a server that did not answer. No server is needed: nothing is ever sent.
 */
class UnreadableFileTest {

    @Test
    @DisplayName("Uploading a file that is not there says the file cannot be read, not that the server is gone")
    void aMissingFileIsNamed() {
        MarketServer server = new MarketServer("http://localhost:1/guess-market", Runnable::run);
        List<UploadResultDto> accepted = new ArrayList<>();
        List<MarketServer.Failure> refused = new ArrayList<>();

        server.upload(new File("no-such-events-file.xml"), accepted::add, refused::add);

        assertTrue(accepted.isEmpty());
        assertEquals(1, refused.size());
        assertTrue(refused.get(0).message().contains("no-such-events-file.xml"), refused.get(0).message());
        assertTrue(refused.get(0).message().contains("cannot be read"), refused.get(0).message());
    }
}
