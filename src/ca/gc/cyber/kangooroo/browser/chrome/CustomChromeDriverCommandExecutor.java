package ca.gc.cyber.kangooroo.browser.chrome;

import org.openqa.selenium.remote.CommandInfo;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.service.DriverCommandExecutor;
import org.openqa.selenium.remote.service.DriverService;

import com.google.common.collect.ImmutableMap;

/**
 * Taken from https://stackoverflow.com/questions/45199076/take-full-page-screen-shot-in-chrome-with-selenium
 */
public class CustomChromeDriverCommandExecutor extends DriverCommandExecutor {

    private static final ImmutableMap<String, CommandInfo> CHROME_COMMAND_NAME_TO_URL =
            ImmutableMap.of("launchApp",
                    new CommandInfo("/session/:sessionId/chromium/launch_app", HttpMethod.POST),
                    "sendCommandWithResult",
                    new CommandInfo("/session/:sessionId/chromium/send_command_and_get_result", HttpMethod.POST));

    public CustomChromeDriverCommandExecutor(DriverService service) {
        super(service, CHROME_COMMAND_NAME_TO_URL);
    }
}