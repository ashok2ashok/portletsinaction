package chapter03.code.listing.base;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.MimeResponse;
import javax.portlet.PortalContext;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletModeException;
import javax.portlet.PortletRequest;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.PortletSession;
import javax.portlet.PortletURL;
import javax.portlet.ProcessAction;
import javax.portlet.RenderMode;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.WindowState;
import javax.portlet.WindowStateException;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.portlet.PortletFileUpload;
import org.apache.log4j.Logger;
import org.w3c.dom.Element;

import chapter03.code.listing.utils.BookDataObject;
import chapter03.code.listing.utils.Constants;

/**
 * BookCatalogPortlet class delegates most of the request to BookServlet (refer
 * web.xml). Some of the requests like uploadToc are handled by the portlet
 * class itself.
 * 
 * @author asarin
 * 
 */
public class BookCatalogPortlet extends GenericPortlet {
	private static final long MAX_UPLOAD_FILE_SIZE = 1024 * 1024;
	private Logger logger = Logger.getLogger(BookCatalogPortlet.class);

	/*
	 * doHeaders method is responsible for adding bookCatalog.css (CSS file) and
	 * to the <head> section of the HTML markup
	 * generated by the portal page. This is an optional feature and may not be
	 * available in all portal servers. For example, JetSpeed allows you to add
	 * elements, and also Liferay with OpenPortal Portlet Container. Glassfish
	 * v2.1 ignores the MARKUP_HEAD_ELEMENT header.
	 * 
	 * @see javax.portlet.GenericPortlet#doHeaders(javax.portlet.RenderRequest,
	 * javax.portlet.RenderResponse)
	 */
	protected void doHeaders(RenderRequest request, RenderResponse response) {
		super.doHeaders(request, response);
		PortalContext portalContext = request.getPortalContext();
		String portalInfo = portalContext.getPortalInfo();

		// -- adding DOM element to head is supported by JetSpeed 2.2
		//--if you are using Liferay with OpenPortal Portlet Container then
		//-- you can use MARKUP_HEAD_ELEMENT to add your JS / CSS
		//-- files to the head section of the HTML markup generated by portal
		if (portalContext.getProperty(PortalContext.MARKUP_HEAD_ELEMENT_SUPPORT) != null 
				|| portalInfo.contains(Constants.JETSPEED)) {
			// -- add CSS
			Element cssElement = response.createElement("link");
			// --encoding URLs is important
			cssElement.setAttribute("href", response.encodeURL((request
					.getContextPath() + "/css/bookCatalog.css")));
			cssElement.setAttribute("rel", "stylesheet");
			cssElement.setAttribute("type", "text/css");
			response.addProperty(MimeResponse.MARKUP_HEAD_ELEMENT, cssElement);

			// -- add JavaScript
			Element jsElement = response.createElement("script");

			// --encoding URLs to resources is important
			jsElement.setAttribute("src", response.encodeURL((request
					.getContextPath() + "/js/bookCatalog.js")));
			jsElement.setAttribute("type", "text/javascript");
			response.addProperty(MimeResponse.MARKUP_HEAD_ELEMENT, jsElement);
		}
	}

	/**
	 * Render method that is invoked when the portlet mode is 'print'. If the
	 * portal server doesn't support this mode then there is simply ignored. For
	 * example, JetSpeed 2.2 and Liferay 5.2.3 support 'print' custom mode.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws PortletException
	 */
	@RenderMode(name = "print")
	public void showPrint(RenderRequest request, RenderResponse response)
			throws IOException, PortletException {
		request.setAttribute("myaction", "print");
		logger.info("Generating printable version of catalog");
		showPrintableCatalog(request, response);
	}

	/**
	 * Render method for HELP portlet mode. In this mode portlet shows help
	 * information to the user.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws PortletException
	 */
	@RenderMode(name = "help")
	public void showHelp(RenderRequest request, RenderResponse response)
			throws IOException, PortletException {
		logger.info("Generating Help information for the portlet");
		showHelpInfo(request, response);
	}

	/**
	 * Render method for the EDIT portlet mode. In this mode the portlet allows
	 * users to view and specify their preferences. The Book catalog portlet
	 * in-turn personalizes portlet content / behavior based on the preferences
	 * selected/entered by the user.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws PortletException
	 */
	@RenderMode(name = "edit")
	public void showPrefs(RenderRequest request, RenderResponse response)
			throws IOException, PortletException {
		logger.info("Generating Preferences details for the portlet");
		showPrefsInfo(request, response);
	}

