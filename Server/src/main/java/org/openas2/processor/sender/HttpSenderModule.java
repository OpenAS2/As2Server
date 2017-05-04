package org.openas2.processor.sender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.InternetHeaders;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.util.Properties;


public abstract class HttpSenderModule extends BaseSenderModule implements SenderModule {
	
	  public static final String PARAM_READ_TIMEOUT = "readtimeout";
	  public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
	  
  	private Log logger = LogFactory.getLog(HttpSenderModule.class.getSimpleName());
	
    public HttpURLConnection getConnection(String url, boolean output, boolean input,
        boolean useCaches, String requestMethod) throws OpenAS2Exception
    {
    	if (url == null) throw new OpenAS2Exception("HTTP sender module received empty URL string.");
        try {
        	System.setProperty("sun.net.client.defaultReadTimeout", getParameter(PARAM_READ_TIMEOUT, "60000"));
            System.setProperty("sun.net.client.defaultConnectTimeout", getParameter(PARAM_CONNECT_TIMEOUT, "60000"));

            initializeProxyAuthenticator();
            HttpURLConnection conn;
            URL urlObj = new URL(url);
            if (urlObj.getProtocol().equalsIgnoreCase("https"))
            {
            	HttpsURLConnection connS = (HttpsURLConnection) urlObj.openConnection(getProxy("https"));
            	String selfSignedCN = System.getProperty("org.openas2.cert.TrustSelfSignedCN");
				if (selfSignedCN != null)
				{
					File file = new File("jssecacerts");
					if (file.isFile() == false)
					{
			            char SEP = File.separatorChar;
			            File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
			            /* Check if this is a JDK home */
			            if (!dir.isDirectory())
			            {
			                dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
			            }
			            if (!dir.isDirectory()) {
			            	throw new OpenAS2Exception(
			                        "The JSSE folder could not be identified. Please check that JSSE is installed.");
			            }
			            file = new File(dir, "jssecacerts");
						if (file.isFile() == false)
						{
							file = new File(dir, "cacerts");
						}
					}
					InputStream in = new FileInputStream(file);
					try
					{
						KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
						ks.load(in, "changeit".toCharArray());
						in.close();
						SSLContext context = SSLContext.getInstance("TLS");
						TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory
								.getDefaultAlgorithm());
						tmf.init(ks);
						X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
						SelfSignedTrustManager tm = new SelfSignedTrustManager(defaultTrustManager);
						tm.setTrustCN(selfSignedCN);
						context.init(null, new TrustManager[] { tm }, null);
						connS.setSSLSocketFactory(context.getSocketFactory());
					} catch (Exception e)
					{
			        	logger.error("URL connection failed connecting to : " + url, e);
						throw new OpenAS2Exception("Error in self signed certificate management", e);
					}
				}
				conn = connS;
			} else
			{
				conn = (HttpURLConnection) urlObj.openConnection(getProxy("http"));
			}
            conn.setDoOutput(output);
            conn.setDoInput(input);
            conn.setUseCaches(useCaches);
            conn.setRequestMethod(requestMethod);

            return conn;
        } catch (IOException ioe) {
        	logger.error("URL connection failed connecting to: " + url, ioe);
            throw new WrappedException(ioe);
        }
    }
    
    private Proxy getProxy(String protocol) throws OpenAS2Exception
    {
        String proxyHost =Properties.getProperty(protocol + ".proxyHost", null);
        if (proxyHost == null) proxyHost = System.getProperty(protocol + ".proxyHost");
        if (logger.isDebugEnabled()) logger.debug("PROXY HOST: " + proxyHost + " (protocol=" + protocol + ")");
    	if (proxyHost == null) return Proxy.NO_PROXY;
        String proxyPort =Properties.getProperty(protocol + ".proxyPort", null);
        if (proxyPort == null) proxyPort = System.getProperty(protocol + ".proxyPort");
        if (proxyPort == null) throw new OpenAS2Exception("Missing PROXY port since Proxy host is set");
        int port = Integer.parseInt(proxyPort);
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port));

    }

    private void initializeProxyAuthenticator() {
        String proxyUser1 = Properties.getProperty("http.proxyUser", null);
        final String proxyUser = proxyUser1 == null?System.getProperty("http.proxyUser"):proxyUser1;
        String proxyPwd1 =Properties.getProperty("http.proxyPassword", null);
        final String proxyPassword = proxyPwd1 == null?System.getProperty("http.proxyPassword"):proxyPwd1;

        if (proxyUser != null && proxyPassword != null) {
            Authenticator.setDefault(
              new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                  return new PasswordAuthentication(
                    proxyUser, proxyPassword.toCharArray()
                  );
                }
              }
            );
        }
    }
    
    // Copy headers from an Http connection to an InternetHeaders object
    protected void copyHttpHeaders(HttpURLConnection conn, InternetHeaders headers) {
        Iterator<Map.Entry<String,List<String>>> connHeadersIt = conn.getHeaderFields().entrySet().iterator();
        Iterator<String> connValuesIt;
        Map.Entry<String,List<String>> connHeader;
        String headerName;

        while (connHeadersIt.hasNext()) {
            connHeader = connHeadersIt.next();
            headerName = connHeader.getKey();

            if (headerName != null) {
                connValuesIt = connHeader.getValue().iterator();

                while (connValuesIt.hasNext()) {
                    String value = connValuesIt.next();

                    String[] existingVals = headers.getHeader(headerName);
                    if (existingVals == null) {
                        headers.setHeader(headerName, value);
                    }
                    else
                    {
                    	// Avoid duplicates of the same value since headers that exist in the HTTP headers
                    	// may already have been inserted in the Message object
                    	boolean exists = false;
                    	for (int i = 0; i < existingVals.length; i++)
						{
							if (value.equals(existingVals[i]))
							{
								exists = true;
							}
						}
                        if (!exists) headers.addHeader(headerName, value);
                    }
                }
            }
        }
    }

	private static class SelfSignedTrustManager implements X509TrustManager
	{

		private final X509TrustManager tm;
		private String[] trustCN = null;


		SelfSignedTrustManager(X509TrustManager tm)
		{
			this.tm = tm;
		}

		public X509Certificate[] getAcceptedIssuers()
		{
			return tm.getAcceptedIssuers();
		}

		public void checkClientTrusted(X509Certificate[] chain, String authType)
		{
			throw new UnsupportedOperationException();
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
		{
			if (chain.length == 1)
			{
				// Only ignore the check for self signed certs where CN (Canonical Name) matches
				String dn = chain[0].getIssuerDN().getName();
				for (int i = 0; i < trustCN.length; i++)
				{
					if (dn.contains("CN="+trustCN[i]))
						return;
				}
			}
		    tm.checkServerTrusted(chain, authType);
		}

		public void setTrustCN(String trustCN)
		{
			this.trustCN = trustCN.split(",");
		}

	}
}
