package de.uke.iam.parkinson_on_fhir.servlet;

import java.util.List;
import java.util.Base64;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;

/*
 * Handler supporting Basic HTTP authorization.
 */
public class AuthorizationHandler extends AuthorizationInterceptor {
    private String expectedHeader;

    public AuthorizationHandler(String userName, String password) {
        this(String.format("%s:%s", userName, password));
    }

    public AuthorizationHandler(String expectedCredentials) {
        // Encode using UTF-8
        var credentialBytes = expectedCredentials.getBytes();
        var encodedCredentials = Base64.getEncoder().encodeToString(credentialBytes);

        this.expectedHeader = String.format("Basic %s", encodedCredentials);
    }

    @Override
    public List<IAuthRule> buildRuleList(RequestDetails theRequestDetails) {
        String authHeader = theRequestDetails.getHeader("Authorization");
        if (this.expectedHeader.equals(authHeader)) {
            return new RuleBuilder().allowAll().build();
        } else {
            var exception = new AuthenticationException(
                    String.format("%sMissing or invalid Authorization header value", Msg.code(644)));
            exception.addAuthenticateHeaderForRealm("ParkinsonOnFHIR");
            throw exception;
        }
    }

    public static AuthorizationHandler loadFromContext() {
        var authorization = System.getProperty("de.uke.iam.parkinson_on_fhir.authorization");
        if (authorization == null) {
            return null;
        }
        return new AuthorizationHandler(authorization);
    }
}