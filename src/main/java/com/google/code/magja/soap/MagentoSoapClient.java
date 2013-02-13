/**
 * @author ???
 * 2011-08-27 Multi-instance support - marcolopes@netc.pt
 *
 */
package com.google.code.magja.soap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.magja.magento.ResourcePath;
import com.google.common.base.Preconditions;

public class MagentoSoapClient implements SoapClient {

	public static final String CONFIG_PROPERTIES_FILE = "magento-api";

	private static final QName LOGIN_RETURN = new QName("loginReturn");

	private static final QName LOGOUT_RETURN = new QName("endSessionReturn");

	private static final QName CALL_RETURN = new QName("callReturn");

	private SoapCallFactory callFactory;

	private SoapReturnParser returnParser;

	private SoapConfig config;

	private String sessionId;

	private ServiceClient sender;

	private static final Logger log = LoggerFactory.getLogger(MagentoSoapClient.class);

	// holds all the created instances by creation order, Multiton Pattern
	private static final Map<SoapConfig, MagentoSoapClient> INSTANCES = new LinkedHashMap<SoapConfig, MagentoSoapClient>();

	/**
	 * Returns the default instance, or a newly created one from the
	 * magento-api.properties file, if there is no default instance. The default
	 * instance is the first one created.
	 * 
	 * @return the default instance or a newly created one
	 */
	public static MagentoSoapClient getInstance() {
		return (INSTANCES.size() == 0) ? getInstance(null) : INSTANCES.values().iterator().next();
	}

	/**
	 * Returns the instance that was created with the specified configuration or
	 * create one if the instance does not exist.
	 * 
	 * @return the already created instance or a new one
	 */
	public static MagentoSoapClient getInstance(final SoapConfig soapConfig) {
		// if has default instance and soapConfig is null
		if (INSTANCES.size() > 0 && soapConfig == null)
			return INSTANCES.values().iterator().next();

		synchronized (INSTANCES) {

			SoapConfig loadedSoapConfig = null;
			if (soapConfig == null) {
				InputStream configStream = MagentoSoapClient.class.getResourceAsStream("/magento-api.properties");
				if (configStream != null) {
					log.info("/magento-api.properties found in classpath, trying to load using java.util.Properties");
					Properties props = new Properties();
					try {
						props.load(configStream);
						loadedSoapConfig = new SoapConfig(props.getProperty("magento-api-username"), props.getProperty("magento-api-password"),
								props.getProperty("magento-api-url"));
					} catch (IOException e) {
						log.error("Cannot load /magento-api.properties from classpath", e);
					}
				} else {
					log.error("/magento-api.properties not found in classpath");
				}

				if (loadedSoapConfig == null) { // still null?
					throw new RuntimeException(
							"Cannot create soapConfig, make sure /magento-api.properties is in classpath, or provide your own SoapConfig instance");
				}
			} else {
				loadedSoapConfig = soapConfig;
			}

			MagentoSoapClient instance = INSTANCES.get(loadedSoapConfig);
			if (instance == null) {
				instance = new MagentoSoapClient(loadedSoapConfig);
				INSTANCES.put(loadedSoapConfig, instance);
			}
			return instance;
		}
	}

	/**
	 * Construct soap client using given configuration
	 * 
	 * @param soapConfig
	 */
	public MagentoSoapClient(SoapConfig soapConfig, SoapCallFactory callFactory) {
		super();
		Preconditions.checkNotNull(soapConfig, "soapConfig cannot be null");
		Preconditions.checkNotNull(callFactory, "callFactory cannot be null");
		this.config = soapConfig;
		this.callFactory = callFactory;
		this.returnParser = new SoapReturnParser();
		try {
			prepareConnection(config.getHttpConnectionManagerParams());
			login();
		} catch (AxisFault e) {
			// do not swallow, rethrow as runtime
			throw new RuntimeException("Cannot connect to Magento", e);
		}
	}

	/**
	 * Construct soap client using given configuration
	 * 
	 * @param soapConfig
	 */
	public MagentoSoapClient(SoapConfig soapConfig) {
		this(soapConfig, new SoapCallFactory());
	}