	private boolean isMarkupValid(RenderRequest request, RenderResponse response) {
		boolean isMarkupValid = false;
		BookDataObject catalog = (BookDataObject) getPortletContext()
		.getAttribute("bookCatalog");
		int currentCountInDatastore = catalog.getBooks().size();
		logger.info("Current book count in catalog : "
				+ currentCountInDatastore);
		
		String earlierCount = response.getCacheControl().getETag();
		logger.info(earlierCount);
		
		logger.info("Book count from validation token : "
				+ earlierCount);
		
		// -- in case of mismatch between currentCountInDatastore and
		// earlierCountInValidationToken
		// -- continue with the method invocation or instruct the container use
		// the cached content
		
		if (String.valueOf(currentCountInDatastore).equals(earlierCount)) {
			isMarkupValid = true;
		}
		return isMarkupValid;
	}
	
	/**
	 * Render method for the VIEW portlet mode. This is where all the main
	 * business functionality of the portlet lies.
	 * 
	 * @param request
	 * @param response
	 * @throws IOException
	 * @throws PortletException
	 */
	@SuppressWarnings("unchecked")
	@RenderMode(name = "VIEW")
	public void showBooks(RenderRequest request, RenderResponse response)
			throws IOException, PortletException {
		logger.info("Entering showBooks method");

		//--return if the content is still valid
		if(isMarkupValid(request, response)) {
			response.getCacheControl().setUseCachedContent(true);
			response.getCacheControl().setExpirationTime(100);
			return;
		} else {
			BookDataObject catalog = (BookDataObject) getPortletContext()
			.getAttribute("bookCatalog");
			int currentCountInDatastore = catalog.getBooks().size();
			// -- set the currentCountInDatabase as the etag value
			response.getCacheControl().setETag("" + currentCountInDatastore);
		}
		
		PortalContext context = request.getPortalContext();
		printSupportedPortletModes(context);
		printSupportedWindowStates(context);
		// --get user attributes user.name.given and user.name.family
		Map<String, Object> userAttributeMap = (Map<String, Object>) request
				.getAttribute(PortletRequest.USER_INFO);
		String firstName = "";
		String lastName = "";
		if (userAttributeMap != null) {
			firstName = (String) userAttributeMap.get("user.name.given");
			lastName = (String) userAttributeMap.get("user.name.family");
			request.setAttribute("firstName", firstName);
			request.setAttribute("lastName", lastName);
		}

		String portalInfo = context.getPortalInfo();
		request.setAttribute("portalInfo", portalInfo);

		// --generate all the URLs that will be used by the portlet
		generateUrls(request, response);

		String myaction = request.getParameter("myaction");
		if (myaction != null) {
			logger.info("myaction parameter is not null. Value is " + myaction);
			request.getPortletSession().setAttribute("myaction", myaction,
					PortletSession.PORTLET_SCOPE);
		} else {
			// -- if myaction is NULL then show the home page of Book
			// catalog
			// page
			request.getPortletSession().setAttribute("myaction", "showCatalog",
					PortletSession.PORTLET_SCOPE);
		}

		// -- send myaction as a request attribute to the BookServlet.
		request.setAttribute("myaction", request.getPortletSession()
				.getAttribute("myaction"));

		// --dynamically obtain the title for the portlet, based on myaction
		String titleKey = "portlet.title."
				+ (String) request.getPortletSession().getAttribute("myaction");
		response.setTitle(getResourceBundle(request.getLocale()).getString(
				titleKey));

		// --if the action is uploadTocForm then store the ISBN number of
		// the
		// --book for which the TOC is being uploaded. The upload action
		// will use the ISBN number to create file name -- refer home.jsp
		// page
		if (((String) request.getAttribute("myaction"))
				.equalsIgnoreCase("uploadTocForm")) {
			request.getPortletSession().setAttribute("isbnNumber",
					request.getParameter("isbnNumber"));
		}

		if (((String) request.getPortletSession().getAttribute("myaction"))
				.equalsIgnoreCase("showSearchResults")) {
			request.setAttribute("matchingBooks", request.getPortletSession()
					.getAttribute("matchingBooks"));
		}

		// its important to encode URLs
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/myservlet/bookServlet"));
		dispatcher.include(request, response);
	}

