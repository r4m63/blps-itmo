package blps.itmo.auth.api;

import blps.itmo.auth.security.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class GatewayProxyController {

    private final RestTemplate restTemplate = buildTransparentRestTemplate();

    @Value("${services.claim.base-url}")
    private String claimServiceBaseUrl;

    @Value("${services.penalty.base-url}")
    private String penaltyServiceBaseUrl;

    @RequestMapping(path = "/api/claims/**")
    public ResponseEntity<byte[]> proxyClaims(HttpServletRequest request,
                                              @AuthenticationPrincipal AppUserDetails principal) throws IOException {
        return proxyTo(request, principal, claimServiceBaseUrl);
    }

    @RequestMapping(path = "/api/penalties/**")
    public ResponseEntity<byte[]> proxyPenalties(HttpServletRequest request,
                                                 @AuthenticationPrincipal AppUserDetails principal) throws IOException {
        return proxyTo(request, principal, penaltyServiceBaseUrl);
    }

    private ResponseEntity<byte[]> proxyTo(HttpServletRequest request,
                                           AppUserDetails principal,
                                           String baseUrl) throws IOException {
        String target = baseUrl + request.getRequestURI();
        String query = request.getQueryString();
        if (query != null && !query.isBlank()) {
            target = target + "?" + query;
        }
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(principal.getUser().getId()));
        headers.set("X-User-Role", principal.getUser().getRole().name());
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set("X-Correlation-Id", correlationId);
        }
        String contentType = request.getContentType();
        if (contentType != null) {
            headers.set("Content-Type", contentType);
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body.length == 0 ? null : body, headers);
        ResponseEntity<byte[]> response = restTemplate.exchange(target, method, entity, byte[].class);
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .body(response.getBody());
    }

    private static RestTemplate buildTransparentRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
            }
        });
        return rt;
    }
}
