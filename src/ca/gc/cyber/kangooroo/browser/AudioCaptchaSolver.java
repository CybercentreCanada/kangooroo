package ca.gc.cyber.kangooroo.browser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import ca.gc.cyber.kangooroo.report.KangoorooResult;

public class AudioCaptchaSolver {
    private static final Logger log = LoggerFactory.getLogger(AudioCaptchaSolver.class);


    private static final int BASE_MILISECONDS_DEPLAY = 1500;

    private static final int MAX_CAPTCHA_RETRY = 5;

    private static final int MAX_RANDOM_DELAY = 2000;

    private String firstCaptchaFrame;

    private String testCaptchaFrame;

    private RemoteWebDriver driver;


    public AudioCaptchaSolver(RemoteWebDriver driver) {
        this.driver = driver;
    }

    public KangoorooResult.CaptchaResult removeCaptcha(File tempFolder) throws InterruptedException {

        addRandomDelay(TimeUnit.SECONDS.toMillis(3));

        if (isHCaptcha()) {
            log.info("hCaptcha detected. We cannot solve hcaptcha. Return for now.");
            return KangoorooResult.CaptchaResult.IS_HCAPTCHA;
        }

        firstCaptchaFrame = getFirstCaptchaFrame();
        if (firstCaptchaFrame == null) {
            log.info("Cannot find the actual captcha frame. No captcha to solve.");
            return KangoorooResult.CaptchaResult.NO_CAPTCHA;
        }

        log.debug("Switching to first captcha frame.");
        try {
            driver.switchTo().frame(firstCaptchaFrame);

        } catch (Exception e) {
            log.debug("Can't switch frame but we should try anyways...");
        }

        // after clicking the not robot checkbox, there are two possibilities:
        // 1. The checkbox is checked, meaning that we already solved the captcha
        // 2. The new testCaptchaFrame shows up
        // note I think we can see the html elements all the time.. =_=

        clickNotRobotCheckbox();

        boolean notRobot = isNotRobotChecked();
        if (notRobot) {
            log.debug("The \"not a robot\" check passed. Try to click on the submit button.");
            clickOnSubmitButton();
            return KangoorooResult.CaptchaResult.CAPTCHA_SOLVED;
        }

        log.info("Need to solve captcha challenge.");

        try {
            log.debug("Switch to parent frame.");
            driver.switchTo().parentFrame();
            driver.switchTo().defaultContent();
        } catch (Exception e) {
            log.debug("Can't switch frame.");
        }

        this.testCaptchaFrame = getTestCaptchaFrame();

        if (this.testCaptchaFrame == null) {
            log.info("No test captcha frame detected. No captcha to solve.");
            return KangoorooResult.CaptchaResult.NO_CAPTCHA;
        }

        // now we are at scenario 2. We can try to do this audio challenge problem now.
        log.debug("Switch to test captcha frame: " + this.testCaptchaFrame);
        driver.switchTo().frame(this.testCaptchaFrame);


        try {
            log.debug("Looking for the audio captcha button...");
            (new WebDriverWait(driver, 5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.id("recaptcha-audio-button")));
            driver.findElementById("recaptcha-audio-button").click();

            log.debug("Change captcha to the audio recaptcha challenge.");

        } catch (NoSuchElementException | TimeoutException | ElementNotInteractableException e) {
            log.warn("Cannot click on the audio challenge button. The captcha probably doesn't exist.");
            return KangoorooResult.CaptchaResult.CAPTCHA_FAILED;
        }

        // now we have a captcha test frame... we may need to do multiple tries to pass the captcha
        int numTries = 0;

        while (numTries < MAX_CAPTCHA_RETRY) {
            numTries += 1;
            String solution = null;

            try {
                solution = getCaptchaSolution(tempFolder);
            } catch (UnsupportedAudioFileException | IOException e) {
                log.error("Issue with audio file from audio captcha challenge.");
                throw new RuntimeException(e);
            }


            if (solution != null) {
                addRandomDelay();
                log.info("Solution to audio captcha is " + solution);
                driver.findElementById("audio-response").sendKeys(solution);
                addRandomDelay();
                // click on the verify button
                log.debug("Click on verify button.");
                driver.findElementById("recaptcha-verify-button").click();
            } else if (isCaptchaBlocked()) {
                log.warn("Blocked by google. Cannot solve captcha.");
                return KangoorooResult.CaptchaResult.CAPTCHA_BLOCKED;
            } else {
                log.error("Why is the captcha blocked text not here???");
                return KangoorooResult.CaptchaResult.CAPTCHA_FAILED;
            }


            // If we don't see any audio challenge error...
            if (!isAudioChallengeErrorPresent()) {
                // we go back to button clicking of the captcha hopefully

                // see if our audio captcha frames are still there
                // first let's go to parent frames though
                log.debug("We are switching to the parent frame to check for captcha frames.");
                try {
                    driver.switchTo().parentFrame();
                    driver.switchTo().defaultContent();
                } catch (Exception e) {
                    log.debug("Can't switch frame.");
                }

                String newFirstCaptchaFrame = getFirstCaptchaFrame();

                if (newFirstCaptchaFrame == null) {
                    log.warn("The captcha challenge frame is gone!?");
                    break;

                }

                firstCaptchaFrame = newFirstCaptchaFrame;

                // sometimes we need to do this part. Some times it just skips to the website directly
                // might need to do a reclicking and checking of whether or not I am a robot...
                log.debug("Switch to first captcha frame: " + newFirstCaptchaFrame);
                try {
                    driver.switchTo().frame(firstCaptchaFrame);
                } catch (Exception e) {
                    log.debug("Can't switch frame.");
                }

                // sometimes we need to check this, sometimes we don't
                if (isNotRobotChecked()) {
                    addRandomDelay();

                    log.debug("We are switching to the parent frame to find the proceed button...");
                    try {
                        driver.switchTo().parentFrame();
                        driver.switchTo().defaultContent();
                    } catch (Exception e) {
                        log.info("Can't switch frame. May not be a problem.");
                    }
                    this.clickOnSubmitButton();

                } else {
                    log.info("The check mark may not be there depending on the machine.");
                }
                // also stop trying to solve captchas at this point
                break;

            } else {
                // if there is an error message from audio captcha,
                // it is most likely that we gave the wrong solution. Just try again.
                log.info("Audio captcha not solved, an error message is present. Try to solve captcha again.");
            }
        }

        log.info("Done solving audio captcha.");
        return KangoorooResult.CaptchaResult.CAPTCHA_SOLVED;

    }

