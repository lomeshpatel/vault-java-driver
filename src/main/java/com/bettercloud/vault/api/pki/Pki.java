package com.bettercloud.vault.api.pki;

import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.Json;
import com.bettercloud.vault.json.JsonObject;
import com.bettercloud.vault.json.JsonValue;
import com.bettercloud.vault.response.PkiResponse;
import com.bettercloud.vault.rest.Rest;
import com.bettercloud.vault.rest.RestResponse;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>The implementing class for operations on Vault's <code>/v1/pki/*</code> REST endpoints.</p>
 *
 * <p>This class is not intended to be constructed directly.  Rather, it is meant to used by way of <code>Vault</code>
 * in a DSL-style builder pattern.  See the Javadoc comments of each <code>public</code> method for usage examples.</p>
 */
public class Pki {

    private final VaultConfig config;

    public Pki(final VaultConfig config) {
        this.config = config;
    }

    /**
     * <p>Operation to create an role using the PKI backend.  Relies on an authentication token being present in
     * the <code>VaultConfig</code> instance.</p>
     *
     * <p>This version of the method uses default values for all optional settings.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, token);
     * final Vault vault = new Vault(config);
     * final PkiResponse response = vault.pki().createOrUpdateRole("testRole");
     *
     * assertEquals(204, response.getRestResponse().getStatus());
     * }</pre>
     * </blockquote>
     *
     * @param roleName
     * @return
     * @throws VaultException
     */
    public PkiResponse createOrUpdateRole(final String roleName) throws VaultException {
        return createOrUpdateRole(roleName, null);
    }

