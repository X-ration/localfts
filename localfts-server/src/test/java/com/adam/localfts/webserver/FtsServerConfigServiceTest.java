package com.adam.localfts.webserver;

import com.adam.localfts.webserver.config.server.TestLanguageText;
import com.adam.localfts.webserver.service.FtsServerConfigService;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@RunWith(SpringRunner.class)
public class FtsServerConfigServiceTest {

    @Autowired
    private FtsServerConfigService ftsServerConfigService;

//    @Test
    public void testPersistConfigChanges() throws IOException {
        ftsServerConfigService.updateTestLanguage(TestLanguageText.Simplified_Chinese, false);
        ftsServerConfigService.persistConfigChanges();
    }

}
