package org.sobotics.chatexchange.chat;

/**
 * This class represents a valid chat host on the Stack Exchange network. There are 3 chat servers, which are <code>stackoverflow.com</code>,
 * <code>stackexchange.com</code> and <code>meta.stackexchange.com</code>.
 * @author Tunaki
 */
public enum ChatHost {

	STACK_OVERFLOW("stackoverflow.com"),
	STACK_EXCHANGE("stackexchange.com"),
	META_STACK_EXCHANGE("meta.stackexchange.com");

	private final String name, baseUrl;
	
	/**
	 * Host that is used for the login
	 * 
	 * This might be a different host, because stackexchange.com does not have a login
	 */
	private final String loginHost;

	private ChatHost(String name) {
		this.name = name;
		this.baseUrl = "https://chat." + name;
		
		if (this.name.equalsIgnoreCase("stackexchange.com")) {
			//stackexchange.com doesn't have a login, so we have to use meta
			this.loginHost = "meta.stackexchange.com";
		} else {
			this.loginHost = this.name;
		}
	}

	/**
	 * @return The name of the chat server this host represents (example: <code>stackoverflow.com</code>).
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return The base URL that points to this chat server (example: <code>https://chat.stackoverflow.com</code>).
	 */
	public String getBaseUrl() {
		return baseUrl;
	}
	
	/**
	 * @return Host that is used for the login
	 */
	public String getLoginHost() {
		return this.loginHost;
    }
  
    /**
	 * Compares the host to another object
	 * @param otherHost other object
	 * @return true, if the name is the same
	 */
	public boolean equals(ChatHost otherHost) {
		if (otherHost == null)
			return false;
		
		return this.name.equals(otherHost.name);
	}

}
