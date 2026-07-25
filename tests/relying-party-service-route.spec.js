const {
    createHash
} = require("crypto");

const {
    expect,
    test
} = require("@playwright/test");

test("should successfully run Relying Party Service DHIS2 route",
    async function({
        request
    }) {
        const authCode = await fetchAuthCode(request);
        const userInfo = await request.post(
            "/api/routes/relying-party-service/run", {
                data: {
                    "code": authCode,
                    "redirect_uri": "http://localhost:8080/**",
                    "client_id": "IIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArLeYj",
                    "client_assertion_type": "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                    "grant_type": "authorization_code"
                }
            });

        expect(userInfo.status()).toBe(200);
        expect((await userInfo.json()).name).toBe("Siddharth K Mansour");
    });

async function fetchAuthCode(request) {
    const csrfToken = (await (await request.get(
        "http://localhost:8088/v1/esignet/csrf/token")).json()).token;

    const oauthDetails = await request.post(
        "http://localhost:8088/v1/esignet/authorization/v3/oauth-details", {
            data: {
                "request": {
                    "clientId": "IIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArLeYj",
                    "scope": "openid profile",
                    "responseType": "code",
                    "redirectUri": "http://localhost:8080/**",
                },
                "requestTime": new Date().toISOString()
            },
            headers: {
                "X-XSRF-TOKEN": csrfToken
            }
        });

    const oauthDetailsResponse = (await oauthDetails.json()).response;
    await expect.poll(async function() {
        return (await request.post(
            "http://localhost:8088/v1/esignet/authorization/send-otp", {
                data: {
                    "request": {
                        "transactionId": oauthDetailsResponse
                            .transactionId,
                        "individualId": "8267411571",
                        "otpChannels": ["email",
                            "phone"],
                        "captchaToken": "dummy"
                    },
                    "requestTime": new Date()
                        .toISOString()
                },
                headers: {
                    "X-XSRF-TOKEN": csrfToken,
                    "oauth-details-hash": hash(
                        oauthDetailsResponse),
                    "oauth-details-key": oauthDetailsResponse
                        .transactionId
                }
            })).status();
    }).toBe(200);

    const authenticate = await request.post(
        "http://localhost:8088/v1/esignet/authorization/v3/authenticate", {
            data: {
                "request": {
                    "challengeList": [{
                        "authFactorType": "OTP",
                        "challenge": "111111",
                        "format": "alpha-numeric"
                    }],
                    "transactionId": oauthDetailsResponse.transactionId,
                    "individualId": "8267411571"
                },
                "requestTime": new Date().toISOString()
            },
            headers: {
                "X-XSRF-TOKEN": csrfToken,
                "oauth-details-hash": hash(oauthDetailsResponse),
                "oauth-details-key": oauthDetailsResponse.transactionId
            }
        });
    const authenticateResponse = (await authenticate.json()).response;

    const authCodeResponse = await request.post(
        "http://localhost:8088/v1/esignet/authorization/auth-code", {
            data: {
                "request": {
                    "acceptedClaims": ["name"],
                    "permittedAuthorizeScopes": [],
                    "transactionId": authenticateResponse.transactionId
                },
                "requestTime": new Date().toISOString()

            },
            headers: {
                "X-XSRF-TOKEN": csrfToken,
                "oauth-details-hash": hash(oauthDetailsResponse),
                "oauth-details-key": oauthDetailsResponse.transactionId
            }
        });
    return (await authCodeResponse.json()).response.code;
}

function hash(string) {
    const sha256Hash = createHash("sha256").update(JSON.stringify(string))
        .digest("base64");
    return sha256Hash
        .split("=")[0]
        .replace(/\+/g, "-")
        .replace(/\//g, "_");
}