	/**
	 * Removes a book from the catalog.
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@ProcessAction(name = "removeBookAction")
	public void removeBook(ActionRequest request, ActionResponse response)
			throws PortletException, IOException {
		logger.info("Inside removeBook action method");
		request.setAttribute("myaction", "removeBookAction");
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/myservlet/bookServlet"));
		dispatcher.include(request, response);
		response.setRenderParameter("myaction", "showCatalog");
	}
	
	/**
	 * Uploads book's TOC. The TOC is uploaded to the folder identified
	 * by uploadFolder portlet initialization parameter.
	 * Method makes use of Commons FileUpload library to upload files.
	 * Make sure that you are using Commons FileUpload 1.1 or later.
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@ProcessAction(name = "uploadTocAction")
	public void uploadToc(ActionRequest request, ActionResponse response)
			throws PortletException, IOException {
		logger.info("Inside uploadToc action method");
		PortletFileUpload pfu = new PortletFileUpload();
		pfu.setFileSizeMax(MAX_UPLOAD_FILE_SIZE);
		String fileExtension = null;
		FileOutputStream outStream = null;
		try {
			FileItemIterator iter = pfu.getItemIterator(request);
			while (iter.hasNext()) {
				FileItemStream item = iter.next();
				String fileName = item.getName();
				fileExtension = fileName.substring(fileName.lastIndexOf("."),
						fileName.length());

				outStream = new FileOutputStream(
						getInitParameter("uploadFolder")
								+ "\\"
								+ request.getPortletSession().getAttribute(
										"isbnNumber") + fileExtension);
				InputStream stream = item.openStream();
				if (!item.isFormField()) {
					byte[] buffer = new byte[1024];
					while (true) {
						int bytes = stream.read(buffer);
						if (bytes <= 0) {
							break;
						}
						outStream.write(buffer, 0, bytes);
					}
					outStream.flush();
				}
			}
			response.setRenderParameter("myaction", "showCatalog");
		} catch (Exception ex) {
			// --close the output stream and delete the generated file
			if(outStream != null) {
				outStream.close();
			}
			File file = new File(getInitParameter("uploadFolder") + "\\"
					+ request.getPortletSession().getAttribute("isbnNumber")
					+ fileExtension);
			if (file != null && file.canRead() && file.canWrite()) {
				file.delete();
			}
			response.setRenderParameter("myaction", "error");
			response
					.setRenderParameter(
							"exceptionMsg",
							"Exception occurred while uploading the file. Please check the file size is <= 1MB");
		}
		finally {
			// --close the output stream
			if (outStream != null) {
				outStream.close();
			}
		}
	}
	
	/**
	 * Resets the search results. When search is made, the matching results are shown.
	 * This method is invoked when the user clicks the 'Reset' hyperlink on the search
	 * results page.
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@ProcessAction(name = "resetAction")
	public void resetAction(ActionRequest request, ActionResponse response)
			throws PortletException, IOException {
		logger.info("Inside reset action");
		response.setRenderParameter("myaction", "showCatalog");
	}

	/**
	 * Searches for a matching book. 
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@ProcessAction(name = "searchBookAction")
	public void searchBook(ActionRequest request, ActionResponse response)
			throws PortletException, IOException {
		logger.info("Inside search Book action");
		request.setAttribute("myaction", "searchBookAction");
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/myservlet/bookServlet"));
		dispatcher.include(request, response);
		response.setRenderParameter("myaction", "showSearchResults");

		// --store the search criteria in session
		request.getPortletSession().setAttribute("authorNameSearchField",
				request.getParameter("authorNameSearchField"),
				PortletSession.APPLICATION_SCOPE);
		request.getPortletSession().setAttribute("bookNameSearchField",
				request.getParameter("bookNameSearchField"),
				PortletSession.APPLICATION_SCOPE);
		// retrieving the matchingBooks request attribute set by BookServlet and
		// store it in session
		request.getPortletSession().setAttribute("matchingBooks",
				request.getAttribute("matchingBooks"));
	}
	

	/**
	 * Adds a book to the catalog.
	 * 
	 * @param request
	 * @param response
	 * @throws PortletException
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	@ProcessAction(name = "addBookAction")
	public void addBook(ActionRequest request, ActionResponse response)
			throws PortletException, IOException {
		logger.info("addBook action invoked");
		request.setAttribute("myaction", "addBookAction");
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/myservlet/bookServlet"));
		dispatcher.include(request, response);
		Map<String, String> map = (Map<String, String>) request.getAttribute("errors");
		if (map != null && !map.isEmpty()) {
			response.setRenderParameter("myaction", "addBookAction");
		} else {
			response.setRenderParameter("myaction", "showCatalog");
		}
	}

	/*
	 * Generates URLs that will be used by the portlet.
	 */
	private void generateUrls(RenderRequest request, RenderResponse response)
			throws PortletModeException, WindowStateException {
		// Render URL for Print hyperlink
		PortletURL printModeUrl = response.createRenderURL();
		if (request.isPortletModeAllowed(new PortletMode("print"))) {
			printModeUrl.setPortletMode(new PortletMode("print"));
		}
		if (request.isWindowStateAllowed(new WindowState("pop_up"))) {
			printModeUrl.setWindowState(new WindowState("pop_up"));
		}
		request.setAttribute("printModeUrl", printModeUrl);

		// Action URL for upload Toc action
		PortletURL uploadTocActionUrl = response.createActionURL();
		uploadTocActionUrl.setParameter("myaction", "uploadTocAction");
		uploadTocActionUrl.setParameter(ActionRequest.ACTION_NAME,
				"uploadTocAction");
		request.setAttribute("uploadTocActionUrl", uploadTocActionUrl);

		// Render URL for Full Screen hyperlink
		PortletURL fullScreenUrl = response.createRenderURL();
		fullScreenUrl.setWindowState(WindowState.MAXIMIZED);
		request.setAttribute("fullScreenUrl", fullScreenUrl);

		// Render URL for Help hyperlink
		PortletURL helpUrl = response.createRenderURL();
		helpUrl.setPortletMode(PortletMode.HELP);
		request.setAttribute("helpUrl", helpUrl);

		// Render URL for Home hyperlink
		PortletURL homeUrl = response.createRenderURL();
		homeUrl.setPortletMode(PortletMode.VIEW);
		request.setAttribute("homeUrl", homeUrl);

		// Render URL for Preferences hyperlink
		PortletURL prefUrl = response.createRenderURL();
		prefUrl.setPortletMode(PortletMode.EDIT);
		request.setAttribute("prefUrl", prefUrl);

		// Render URL for form submission for Adding book
		PortletURL addBookFormUrl = response.createRenderURL();
		addBookFormUrl.setParameter("myaction", "addBookForm");
		request.setAttribute("addBookFormUrl", addBookFormUrl);

		// Action URL for Add Book Action
		PortletURL addBookActionUrl = response.createActionURL();
		addBookActionUrl.setParameter(ActionRequest.ACTION_NAME,
				"addBookAction");
		request.setAttribute("addBookActionUrl", addBookActionUrl);

		// Action URL for resetting search
		PortletURL resetActionUrl = response.createActionURL();
		resetActionUrl.setParameter(ActionRequest.ACTION_NAME, "resetAction");
		request.setAttribute("resetActionUrl", resetActionUrl);

		// Action URL for searching books
		PortletURL searchBookActionUrl = response.createActionURL();
		searchBookActionUrl.setParameter(ActionRequest.ACTION_NAME,
				"searchBookAction");
		request.setAttribute("searchBookActionUrl", searchBookActionUrl);

		// Render URL for Refresh Search Results link
		PortletURL refreshResultsUrl = response.createRenderURL();
		refreshResultsUrl.setParameter("myaction", "refreshResults");
		request.setAttribute("refreshResultsUrl", refreshResultsUrl);
	}

