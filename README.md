# FHIR Authorization Server
This Profile is to demonstrate using Ping as a FHIR-compliant AS that can be used to provide tokens for applications using the SMART on FHIR guidelines.

The profile has the following:

* PingFederate -- AuthN and OAuth Token Minting
* PingAccess -- manipulation of the `token response` to add the `patientId` claim

## Pingfederate
Configured with the FHIR Scopes:
* Patient/*.read
* offline_access
* launch/patient

OAuth client:  
`FHIRApp`
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

`{
    "access_token": "eyJhbGciOiJIUzI1NiIsImtpZCI6InN5bSIsInBpLmF0bSI6ImVtNjgifQ.eyJzY29wZSI6IlBhdGllbnQvKi5yZWFkIGxhdW5jaC9wYXRpZW50IG9mZmxpbmVfYWNjZXNzIiwiY2xpZW50X2lkIjoiRkhJUkFwcCIsInN1YiI6ImZmOTllMTNiLTZmZjgtNDBlZi05Y2U1LTFjYzVlZjg5MWQzZSIsImV4cCI6MTU2OTY4OTQ4MH0.GzuHNOXjI4TaR0gAX1gAmSuZhllRBG-o45qIdaxgwe4",
    "refresh_token": "gdJ7Mv2NwyaizSGBr8AWjAIHvKXJDgd92qbXPU3T6B",
    "token_type": "Bearer",
    "expires_in": 7199,
    "patientId": "30006655"
}`