	@PreDestroy
	public void destroy() {
		// close the connection to magento api
		if (callFactory != null && sender != null) {
			// first, we need to logout the previous session
			if (sessionId != null) {
				OMElement logoutMethod = callFactory.createLogoutCall(sessionId);
				try {
					sender.sendReceive(logoutMethod);
				} catch (Exception e) {
					log.warn("Cannot logout Magento SOAP API from session " + sessionId, e);
				}
				sessionId = null;
			}
			try {
				sender.cleanupTransport();
				sender.cleanup();
			} catch (Exception e) {
				log.warn("Cannot cleanup Axis2 ServiceClient", e);
			}
			sender = null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#finalize()
	 */
	@Override
	protected void finalize() throws Throwable {
		destroy();
		super.finalize();
	}

	/**
	 * @return the config
	 */
	public SoapConfig getConfig() {
		return config;
	}

	/**
	 * Use to change the connection parameters to API (apiUser, apiKey,
	 * remoteHost)
	 * 
	 * @param config
	 *            the config to set
	 */
	@Deprecated
	public void setConfig(SoapConfig config) throws AxisFault {
		this.config = config;
		login();
	}

	/**
	 * Public version of call.
	 * 
	 * @see com.google.code.magja.soap.SoapClient#callArgs(com.google.code.magja.magento
	 *      .ResourcePath, Object[])
	 */
	@Override
	public <R> R callArgs(ResourcePath path, Object[] args) throws AxisFault {
		final String pathString = path.getPath();
		return call(pathString, args);
	}

	@Override
	public <T, R> R callSingle(ResourcePath path, T arg) throws AxisFault {
		if (arg != null && arg.getClass().isArray())
			log.warn("Passing array argument to callSingle {}, probably you want to call callArgs?", path);
		final String pathString = path.getPath();
		return call(pathString, new Object[] { arg });
	}

	/**
	 * Dynamic version of call.
	 * 
	 * @param pathString
	 * @param args
	 * @return
	 * @throws AxisFault
	 */
	public <R> R call(final String pathString, Object args) throws AxisFault {
		// Convert array input to List<Object>
		if (args != null && args.getClass().isArray())
			args = Arrays.asList((Object[]) args);

		log.info("Calling {} {} at {}@{} with session {}", new Object[] { pathString, args, config.getApiUser(), config.getRemoteHost(), sessionId });
		OMElement method = callFactory.createCall(sessionId, pathString, args);

		// try {
		// final StringWriter stringWriter = new StringWriter();
		// IndentingXMLStreamWriter xmlWriter = new
		// IndentingXMLStreamWriter(StaxUtilsXMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter));
		// method.serialize(xmlWriter);
		// log.debug("Calling {}: {}", pathString, stringWriter);
		// } catch (Exception e) {
		// log.warn("Cannot serialize SOAP call XML {}", method);
		// }

		OMElement result = null;
		try {
			result = sender.sendReceive(method);
		} catch (AxisFault axisFault) {
			if (axisFault.getMessage().toUpperCase().indexOf("SESSION EXPIRED") >= 0) {
				log.info("call session expired: ", axisFault);
				login();
				result = sender.sendReceive(method);
			} else {
				throw axisFault;
			}
		}

		return (R) returnParser.parse(result.getFirstChildWithName(CALL_RETURN));
	}

	/**
	 * (non-Javadoc)
	 * 
	 * @see com.google.code.magja.soap.SoapClient#multiCall(java.util.List,
	 *      java.util.List)
	 */
	@Override
	public Object multiCall(List<ResourcePath> path, List<Object> args) throws AxisFault {
		throw new UnsupportedOperationException("not implemented");
	}

	/**
	 * Connect to the service using the current config
	 */
	protected void login() throws AxisFault {

		if (isLoggedIn())
			logout();

		OMElement loginMethod = callFactory.createLoginCall(this.config.getApiUser(), this.config.getApiKey());
		log.info("====================================== Logging in user: " + this.config.getApiUser());
		OMElement loginResult = sender.sendReceive(loginMethod);

		sessionId = loginResult.getFirstChildWithName(LOGIN_RETURN).getText();
		log.info("====================================== Logged in sessionId: " + sessionId);
	}

	/**
	 * @throws AxisFault
	 */
	protected void prepareConnection(HttpConnectionManagerParams params) throws AxisFault {
		Options connectOptions = new Options();
		connectOptions.setTo(new EndpointReference(config.getRemoteHost()));
		connectOptions.setTransportInProtocol(Constants.TRANSPORT_HTTP);
		connectOptions.setTimeOutInMilliSeconds(60000);
		connectOptions.setProperty(HTTPConstants.MC_GZIP_REQUEST, true);
		connectOptions.setProperty(HTTPConstants.MC_ACCEPT_GZIP, true);

		// to use the same tcp connection for multiple calls
		// workaround:
		// http://amilachinthaka.blogspot.com/2009/05/improving-axis2-client-http-transport.html
		MultiThreadedHttpConnectionManager httpConnectionManager = new MultiThreadedHttpConnectionManager();
		httpConnectionManager.setParams(params);
		HttpClient httpClient = new HttpClient(httpConnectionManager);
		// prepare for Axis2 1.7+ / HttpClient 4.2+
		// ClientConnectionManager httpConnectionManager = new
		// PoolingClientConnectionManager();
		// HttpClient httpClient = new DefaultHttpClient(httpConnectionManager);
		connectOptions.setProperty(HTTPConstants.REUSE_HTTP_CLIENT, Constants.VALUE_TRUE);
		connectOptions.setProperty(HTTPConstants.CACHED_HTTP_CLIENT, httpClient);
		connectOptions.setProperty(HTTPConstants.HTTP_PROTOCOL_VERSION, HTTPConstants.HEADER_PROTOCOL_10);

		// Useful during HTTP debugging
		// HttpTransportProperties.ProxyProperties proxyProps = new
		// HttpTransportProperties.ProxyProperties();
		// proxyProps.setProxyName("localhost");
		// proxyProps.setProxyPort(8008);
		// proxyProps.setUserName("guest");
		// proxyProps.setPassWord("guest");
		// connectOptions.setProperty(HTTPConstants.PROXY, proxyProps);

		sender = new ServiceClient();
		sender.setOptions(connectOptions);

		// disable SOAP XML indenting for now, until I find out how to make it
		// work on OSGi ~Hendy
		// java.lang.ClassNotFoundException:
		// javanet.staxutils.StaxUtilsXMLOutputFactory not found by
		// org.apache.servicemix.bundles.stax-utils [115]
		// StAXUtils.setFactoryPerClassLoader(false);
		// sender.getServiceContext().setProperty(Constants.Configuration.MESSAGE_FORMATTER,
		// new SOAPMessageFormatter() {
		// @Override
		// public String formatSOAPAction(MessageContext msgCtxt,
		// OMOutputFormat format, String soapActionString) {
		// format.setStAXWriterConfiguration(new StAXWriterConfiguration() {
		// @Override
		// public XMLOutputFactory configure(XMLOutputFactory factory,
		// StAXDialect dialect) {
		// StaxUtilsXMLOutputFactory indentingFactory = new
		// StaxUtilsXMLOutputFactory(factory);
		// indentingFactory.setProperty(StaxUtilsXMLOutputFactory.INDENTING,
		// true);
		// return indentingFactory;
		// }
		//
		// @Override
		// public String toString() {
		// return "StaxUtils";
		// }
		// });
		// return super.formatSOAPAction(msgCtxt, format, soapActionString);
		// }
		// });
	}

	/**
	 * Logout from service, throws logout exception if failed
	 * 
	 * @throws AxisFault
	 */
	protected void logout() throws AxisFault {

		log.info("====================================== Log out sessionId: " + sessionId);
		// first, we need to logout the previous session
		try {
			OMElement logoutMethod = callFactory.createLogoutCall(sessionId);
			OMElement logoutResult = sender.sendReceive(logoutMethod);
			Boolean logoutSuccess = Boolean.parseBoolean(logoutResult.getFirstChildWithName(LOGOUT_RETURN).getText());
			if (!logoutSuccess) {
				throw new RuntimeException("Error logging out");
			}
		} catch (AxisFault axisFault) {
			if (axisFault.getMessage().toUpperCase().indexOf("SESSION EXPIRED") < 0) {
				throw axisFault;
			}
			log.info("logout session expired: ", axisFault);
		}
		log.info("====================================== Logging out user: " + this.config.getApiUser());
		sessionId = null;
	}

	/**
	 * Are we currently logged in?
	 */
	protected Boolean isLoggedIn() {
		return sessionId != null;
	}

	@Override
	public <R> R callNoArgs(ResourcePath path) throws AxisFault {
		return callSingle(path, null);
	}
}