	private void printSupportedPortletModes(PortalContext context) {
		// -- supported portlet modes by the portal server
		Enumeration<PortletMode> portletModes = context
				.getSupportedPortletModes();
		while (portletModes.hasMoreElements()) {
			PortletMode mode = portletModes.nextElement();
			logger.info("Support portlet mode " + mode.toString());
		}
	}

	/*
	 * Prints window states supported by the portal server
	 */
	private void printSupportedWindowStates(PortalContext context) {
		// -- supported window states by the portal server
		Enumeration<WindowState> windowStates = context
				.getSupportedWindowStates();
		while (windowStates.hasMoreElements()) {
			WindowState windowState = windowStates.nextElement();
			logger.info("Support window state " + windowState.toString());
		}
	}

	/*
	 * Shows help information.
	 */
	private void showHelpInfo(RenderRequest request, RenderResponse response)
			throws PortletException, IOException {
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/WEB-INF/jsp/help.jsp"));
		dispatcher.include(request, response);
	}

	/*
	 * Show portlet preferences options available to the user
	 */
	private void showPrefsInfo(RenderRequest request, RenderResponse response)
			throws PortletException, IOException {
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/WEB-INF/jsp/preferences.jsp"));
		dispatcher.include(request, response);
	}

	/*
	 * Shows porlet in a printable version with no HTML form elements
	 */
	private void showPrintableCatalog(RenderRequest request,
			RenderResponse response) throws PortletException, IOException {
		PortletRequestDispatcher dispatcher = request.getPortletSession()
				.getPortletContext().getRequestDispatcher(
						response.encodeURL("/myservlet/bookServlet"));
		dispatcher.include(request, response);
	}
}
