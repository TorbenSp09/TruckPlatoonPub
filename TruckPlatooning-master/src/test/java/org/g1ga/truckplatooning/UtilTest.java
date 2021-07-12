package org.g1ga.truckplatooning;

import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

public class UtilTest {

    @Test
    void testUriComponentsBuilder() {
        String path = "/pfad";
        int port = 1234;
        String url = Util.getBaseUriComponentsBuilder(port, path).toUriString();
        Assert.isTrue(url.equals("http://localhost:1234/pfad"), "getBaseUriComponentsBuilder muss die korrekte Url mit localhost, dem angegebenen Port und dem angegebenen Pfad bilden.");
    }

}
