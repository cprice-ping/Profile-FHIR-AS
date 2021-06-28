import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// grab the Request and Response from Exchange
def request = exc?.request;
def response = exc?.response;
def myVersion = "1.1.0";

// In bound we don't have anything to do but we will log the Inbound for troubleshooting
if(!response) 
{
	logger.info("Inbound to token_endpoint, ver "+myVersion);
}
else // Our work in on the response side to enrich the response from PingFederate to make it SMART on FHIR Launch compliant
{
	logger.info("Outbound from token_endpoint, ver "+myVersion);

  // Fetch the response code to make sure we have a good response to actually re-write
  def statusCode = response?.getStatusCode();
  if ( statusCode == 200 ) 
  {

		def body = response?.getBody();
		def bodyStr = "";
		if(body != null)
			bodyStr = new String(body.getContent()); // at this point the body of the response is in bodyStr

    // drop the pre-write body to the log
	  logger.debug("Original Body "+bodyStr);

    // grab the access_token from the response (this will be a JWT with our FHIR ATM in PingFederate)
		def accessToken = getClaimValue(bodyStr, "access_token");

    // if there is no access_token then there is nothing to do
		if(accessToken) 
		{
		
      // pull the payload from the JWT access_token
			def accessTokenPayload = parseAccessToken(accessToken);

      logger.debug("access_token payload: "+accessTokenPayload);

			// get the value for the patient=xxx claim out of the payload (JSON)
			def patientIdAccessTokenValue = getClaimValue(accessTokenPayload, getPatientIdClaim());

			// get the value for the scopes out of the payload (JSON)
			def scopeAccessTokenValue = getClaimValue(accessTokenPayload, getScopeClaim());

			// get the value for the launch=xxx claim out of the payload (JSON)
			def launchAccessTokenValue = getClaimValue(accessTokenPayload, getLaunchClaim());

      // add the values to bodyStr if they were found in the access_token JWT payload and log if level is debug
			if (patientIdAccessTokenValue)
			{
				bodyStr = bodyStr.replace("}",",\"" + getPatientIdClaim() + "\":\"" + patientIdAccessTokenValue + "\"}");
		    logger.debug("Added "+getPatientIdClaim()+" to body");
			}

			if (scopeAccessTokenValue)
			{
				bodyStr = bodyStr.replace("}",",\"" + getScopeClaim() + "\":\"" + scopeAccessTokenValue + "\"}");
		    logger.debug("Added "+getScopeClaim()+" to body");
			}

			if (launchAccessTokenValue)
			{
					bodyStr = bodyStr.replace("}",",\"" + getLaunchClaim() + "\":\"" + launchAccessTokenValue + "\"}");
					logger.debug("Added "+getLaunchClaim()+" to body");
			}

      // emit the updated body to the log if debugging
	    logger.debug("Updated Body "+bodyStr);
	    
	    // replace the body content in the response with the updates we have in bodyStr
			response?.setBodyContent(bodyStr.getBytes("UTF-8"));
			logger.info("Rewrite completed");
			
		} // if accessToken
		else
		{
		  logger.debug("No access_token found");
		}
	     
	} // if not error stats code
	else 
	{
     logger.info("Exiting since status code is: "+statusCode);
  } // if-else on statusCode
  
} // outer else

anything();


String parseAccessToken(String accessToken) {

	// access_token is comprised of header.payload.signature
	def accessTokenSplit = accessToken.split("\\.");
	
	// we want the payload (e.g. where the claims are)
	def accessTokenBodyEncoded = accessTokenSplit[1];

	// convert from URL base 64 encoding to base64 encoding	
	accessTokenBodyEncoded = accessTokenBodyEncoded.replaceAll( "-", "+" );
	accessTokenBodyEncoded = accessTokenBodyEncoded.replaceAll( "_", "/" );
	while (accessTokenBodyEncoded.length() % 4) 
	{
	   accessTokenBodyEncoded = accessTokenBodyEncoded + "=";
	}

	// decode the payload
	def accessTokenBody = new String(Base64.getDecoder().decode(accessTokenBodyEncoded));

	return accessTokenBody;
	
} // parseAccessToken

String getPatientIdClaim()
{
	return "patient";
}

String getScopeClaim()
{
	return "scope";
}

String getLaunchClaim()
{
	return "launch";
}


String getClaimValue(String responseStr, String jsonPath)
{
	ObjectMapper mapper = new ObjectMapper();
	JsonNode resultObject = mapper.readTree(responseStr);

	logger.debug("JSONObject: {}", resultObject);

	String [] splitJsonPath = jsonPath.split("\\.");
	
	for(String path : splitJsonPath)
	{
		resultObject = resultObject.findPath(path);
		logger.debug("Claim: {}", path);
		logger.debug("Value: {}", resultObject.textValue());

	}

	return resultObject.textValue();
}