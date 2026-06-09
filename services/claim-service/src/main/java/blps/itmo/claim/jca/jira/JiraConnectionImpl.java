package blps.itmo.claim.jca.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.resource.ResourceException;
import jakarta.resource.cci.Connection;
import jakarta.resource.cci.ConnectionMetaData;
import jakarta.resource.cci.Interaction;
import jakarta.resource.cci.LocalTransaction;
import jakarta.resource.cci.ResultSetInfo;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JiraConnectionImpl implements JiraConnection {

    private final String baseUrl;
    private final String user;
    private final String token;
    private final JiraManagedConnection managedConnection;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JiraConnectionImpl(String baseUrl, String user, String token, JiraManagedConnection managedConnection) {
        this.baseUrl = baseUrl;
        this.user = user;
        this.token = token;
        this.managedConnection = managedConnection;
    }

    @Override
    public String createIssue(String projectKey, String summary, String description, List<String> labels) {
        String auth = Base64.getEncoder().encodeToString((user + ":" + token).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + auth);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", projectKey));
        fields.put("summary", summary);
        fields.put("description", description);
        fields.put("issuetype", Map.of("name", "Task"));
        if (labels != null && !labels.isEmpty()) {
            fields.put("labels", labels);
        }
        Map<String, Object> body = Map.of("fields", fields);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/rest/api/2/issue", request, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Jira issue creation failed with status " + response.getStatusCode()
                    + " body=" + response.getBody());
        }
        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("key").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() throws ResourceException {
        managedConnection.close();
    }

    @Override
    public ConnectionMetaData getMetaData() throws ResourceException {
        return null;
    }

    @Override
    public Interaction createInteraction() throws ResourceException {
        return null;
    }

    @Override
    public LocalTransaction getLocalTransaction() throws ResourceException {
        return null;
    }

    @Override
    public ResultSetInfo getResultSetInfo() throws ResourceException {
        return null;
    }

    public void associateConnection(Connection connection) throws ResourceException {
        // not part of CCI Connection interface, kept as helper
    }
}
