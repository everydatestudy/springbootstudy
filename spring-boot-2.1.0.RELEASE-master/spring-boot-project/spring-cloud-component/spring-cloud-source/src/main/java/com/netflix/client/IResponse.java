package com.netflix.client;

import java.io.Closeable;
import java.net.URI;
import java.util.Map;

/**
 * Response interface for the client framework.  
 *
 */
public interface IResponse extends Closeable
{
   
   /**从响应中获得实体。若是Http协议，那就是Body体
	// 因为和协议无关，所以这里只能取名叫Payload
    * Returns the raw entity if available from the response 
    */
   public Object getPayload() throws ClientException;
      
   /**
    * A "peek" kinda API. Use to check if your service returned a response with an Entity
    */
   public boolean hasPayload();
   
   /** 如果认为响应成功，则为真，例如，http协议的200个响应代码。
    * @return true if the response is deemed success, for example, 200 response code for http protocol.
    */
   public boolean isSuccess();
   
      
   /**
    * Return the Request URI that generated this response
    */
   public URI getRequestedURI();
   
   /**
    * 
    * @return Headers if any in the response.
    */
   public Map<String, ?> getHeaders();   
}
