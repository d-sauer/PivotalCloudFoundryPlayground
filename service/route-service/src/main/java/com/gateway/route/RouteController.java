package com.gateway.route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Davor Sauer
 */
@RestController
public class RouteController {

    static final String FORWARDED_URL = "X-CF-Forwarded-Url";

    static final String PROXY_METADATA = "X-CF-Proxy-Metadata";

    static final String PROXY_SIGNATURE = "X-CF-Proxy-Signature";

    private static final Logger logger = LoggerFactory.getLogger(RouteController.class);

    private final RestOperations restOperations;

    private final Map<String, String> redirectMapping;

    @Autowired
    RouteController(RestOperations restOperations, RouteProperties routeProperties) {
        this.restOperations = restOperations;
        this.redirectMapping = routeProperties.getRedirect();

        redirectMapping.entrySet().forEach(entry -> {
            logger.info("Redirect mapping {} -> {}", entry.getKey(), entry.getValue());
        });
    }

    @RequestMapping(headers = {FORWARDED_URL, PROXY_METADATA, PROXY_SIGNATURE})
    ResponseEntity<?> route(RequestEntity<byte[]> incoming) throws URISyntaxException {
        this.logger.info("Incoming Request: {}", incoming);

        HttpHeaders incomingHeaders = incoming.getHeaders();
        logger.info("X-CF-Forwarded-Url: {}", incomingHeaders.get(FORWARDED_URL).toArray());
        logger.info("X-CF-Proxy-Metadata: {}", incomingHeaders.get(PROXY_METADATA).toArray());
        logger.info("X-CF-Proxy-Signature: {}", incomingHeaders.get(PROXY_SIGNATURE).toArray());


        RequestEntity<?> outgoing = getOutgoingRequest(incoming);
        this.logger.info("Outgoing Request: {}", outgoing);

        return this.restOperations.exchange(outgoing, byte[].class);
    }

    private RequestEntity<?> getOutgoingRequest(RequestEntity<?> incoming) throws URISyntaxException {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(incoming.getHeaders());

//        headers.remove(PROXY_SIGNATURE);
//        headers.remove(PROXY_METADATA);

//        URI uri = redirect(headers.get(FORWARDED_URL).get(0));
//        URI uri = new URI(headers.get(FORWARDED_URL).get(0));
        URI uri = headers
                .remove(FORWARDED_URL)
                .stream()
                .findFirst()
                .map(this::redirect)
                .orElseThrow(() -> new IllegalStateException(String.format("No %s header present", FORWARDED_URL)));

        logger.info("Outgoing Request URI: {}", uri);

        return new RequestEntity<>(incoming.getBody(), headers, incoming.getMethod(), uri);
    }

    private URI redirect(String forwardUri) {
        logger.info("Checking URI: {}", forwardUri);
        String redirectUrl = getRedirectUrl(forwardUri).orElse(forwardUri);

        if (!Objects.equals(forwardUri, redirectUrl)) {
            logger.info("Redirect URI from: '{}' to '{}'", forwardUri, redirectUrl);
        } else {
            logger.info("Pass by URI: {}", forwardUri);
        }

        return URI.create(redirectUrl);
    }

    private Optional<String> getRedirectUrl(String forwardUri) {
        return Optional.ofNullable(redirectMapping.get(forwardUri));
    }

}
