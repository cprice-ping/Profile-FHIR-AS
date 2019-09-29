import java.util.ArrayList;
import java.util.Base64;
import java.io.InputStream;
import java.lang.StringBuffer;
import java.io.BufferedReader;
import javax.net.ssl.SSLContext;
import java.io.InputStreamReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.HttpResponse;

def request = exc?.request;
def response = exc?.response;
def method = request?.method;
if(!response)
{
  request?.header?.removeFields("Accept-Encoding");
}
else
{
    def responseHeader = response?.header;
    def body = response?.getBody();
    def bodyStr = "";
    if(body != null)
        bodyStr = new String(body.getContent());
    def accessToken = getClaimValue(bodyStr, "access_token");
  
    if(accessToken)
    {            
      bodyStr = bodyStr.replace("}",",\"" + getClaim() + "\":\"" + getValue(accessToken) + "\"}");
      response?.setBodyContent(bodyStr.getBytes("UTF-8"));
      responseHeader?.removeContentEncoding(); 
      // def bigIntContentLength = (bodyStr.getBytes("UTF-8").length)/2;
      // responseHeader?.setContentLength(bigIntContentLength);
    }
}
anything();

String getClaim()
{
    return "patientId";
}

String getValue(String accessToken)
{
    def sub = getAccessTokenClaim(accessToken, "sub");
  
    String username = "cn=administrator";
    String password = "2FederateM0re";
    
    String encodedBasicAuth = Base64.getEncoder().encodeToString(String.format("%s:%s", username, password).getBytes());
    
    String authHeader = "Basic " + encodedBasicAuth;
    
    String endpoint = "${PD_CONSENT_URL}/consent/v1/consents?actor=" + sub + "&audience=AnyHealth";
  
  	// logger.info("Endpoint {}", endpoint);
    
    String responseStr = getEndpointResponse(endpoint, "GET", authHeader);
  
  	// logger.info("Response {}", responseStr);
    
    if(responseStr == null)
        return "no-value";
    else
        return getClaimValue(responseStr, "_embedded.consents.subject");
}
String getAccessTokenClaim(String accessToken, String claim)
{
    def accessTokenSplit = accessToken.split("\\.");
    def accessTokenBodyEncoded = accessTokenSplit[1];
    def accessTokenBody = new String(Base64.getDecoder().decode(accessTokenBodyEncoded));
  
    return getClaimValue(accessTokenBody, claim);
}

String getEndpointResponse(String endpoint, String method, String authHeader) {
  SSLContextBuilder sslCtx = new SSLContextBuilder();
  SSLContext sslCtxBuild = sslCtx.build();
  SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslCtxBuild);
  HttpClient client = HttpClients.custom().setSSLSocketFactory(socketFactory).build();
  HttpRequestBase request = new HttpGet(endpoint);
  request.addHeader("Authorization", authHeader);
  HttpResponse response = client.execute(request);
  if (response == null) {
    return null;
  }
  BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
  StringBuffer result = new StringBuffer();
  String line = "";
  while ((line = rd.readLine()) != null) {
    result.append(line);
  }
  return result.toString();
}

String getClaimValue(String responseStr, String jsonPath)
    {
        ObjectMapper mapper = new ObjectMapper();       
        JsonNode resultObject = mapper.readTree(responseStr);
      
        logger.info("JSONObject: {}", resultObject);
        
        String [] splitJsonPath = jsonPath.split("\\.");
        
        for(String path : splitJsonPath)
        {
            resultObject = resultObject.findPath(path);
          	logger.info("Claim: {}", path);
          	logger.info("Value: {}", resultObject.textValue);
          
        }
        
        return resultObject.textValue();
        
    }