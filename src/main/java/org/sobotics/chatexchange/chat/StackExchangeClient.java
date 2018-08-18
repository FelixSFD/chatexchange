package org.sobotics.chatexchange.chat;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client used to authenticate with Stack Exchange. To properly dispose of this client once created, it is expected to be
 * closed by invoking the {@link #close()} method.
 * @author Tunaki
 */
public class StackExchangeClient implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(StackExchangeClient.class);

	/**
	 * @deprecated in 1.2.0. See meta: https://meta.stackexchange.com/q/307647/347985
	 */
	@Deprecated
	private static final Pattern OPEN_ID_PROVIDER_PATTERN = Pattern.compile("(https://openid.stackexchange.com/user/.*?)\"");

	/**
	 * @deprecated in 1.2.0. See meta: https://meta.stackexchange.com/q/307647/347985
	 */
	@Deprecated
	private String openIdProvider;

	private HttpClient httpClient;
	private Map<String, String> cookies = new HashMap<>();

	/**
	 * Rooms the user is currently in
	 */
	private List<Room> rooms = new ArrayList<>();
	
	/**
	 * The user's e-mail-address
	 * This needs to be stored in order to login to a site when joining a room.
	 * With OpenID, this was not necessary because we only had to login once while initializing `StackExchangeClient`
	 * */
	private String email = null;
	
	/**
	 * The user's password
	 * This needs to be stored in order to login to a site when joining a room.
	 * With OpenID, this was not necessary because we only had to login once while initializing `StackExchangeClient`
	 * */
	private String password = null;
	
	/**
	 * true, if the account for a user should automatically be created when logging in to a new site
	 */
	private boolean autoCreateAccount = true;

	/**
	 * Constructs the client with the provided credentials. Those will be the credentials used to send messages.
	 * @param email Email of the account to connect with.
	 * @param password Password of the account to connect with.
	 */
	public StackExchangeClient(String email, String password) {
		httpClient = new HttpClient();
		this.email = email;
		this.password = password;
	}
	
	/**
	 * Logs in to s given site
	 * @param email The user's e-mail-address
	 * @param password The password
	 * @param host {@link ChatHost} of the {@link Room}
	 * @throws ChatLoginException if the login fails
	 * */
	private void seLogin(String email, String password, ChatHost host) throws IOException, ChatLoginException {
		String originalHost = host.getName();
		String loginHost = host.getLoginHost();
		
		
		//The login-form has a hidden field called "fkey" which needs to be sent along with the mail and password
		Response response = httpClient.get("https://"+loginHost+"/users/login", cookies);
		String fkey = response.parse().select("input[name='fkey']").val();
		
		response = httpClient.post("https://"+loginHost+"/users/login", cookies, "email", email, "password", password, "fkey", fkey);
		
		//Create account on that site if necessary
		Element formElement = response.parse().getElementById("logout-user");
		if (formElement != null) {
			if (!this.autoCreateAccount) {
				throw new ChatLoginException(host, "The user does not have an account on this site!");
			} // if autoCreate
			
			Elements formInputs = formElement.getElementsByTag("input");
			List<String> formData = new ArrayList<>();
			
			for (Element input : formInputs) {
				String key = input.attr("name");
				String value = input.val();
				
				if (key == null || key.isEmpty())
					continue;
				
				formData.add(key);
				formData.add(value);
			} // for formInputs
			
			String[] formDataArray = formData.toArray(new String[formData.size()]);
			
			String formUrl = "https://" + loginHost + formElement.attr("action");
			
			Response formResponse = httpClient.post(formUrl, cookies, formDataArray);
			if (formResponse.parse().getElementsByClass("js-inbox-button").first() == null) {
				LOGGER.debug(formResponse.parse().html());
				throw new ChatLoginException(host, "Unable to create account! Please create the account manually.");
			} // if
		} // if
		
		
		//Check for captcha
		Element captchaElement = response.parse().getElementById("nocaptcha-form");
		if (captchaElement != null) {
			throw new ChatLoginException(host, "Captcha found! Please wait some time before trying to login again.");
		}
		
		// check if login succeeded
		Response checkResponse = httpClient.get("https://"+originalHost+"/users/current", cookies);
		if (checkResponse.parse().getElementsByClass("js-inbox-button").first() == null) {
			LOGGER.debug(checkResponse.parse().html());
			throw new ChatLoginException(host, "Maybe your login credentials are wrong.");
		} // if
	} // seLogin

	/**
	 * The old login-flow with OpenID
	 * @deprecated in 1.2.0. See meta: https://meta.stackexchange.com/q/307647/347985
	 * */
	@Deprecated
	private void SEOpenIdLogin(String email, String password) throws IOException {
		Response response = httpClient.get("https://openid.stackexchange.com/account/login", cookies);
		String fkey = response.parse().select("input[name='fkey']").val();
		response = httpClient.post("https://openid.stackexchange.com/account/login/submit", cookies, "email", email, "password", password, "fkey", fkey);
		Document document = response.parse();
		if (document.getElementsByClass("error").size() > 0) {
			LOGGER.debug(document.html());
			throw new ChatOperationException("Invalid OpenID credentials");
		}
		Matcher matcher = OPEN_ID_PROVIDER_PATTERN.matcher(document.getElementById("delegate").html());
		if (!matcher.find()) {
			LOGGER.debug(document.html());
			throw new IllegalStateException("Cannot retrieve the OpenID provider");
		}
		openIdProvider = matcher.group(1);
	}

	/**
	 * Joins the given room for the given chat host.
	 * <p>Trying to join a room in which you are already in results in a <code>ChatOperationException</code>.
	 * @param host Host of the chat room to join.
	 * @param roomId Id of the room to join.
	 * @return <code>Room</code> joined.
	 * @throws ChatLoginException when the login failed
	 */
	public Room joinRoom(ChatHost host, int roomId) throws ChatLoginException {
		boolean alreadyLoggedIn = false;
		
		for (Room room : this.rooms) {
			if (room.getHost().equals(host)) {
				alreadyLoggedIn = true;
				break;
			} // if
		} // for rooms
		
		if (!alreadyLoggedIn) {
			//not logged in on that site yet
			try {
				this.seLogin(email, password, host);
			} catch (IOException e) {
				LOGGER.error("Login failed due to IOException!", e);
				throw new ChatLoginException(host, "IOException: " + e.getMessage());
			}
		}
		
		if (rooms.stream().anyMatch(r -> r.getHost().equals(host) && r.getRoomId() == roomId)) {
			throw new ChatOperationException("Cannot join a room you are already in.");
		}
		
		Room chatRoom = new Room(host, roomId, httpClient, cookies);
		rooms.add(chatRoom);
		return chatRoom;
	}

	/**
	 * @deprecated in 1.2.0: This is not required anymore, but maybe someone can re-implement the account creation in the new login-flow?
	 * */
	@Deprecated
	private void siteLogin(String host) throws IOException {
		Response response = httpClient.get("https://" + host + "/users/login?returnurl=" + URLEncoder.encode("https://" + host + "/", "UTF-8"), cookies);
		String fkey = response.parse().select("input[name='fkey']").val();
		response = httpClient.post("https://" + host + "/users/authenticate", cookies, "fkey", fkey, "openid_identifier", openIdProvider);
		Document document = response.parse();

		// confirmation prompt?
		if (response.url().toString().startsWith("https://openid.stackexchange.com/account/prompt")) {
			LOGGER.trace("Confirmation prompt \n" + document.html());
			String session = document.select("input[name='session']").first().val();
			fkey = document.select("input[name='fkey']").first().val();
			Response promptResponse = httpClient.post("https://openid.stackexchange.com/account/prompt/submit", cookies, "session", session, "fkey", fkey);
			document = promptResponse.parse();
			LOGGER.trace("Confirmation prompt response \n" + document.html());
		}

		// when the account doesn't exist on this site, confirm its creation
		if (!document.select("form[action='/users/openidconfirm']").isEmpty()) {
			LOGGER.debug("Account doesn't exist on target site '{}', confirming new account", host);
			String session = document.select("input[name='s']").first().val();
			fkey = document.select("input[name='fkey']").first().val();
			Response newAccountResponse = httpClient.post("https://" + host + "/users/openidconfirm", cookies, "s", session, "fkey", fkey);
			LOGGER.trace("New account confirmation response \n" + newAccountResponse.parse().html());
		}

		// check logged in
		Response checkResponse = httpClient.get("https://" + host + "/users/current", cookies);
		if (checkResponse.parse().getElementsByClass("js-inbox-button").first() == null) {
			LOGGER.debug(response.parse().html());
			throw new IllegalStateException("Unable to login to Stack Exchange.");
		}
	}

	/**
	 * true, if the account for a user should automatically be created when logging in to a new site
	 */
	public boolean getAutoCreateAccount() {
		return autoCreateAccount;
	}

	/**
	 * Controls, if the account for a user should automatically be created when logging in to a new site
	 * @param autoCreateAccount new value
	 */
	public void setAutoCreateAccount(boolean autoCreateAccount) {
		this.autoCreateAccount = autoCreateAccount;
	}
	
	
	/**
	 * Returns the cookies for the first room with the given host
	 * @param host {@link ChatHost} to search for
	 * @return null, if no room with the given {@link ChatHost} was found
	 */
	public Map<String, String> getCookies(ChatHost host) {
		for (Room room : this.rooms) {
			ChatHost roomHost = room.getHost();
			
			if (host.equals(roomHost)) {
				return room.getCookies();
			}
		}
		
		return null;
	}

	/**
	 * Closes this client by making the logged-in user leave all the chat rooms they joined.
	 * <p>Multiple invocations of this method has no further effect.
	 */
	@Override
	public void close() {
		rooms.forEach(Room::leave);
	}

}
