package org.copywaste.breakzradio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;


public class WebStuff {
    public static final String testurl = "http://copywaste.org/status.html";
    private static final String USER_AGENT = "Copywaste Android HttpClient";
    private static final int HTTP_STATUS_OK = 200;
	private static byte[] sBuffer = new byte[512];
    
    public static String getSSLPage(String url) throws ApiException
    {
    	// We're doing SSL here
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
		schemeRegistry.register(new Scheme("http",  PlainSocketFactory.getSocketFactory(), 80));
		
		// Setting up basic communication 
		HttpParams httpParameters = new BasicHttpParams();
		SingleClientConnManager mgr = new SingleClientConnManager(httpParameters, schemeRegistry);
    	
        // Set the timeout in milliseconds until a connection is established.
        int timeoutConnection = 30000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        
        // Set the default socket timeout (SO_TIMEOUT) 
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = 30000;
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        // Loading client with timeout-params
        HttpClient client = new DefaultHttpClient(mgr, httpParameters);

        HttpGet getter = new HttpGet(url);
        getter.setHeader("User-Agent", USER_AGENT);

        try {
            HttpResponse response = client.execute(getter);

            // Check if server response is valid
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != HTTP_STATUS_OK) {
                throw new ApiException("Invalid response from server: " +
                        status.toString());
            }

            // Pull content stream from response
            HttpEntity entity = response.getEntity();
            String contentstring = EntityUtils.toString(entity).trim();
            Logger.log("return from getSSLPage: "+contentstring);
            return contentstring;
        } catch (IOException e) {
            throw new ApiException("Problem communicating with API ("+e.getMessage()+")", e);
        }
    }

    protected static String getPage(String url) throws ApiException
    {
		// Setting up basic communication 
		HttpParams httpParameters = new BasicHttpParams();
    	
        // Set the timeout in milliseconds until a connection is established.
        int timeoutConnection = 2000;
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        
        // Set the default socket timeout (SO_TIMEOUT) 
        // in milliseconds which is the timeout for waiting for data.
        int timeoutSocket = 2000;
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);

        // Loading client with timeout-params
        HttpClient client = new DefaultHttpClient(httpParameters);

        HttpGet getter = new HttpGet(url);
        getter.setHeader("User-Agent", USER_AGENT);

        try {
            HttpResponse response = client.execute(getter);

            // Check if server response is valid
            StatusLine status = response.getStatusLine();
            if (status.getStatusCode() != HTTP_STATUS_OK) {
                throw new ApiException("Invalid response from server: " +
                        status.toString());
            }

            // Pull content stream from response
            HttpEntity entity = response.getEntity();
            InputStream inputStream = entity.getContent();

            ByteArrayOutputStream content = new ByteArrayOutputStream();

            // Read response into a buffered stream
            int readBytes = 0;
            while ((readBytes = inputStream.read(sBuffer)) != -1) {
                content.write(sBuffer, 0, readBytes);
            }

            // Return result from buffered stream
            String returnstring = new String(content.toByteArray());
            Logger.log(returnstring);
            return returnstring;
        } catch (IOException e) {
            throw new ApiException("Problem communicating with API ("+e.getMessage()+")", e);
        }
    }

	/**
	 *  We are checking if we can reach the testurl 
	 * @return boolean Returns true when our testurl is readable and contains 'on'
	 */
    public static boolean webIsReachable()
    {
		String httpreturn = "";
		int maxretry   = 3;
		int retrycount = 0;
		
		while (!httpreturn.equals("on") && maxretry > retrycount) {
			try {
				httpreturn = getPage(testurl).trim();
				Logger.log("testurl:"+httpreturn+".");
				retrycount++;
				
				if (httpreturn.equals("on"))
					return true;
				
			} catch (Exception e) {
				Logger.log(e);
				return false;
			}
			
			// Sleep for a while before retrying
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return false;
    }
    
    /**
	 * Thrown when there were problems contacting the remote API server, either
	 * because of a network error, or the server returned a bad status code.
	 */
	@SuppressWarnings("serial")
	public static class ApiException extends Exception 
	{
		public ApiException(String detailMessage, Throwable throwable) 
		{
			super(detailMessage, throwable);
		}

		public ApiException(String detailMessage) 
		{
			super(detailMessage);
		}
	}

}
