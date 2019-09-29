# FHIR Authorization Server
This Profile is to demonstrate using Ping as a FHIR-compliant AS that can be used to provide tokens for applications using the SMART on FHIR guidelines.

The profile has the following:

* PingFederate -- AuthN and OAuth Token Minting
* PingAccess -- manipulation of the `token response` to add the `patientId` claim
* PingDirectory -- User Credentials and ConsentAPI used to authenticate to the FHIR App and store proxied accounts

## Pingfederate
Configured with the FHIR Scopes:
* `Patient/*.read`
* `offline_access`
* `launch/patient`

OAuth client:  
`clinet_id` == `FHIRApp`  
`redirect_uri` == https://redirect.health.com/HealthProviderLogin/

PERSISTENT_GRANT_LIFETIME has been configured to provide **optional** `refresh_tokens` when the `offline_access` scope is requested:  
https://support.pingidentity.com/s/document-item?bundleId=pingfederate-93&topicId=yxs1564002993011.html

The OGNL for this can be found in the OAuth APC Mapping

## PingAccess
Configured to proxy connections to the PingFederate AS endpoints. This is needed to manipulate the response from the AS to simulate a FHIR AS.

* https://{{pa}}/as/authorization.oauth2 (Used to request an AuthZ Code)
* https://{{pa}}/as/token.oauth2 (Used to request a token)

There is a Content Rewrite rule that is applied to the Token endpoint.

This rule contains a Groovy script that takes the `sub` from the `access_token` and uses it to make a call to the PingDirectory ConsentAPI and retrieves the `subject` of the found record. This is then placed into a `patientid` claim in the resulting token **response** back to the FHIR client. 

Sample response from the Proxied AS:

```json
{  
    "access_token": "eyJhbGciOiJIUzI1NiIsImtpZCI6InN5bSIsInBpLmF0bSI6ImVtNjgifQ.eyJzY29wZSI6IlBhdGllbnQvKi5yZWFkIGxhdW5jaC9wYXRpZW50IG9mZmxpbmVfYWNjZXNzIiwiY2xpZW50X2lkIjoiRkhJUkFwcCIsInN1YiI6ImZmOTllMTNiLTZmZjgtNDBlZi05Y2U1LTFjYzVlZjg5MWQzZSIsImV4cCI6MTU2OTY4OTQ4MH0.GzuHNOXjI4TaR0gAX1gAmSuZhllRBG-o45qIdaxgwe4",  
    "refresh_token": "gdJ7Mv2NwyaizSGBr8AWjAIHvKXJDgd92qbXPU3T6B",
    "token_type": "Bearer",  
    "expires_in": 7199,  
    "patientId": "30006655"  
}
```

## PingDirectory
This PingDirectory instance uses the AnyHealth dataset (https://github.com/cprice-ping/Profile-AnyHealth) and adds a new Consent Definition \ Record.

This record contains the list of Proxied accounts that are available, and uses the `subject` claim to represent the one that should be sent in the token response.

During the Application authorization, the Consent page should list the proxies and stamp the selected one to the record. PingAccess will extract the `subject` from that record based on the `sub` of the `access_token` and present it in the response.

The call made looks like this:  
https://{{pingdir}}/consent/v1/consents?actor={{sub}}&definition=FHIRApp&audience=AnyHealth

With a ConsentAPI response that looks similar to this:

```json
{
    "_embedded": {
        "consents": [
            {
                "_links": {
                    "localization": {
                        "href": "https://int-docker.cpricedomain.ping-eng.com:1443/consent/v1/definitions/FHIRApp/localizations/en",
                        "hreflang": "en"
                    },
                    "self": {
                        "href": "https://int-docker.cpricedomain.ping-eng.com:1443/consent/v1/consents/44585431-bddc-4c35-acaf-791eb9ba6643"
                    },
                    "definition": {
                        "href": "https://int-docker.cpricedomain.ping-eng.com:1443/consent/v1/definitions/FHIRApp"
                    }
                },
                "id": "44585431-bddc-4c35-acaf-791eb9ba6643",
                "status": "accepted",
                "subject": "30006655",
                "actor": "ff99e13b-6ff8-40ef-9ce5-1cc5ef891d3e",
                "actorDN": "entryUUID=ff99e13b-6ff8-40ef-9ce5-1cc5ef891d3e,ou=People,o=anyhealth.org",
                "audience": "AnyHealth",
                "definition": {
                    "id": "FHIRApp",
                    "version": "1.0",
                    "currentVersion": "1.0",
                    "locale": "en"
                },
                "dataText": "Allow FHIR App to access data on the SUBJECT record",
                "purposeText": "Used to allow Roles or Individuals to have access to all or specific elements of the SUBJECT medical record",
                "data": {
                    "proxies": [
                        {
                            "scopes": "Patient/*.read launch/patient offline_access",
                            "clientID": "APPLE",
                            "proxy": true,
                            "patientID": "11000123455",
                            "resources": [
                                "FHIR resources"
                            ]
                        },
                        {
                            "scopes": "Patient/*.read launch/patient offline_access",
                            "clientID": "APPLE",
                            "proxy": false,
                            "patientID": "1000123466",
                            "resources": [
                                "FHIR resources"
                            ]
                        }
                    ]
                }
            }
        ]
    },
    "_links": {
        "self": {
            "href": "https://int-docker.cpricedomain.ping-eng.com:1443/consent/v1/consents?actor=ff99e13b-6ff8-40ef-9ce5-1cc5ef891d3e&audience=AnyHealth"
        }
    },
    "count": 1,
    "size": 1
}
```

### Postman
A Postman collection for interacting with this Profile can be found here:  
https://www.getpostman.com/collections/804c9df26fb560e2d9a8

An Environment is needed with the following variables configured:  
* `as` -- The FQDN \ Port of PingAccess that is proxying PingFed

The environment will be used to store and transfer the AuthZ Code between the calls.

The calls will POST a credential into the AS (via PingAccess) and receive an AuthZ Code. This code can be swapped for a Token set that will show the modifiable token response.
