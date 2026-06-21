package in.ganeshpandey.spring_mdc_api.controller;

import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @PostMapping("/test")
    public Map<String, Object> test(@RequestBody Map<String, Object> body) {
        // Fetch the data present in "key" from the JSON body
        Object keyValue = body != null ? body.get("key") : null;

        Map<String, Object> response = new HashMap<>();
        response.put("thread-name", Thread.currentThread().getName());
        response.put("mdc-values", MDC.getCopyOfContextMap() != null ? MDC.getCopyOfContextMap() : new HashMap<>());
        
        // We can optionally log the key value or just ensure it's fetched
        System.out.println("Fetched key value: " + keyValue);

        return response;
    }
}
