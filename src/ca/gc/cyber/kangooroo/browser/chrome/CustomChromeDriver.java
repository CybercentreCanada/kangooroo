package ca.gc.cyber.kangooroo.browser.chrome;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.html5.LocalStorage;
import org.openqa.selenium.html5.Location;
import org.openqa.selenium.html5.LocationContext;
import org.openqa.selenium.html5.SessionStorage;
import org.openqa.selenium.html5.WebStorage;
import org.openqa.selenium.interactions.HasTouchScreen;
import org.openqa.selenium.interactions.TouchScreen;
import org.openqa.selenium.mobile.NetworkConnection;
import org.openqa.selenium.remote.FileDetector;
import org.openqa.selenium.remote.RemoteTouchScreen;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.html5.RemoteLocationContext;
import org.openqa.selenium.remote.html5.RemoteWebStorage;
import org.openqa.selenium.remote.mobile.RemoteNetworkConnection;

import com.google.common.collect.ImmutableMap;

/**
 * Taken from https://stackoverflow.com/questions/45199076/take-full-page-screen-shot-in-chrome-with-selenium
 */

public class CustomChromeDriver extends RemoteWebDriver implements LocationContext, WebStorage, HasTouchScreen, NetworkConnection {
    private RemoteLocationContext locationContext;
    private RemoteWebStorage webStorage;
    private TouchScreen touchScreen;
    private RemoteNetworkConnection networkConnection;
    private ChromeDriverService service;


    public CustomChromeDriver(Capabilities capabilities) {
        this(ChromeDriverService.createDefaultService(), capabilities);
    }


    public CustomChromeDriver(ChromeDriverService service, Capabilities capabilities) {
        super(new CustomChromeDriverCommandExecutor(service), capabilities);
        this.locationContext = new RemoteLocationContext(getExecuteMethod());
        this.webStorage = new RemoteWebStorage(getExecuteMethod());
        this.touchScreen = new RemoteTouchScreen(getExecuteMethod());
        this.networkConnection = new RemoteNetworkConnection(getExecuteMethod());
        this.service = service;
    }


    public ChromeDriverService getService() {
        return this.service;
    }


    @Override
    public void setFileDetector(FileDetector detector) {
        throw new WebDriverException("Setting the file detector only works on remote webdriver instances obtained via RemoteWebDriver");
    }


    @Override
    public LocalStorage getLocalStorage() {
        return this.webStorage.getLocalStorage();
    }


    @Override
    public SessionStorage getSessionStorage() {
        return this.webStorage.getSessionStorage();
    }


    @Override
    public Location location() {
        return this.locationContext.location();
    }


    @Override
    public void setLocation(Location location) {
        this.locationContext.setLocation(location);
    }


    @Override
    public TouchScreen getTouch() {
        return this.touchScreen;
    }


    @Override
    public ConnectionType getNetworkConnection() {
        return this.networkConnection.getNetworkConnection();
    }


    @Override
    public ConnectionType setNetworkConnection(ConnectionType type) {
        return this.networkConnection.setNetworkConnection(type);
    }


    public void launchApp(String id) {
        this.execute("launchApp", ImmutableMap.of("id", id));
    }
}