    private boolean isAudioChallengeErrorPresent() {
        addRandomDelay();

        try {
            log.info("look for element: " + "rc-audiochallenge-error-message");

            WebElement element = driver.findElementByClassName("rc-audiochallenge-error-message");

            // the error message gets display set to none if you solved it the second time.
            if (element.isDisplayed()) {
                return true;
            }


        } catch (Exception e) {
            log.info("Cannot find the error audio challenge message for whatever reason you know....");
        }

        return false;

    }

    private String getCaptchaSolution(File tempFolder) throws UnsupportedAudioFileException, IOException {
        try {
            log.info("Refresh audio captcha first.");
            (new WebDriverWait(driver, 5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.id("recaptcha-reload-button")));
            addRandomDelay();
            driver.findElement(By.id("recaptcha-reload-button")).click();

        } catch (NoSuchElementException | TimeoutException | ElementClickInterceptedException e) {
            log.info("Cannot find the recaptcha reload button..");
        }

        log.info("Continue after the reload button....");

        try {
            log.info("Click on the step instruction");
            (new WebDriverWait(driver, 5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//button[@aria-labelledby='audio-instructions rc-response-label']")));
            addRandomDelay();
            driver.findElement(By
                            .xpath("//button[@aria-labelledby='audio-instructions rc-response-label']"))
                    .click();
        } catch (NoSuchElementException | TimeoutException e) {
            log.warn("Cannot find the audio captcha buttons.");
            return null;
        }

        log.info("Look for the audio download link...");
        String audioHref = null;
        try {
            (new WebDriverWait(driver, 5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//a[@class='rc-audiochallenge-tdownload-link']")));
            audioHref = driver.findElement(By.xpath("//a[@class='rc-audiochallenge-tdownload-link']"))
                    .getAttribute("href");
        } catch (NoSuchElementException | TimeoutException e) {
            log.info("Cannot find the download button. Try downloading from source.");
        }

        if (audioHref == null) {
            try {
                audioHref = driver.findElement(By.id("audio-source"))
                        .getAttribute("src");
            } catch (NoSuchElementException | TimeoutException e) {
                log.info("I can't find the download link from source either!!");
            }
        }

        if (audioHref == null) {
            log.info("Cannot find audio challenge download link!");
            return null;
        }

        log.info("Download captcha audio file from URL: " + audioHref);
        File audioMp3File = new File(tempFolder, "audio-important.mp3");

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpGet request = new HttpGet(audioHref);
            CloseableHttpResponse response = client.execute(request);

            log.debug("Audio download status code: " + response.getStatusLine().getStatusCode());
            FileUtils.copyInputStreamToFile(response.getEntity().getContent(), audioMp3File);
            log.debug("Audio challenge file downloaded successfully.");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String solution = getSpeechToText(audioMp3File, tempFolder);
        log.info("SOLUTION TO CAPTCHA IS: " + solution);

        return solution;
    }

    private boolean clickOnSubmitButton() {
        WebElement proceedButton = null;

        try {
            proceedButton = driver.findElement(By.className("btn-dark"));
        } catch (Exception e) {
            log.warn("I can't find the proceed button for solving audio challenge.");
            return false;
        }

        addRandomDelay(TimeUnit.SECONDS.toMillis(2));

        return true;

    }

    private boolean isCaptchaBlocked() {
        WebElement blockCaptchaElement = null;
        String blockCaptchaText = "";
        addRandomDelay();
        try {
            blockCaptchaElement = driver.findElementByClassName("rc-doscaptcha-body-text");
            blockCaptchaText = blockCaptchaElement.getText();
        } catch (Exception e) {
            log.info("Cannot find element with class name: " + "rc-doscaptcha-body-text");
            return false;
        }

        return blockCaptchaText.contains("Your computer or network may be sending automated queries.");
    }


    private boolean isHCaptcha() {
        WebElement hCaptcha = null;
        addRandomDelay();

        try {
            hCaptcha = driver.findElement(By.xpath("//iframe[contains(@src,'https://newassets.hcaptcha.com/captcha/v1')]"));
            return true;
        } catch (Exception e) {
        }

        return false;

    }


    private String getTestCaptchaFrame() {

        try {
            String captchaTestFrame = driver.findElement(By.xpath("//iframe[contains(@src,'https://www.google.com/recaptcha/api2/bframe?')]"))
                    .getAttribute("name");

            return captchaTestFrame;
        } catch (Exception e) {
            log.info("Cannot find the captcha test frame.");
        }

        return null;

    }

    private String getFirstCaptchaFrame() {
        // find the captcha
        try {
            List<WebElement> captFrameElement = driver.findElements(By.xpath("//iframe[@title='reCAPTCHA']"));
            // no captcha... business as usual!
            if (captFrameElement.size() == 0) {
                log.info("Cannot find captcha frame continue to take screenshot of website.");
                return null;
            }
            String name = captFrameElement.get(0).getAttribute("name");

            return name;
        } catch (Exception e) {
            log.info("Cannot find reCAPTCHA frame.");
        }

        return null;
    }

    // this friggin checkbox disappears if we click on the submit button early so be careful
    private boolean clickNotRobotCheckbox() {

        addRandomDelay();

        try {
            log.debug("Click on the I am not a robot checkbox.");
            driver.findElementByClassName("recaptcha-checkbox-border").click();
            addRandomDelay();
            return true;
        } catch (Exception e) {
            log.info("Cannot click on the I am not a robot checkbox.");
        }
        return false;
    }

    private boolean isNotRobotChecked() {
        String attr = "";
        addRandomDelay(TimeUnit.SECONDS.toMillis(2));

        try {
            log.debug("Check if we passed the I am not a robot test.");
            (new WebDriverWait(driver, 5))
                    .until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//span[@id='recaptcha-anchor']")));

            driver.findElement(By.xpath("//span[@id='recaptcha-anchor']"));
        } catch (Exception e) {
            log.warn("Did not get the value of the checkbox.");
            return false;
        }
        attr = driver.findElement(By.xpath("//span[@id='recaptcha-anchor']"))
                .getAttribute("aria-checked");

        log.debug("Attribute of the recaptcha-anchor is " + attr);

        return attr.equals("true");

    }

    private static void addRandomDelay() {
        addRandomDelay(BASE_MILISECONDS_DEPLAY);
    }

    private static void addRandomDelay(long baseMiliDelay) {
        try {
            Thread.sleep(baseMiliDelay + (Math.round(Math.random() * MAX_RANDOM_DELAY)));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getSpeechToText(File audioFile, File tempFolder) {

        String audioPath = audioFile.getAbsolutePath();

        log.debug("Translate audio to text from file: " + audioPath);

        try {
            var result = runProcess( "whisper", audioFile.getAbsolutePath(),
                    "--device", "cpu", "--fp16", "False", "--model", "small.en", "--output_dir", tempFolder.getAbsolutePath(),
                    "--output_format", "txt");

            return result.replaceAll("\\[.*\\]", "").trim();
        } catch (IOException | InterruptedException e) {

            throw new RuntimeException(e);
        }

    }

    public static String runProcess(String... command) throws IOException, InterruptedException {
        var processBuilder = new ProcessBuilder(command);
        var process = processBuilder.start();

        process.waitFor();
        String result = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines().collect(Collectors.joining("\n"));
        process.destroy();

        log.debug("Output from process: " + result);

        return result;

    }

    public static void main(String[] args) throws IOException, InterruptedException {
        File audioFile = new File("audio.mp3");
        var res = getSpeechToText(audioFile, new File("./"));

        log.info("result: " + res);
    }
}