    /**
     * <p>Operation to create an role using the PKI backend.  Relies on an authentication token being present in
     * the <code>VaultConfig</code> instance.</p>
     *
     * <p>This version of the method accepts a <code>RoleOptions</code> parameter, containing optional settings
     * for the role creation operation.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, token);
     * final Vault vault = new Vault(config);
     *
     * final RoleOptions options = new RoleOptions()
     *                              .allowedDomains(new ArrayList<String>(){{ add("myvault.com"); }})
     *                              .allowSubdomains(true)
     *                              .maxTtl("9h");
     * final PkiResponse response = vault.pki().createOrUpdateRole("testRole", options);
     *
     * assertEquals(204, response.getRestResponse().getStatus());
     * }</pre>
     * </blockquote>
     *
     * @param roleName
     * @param options
     * @return
     * @throws VaultException
     */
    public PkiResponse createOrUpdateRole(final String roleName, final RoleOptions options) throws VaultException {
        int retryCount = 0;
        while (true) {
            try {
                final String requestJson = roleOptionsToJson(options);
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/pki/roles/" + roleName)
                        .header("X-Vault-Token", config.getToken())
                        .body(requestJson.getBytes("UTF-8"))
                        .connectTimeoutSeconds(config.getOpenTimeout())
                        .readTimeoutSeconds(config.getReadTimeout())
                        .sslPemUTF8(config.getSslPemUTF8())
                        .sslVerification(config.isSslVerify() != null ? config.isSslVerify() : null)
                        .post();

                // Validate restResponse
                if (restResponse.getStatus() != 204) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }
                return new PkiResponse(restResponse, retryCount);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

    /**
     * <p>Operation to retrieve an role using the PKI backend.  Relies on an authentication token being present in
     * the <code>VaultConfig</code> instance.</p>
     *
     * <p>The role information will be populated in the <code>roleOptions</code> field of the <code>PkiResponse</code>
     * return value.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, token);
     * final Vault vault = new Vault(config);
     * final PkiResponse response = vault.pki().getRole("testRole");
     *
     * final RoleOptions details = response.getRoleOptions();
     * }</pre>
     * </blockquote>
     *
     * @param roleName
     * @return
     * @throws VaultException
     */
    public PkiResponse getRole(final String roleName) throws VaultException {
        int retryCount = 0;
        while (true) {
            // Make an HTTP request to Vault
            try {
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/pki/roles/" + roleName)
                        .header("X-Vault-Token", config.getToken())
                        .connectTimeoutSeconds(config.getOpenTimeout())
                        .readTimeoutSeconds(config.getReadTimeout())
                        .sslPemUTF8(config.getSslPemUTF8())
                        .sslVerification(config.isSslVerify() != null ? config.isSslVerify() : null)
                        .get();

                // Validate response
                if (restResponse.getStatus() != 200 && restResponse.getStatus() != 404) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }

                final Map<String, String> data = restResponse.getBody() == null || restResponse.getBody().length == 0
                        ? new HashMap<String, String>()//NOPMD
                        : parseResponseData(restResponse);
                return new PkiResponse(restResponse, retryCount, data);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

    /**
     * <p>Operation to delete an role using the PKI backend.  Relies on an authentication token being present in
     * the <code>VaultConfig</code> instance.</p>
     *
     * <p>A successful operation will return a 204 HTTP status.  A <code>VaultException</code> will be thrown if
     * the role does not exist, or if any other problem occurs.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, token);
     * final Vault vault = new Vault(config);
     *
     * final PkiResponse response = vault.pki().deleteRole("testRole");
     * assertEquals(204, response.getRestResponse().getStatus();
     * }</pre>
     * </blockquote>
     *
     * @param roleName
     * @return
     * @throws VaultException
     */
    public PkiResponse deleteRole(final String roleName) throws VaultException {
        int retryCount = 0;
        while (true) {
            // Make an HTTP request to Vault
            try {
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/pki/roles/" + roleName)
                        .header("X-Vault-Token", config.getToken())
                        .connectTimeoutSeconds(config.getOpenTimeout())
                        .readTimeoutSeconds(config.getReadTimeout())
                        .sslPemUTF8(config.getSslPemUTF8())
                        .sslVerification(config.isSslVerify() != null ? config.isSslVerify() : null)
                        .delete();

                // Validate response
                if (restResponse.getStatus() != 204) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }
                return new PkiResponse(restResponse, retryCount);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }

    /**
     * <p>Operation to generate a new set of credentials (private key and certificate) based on a given role using
     * the PKI backend.  The issuing CA certificate is returned as well, so that only the root CA need be in a
     * client's trust store.</p>
     *
     * <p>A successful operation will return a 204 HTTP status.  A <code>VaultException</code> will be thrown if
     * the role does not exist, or if any other problem occurs.  Example usage:</p>
     *
     * <blockquote>
     * <pre>{@code
     * final VaultConfig config = new VaultConfig(address, token);
     * final Vault vault = new Vault(config);
     *
     * final PkiResponse response = vault.pki().deleteRole("testRole");
     * assertEquals(204, response.getRestResponse().getStatus();
     * }</pre>
     * </blockquote>
     *
     * @param roleName The role on which the credentials will be based.
     * @param commonName The requested CN for the certificate. If the CN is allowed by role policy, it will be issued.
     * @param altNames (optional) Requested Subject Alternative Names, in a comma-delimited list. These can be host names or email addresses; they will be parsed into their respective fields. If any requested names do not match role policy, the entire request will be denied.
     * @param ipSans (optional) Requested IP Subject Alternative Names, in a comma-delimited list. Only valid if the role allows IP SANs (which is the default).
     * @param ttl (optional) Requested Time To Live. Cannot be greater than the role's max_ttl value. If not provided, the role's ttl value will be used. Note that the role values default to system values if not explicitly set.
     * @param format (optional) Format for returned data. Can be pem, der, or pem_bundle; defaults to pem. If der, the output is base64 encoded. If pem_bundle, the certificate field will contain the private key, certificate, and issuing CA, concatenated.
     * @return  // TODO: cert values parsed in PkiResponse
     * @throws VaultException
     */
    public PkiResponse issue(
            final String roleName,
            final String commonName,
            final List<String> altNames,
            final List<String> ipSans,
            final Integer ttl,
            final CredentialFormat format
    ) throws VaultException {
        int retryCount = 0;
        while (true) {
            // Construct a JSON body from inputs
            final JsonObject jsonObject = Json.object();
            if (commonName != null) {
                jsonObject.add("common_name", commonName);
            }
            if (altNames != null && !altNames.isEmpty()) {
                final StringBuilder altNamesCsv = new StringBuilder();//NOPMD
                for (int index = 0; index < altNames.size(); index++) {
                    altNamesCsv.append(altNames.get(index));
                    if (index + 1 < altNames.size()) {
                        altNamesCsv.append(',');
                    }
                }
                jsonObject.add("alt_names", altNamesCsv.toString());
            }
            if (ipSans != null && !ipSans.isEmpty()) {
                final StringBuilder ipSansCsv = new StringBuilder();//NOPMD
                for (int index = 0; index < ipSans.size(); index++) {
                    ipSansCsv.append(ipSans.get(index));
                    if (index + 1 < ipSans.size()) {
                        ipSansCsv.append(',');
                    }
                }
                jsonObject.add("ip_sans", ipSansCsv.toString());
            }
            if (ttl != null) {
                jsonObject.add("ttl", ttl);
            }
            if (format != null) {
                jsonObject.add("format", format.toString());
            }
            final String requestJson = jsonObject.toString();

            // Make an HTTP request to Vault
            try {
                final RestResponse restResponse = new Rest()//NOPMD
                        .url(config.getAddress() + "/v1/pki/issue/" + roleName)
                        .header("X-Vault-Token", config.getToken())
                        .body(requestJson.getBytes("UTF-8"))
                        .connectTimeoutSeconds(config.getOpenTimeout())
                        .readTimeoutSeconds(config.getReadTimeout())
                        .sslPemUTF8(config.getSslPemUTF8())
                        .sslVerification(config.isSslVerify() != null ? config.isSslVerify() : null)
                        .post();

                // Validate response
                if (restResponse.getStatus() != 200 && restResponse.getStatus() != 404) {
                    throw new VaultException("Vault responded with HTTP status code: " + restResponse.getStatus());
                }

                final Map<String, String> data = restResponse.getBody() == null || restResponse.getBody().length == 0
                        ? new HashMap<String, String>()//NOPMD
                        : parseResponseData(restResponse);
                return new PkiResponse(restResponse, retryCount, data);
            } catch (Exception e) {
                // If there are retries to perform, then pause for the configured interval and then execute the loop again...
                if (retryCount < config.getMaxRetries()) {
                    retryCount++;
                    try {
                        final int retryIntervalMilliseconds = config.getRetryIntervalMilliseconds();
                        Thread.sleep(retryIntervalMilliseconds);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                } else {
                    // ... otherwise, give up.
                    throw new VaultException(e);
                }
            }
        }
    }


    private String roleOptionsToJson(final RoleOptions options) {
        final JsonObject jsonObject = Json.object();
        if (options != null) {
            addJsonFieldIfNotNull(jsonObject, "ttl", options.getTtl());
            addJsonFieldIfNotNull(jsonObject, "max_ttl", options.getMaxTtl());
            addJsonFieldIfNotNull(jsonObject, "allow_localhost", options.getAllowLocalhost());
            if (options.getAllowedDomains() != null && options.getAllowedDomains().size() > 0) {
                final StringBuilder allowedDomains = new StringBuilder();
                for (int index = 0; index < options.getAllowedDomains().size(); index++) {
                    allowedDomains.append(options.getAllowedDomains().get(index));
                    if (index + 1 < options.getAllowedDomains().size()) {
                        allowedDomains.append(',');
                    }
                }
                addJsonFieldIfNotNull(jsonObject, "allowed_domains", allowedDomains.toString());
            }
            addJsonFieldIfNotNull(jsonObject, "allow_bare_domains", options.getAllowBareDomains());
            addJsonFieldIfNotNull(jsonObject, "allow_subdomains", options.getAllowSubdomains());
            addJsonFieldIfNotNull(jsonObject, "allow_any_name", options.getAllowAnyName());
            addJsonFieldIfNotNull(jsonObject, "enforce_hostnames", options.getEnforceHostnames());
            addJsonFieldIfNotNull(jsonObject, "allow_ip_sans", options.getAllowIpSans());
            addJsonFieldIfNotNull(jsonObject, "server_flag", options.getServerFlag());
            addJsonFieldIfNotNull(jsonObject, "client_flag", options.getClientFlag());
            addJsonFieldIfNotNull(jsonObject, "code_signing_flag", options.getCodeSigningFlag());
            addJsonFieldIfNotNull(jsonObject, "email_protection_flag", options.getEmailProtectionFlag());
            addJsonFieldIfNotNull(jsonObject, "key_type", options.getKeyType());
            addJsonFieldIfNotNull(jsonObject, "key_bits", options.getKeyBits());
            addJsonFieldIfNotNull(jsonObject, "use_csr_common_name", options.getUseCsrCommonName());
        }
        return jsonObject.toString();
    }

    private JsonObject addJsonFieldIfNotNull(final JsonObject jsonObject, final String name, final Object value) {
        if (value == null) {
            return jsonObject;
        }
        if (value instanceof String) {
            jsonObject.add(name, (String) value);
        } else if (value instanceof Boolean) {
            jsonObject.add(name, (Boolean) value);
        } else if (value instanceof Long) {
            jsonObject.add(name, (Long) value);
        }
        return jsonObject;
    }

    private Map<String, String> parseResponseData(final RestResponse restResponse) throws VaultException {
        final String mimeType = restResponse.getMimeType() == null ? "null" : restResponse.getMimeType();
        if (!mimeType.equals("application/json")) {
            throw new VaultException("Vault responded with MIME type: " + mimeType);
        }
        String jsonString;
        try {
            jsonString = new String(restResponse.getBody(), "UTF-8");//NOPMD
        } catch (UnsupportedEncodingException e) {
            throw new VaultException(e);
        }

        // Parse JSON
        final Map<String, String> data = new HashMap<String, String>();//NOPMD
        for (final JsonObject.Member member : Json.parse(jsonString).asObject().get("data").asObject()) {
            final JsonValue jsonValue = member.getValue();
            if (jsonValue == null || jsonValue.isNull()) {
                continue;
            } else if (jsonValue.isString()) {
                data.put(member.getName(), jsonValue.asString());
            } else {
                data.put(member.getName(), jsonValue.toString());
            }
        }
        return data;
    }
}