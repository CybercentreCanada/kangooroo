package ca.gc.cyber.kangooroo.report;

import ca.gc.cyber.kangooroo.utils.io.net.http.HarUtils;

import java.net.URL;

import org.apache.commons.lang3.tuple.Pair;

import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;

import lombok.Getter;

@Getter
public final class KangoorooResult {

    private final Pair<Har, URL> urlHar;

    private CaptchaResult captchaResult = CaptchaResult.NONE;

    public enum CaptchaResult {
        CAPTCHA_SOLVED, CAPTCHA_BLOCKED, CAPTCHA_FAILED, NO_CAPTCHA, IS_HCAPTCHA, NONE
    }

    public KangoorooResult(Pair<Har, URL> har) {
        this.urlHar = har;
    }

    public KangoorooResult(Pair<Har, URL> har, CaptchaResult captchaResult) {
        this(har);
        this.captchaResult = captchaResult;
    }

    public boolean isConnectionSuccess() {
        return HarUtils.isConnectionSuccess(getHar());
    }


    public HarEntry getInitial() {
        return HarUtils.getFirstEntry(getHar());
    }


    public HarEntry getFirstNotRedirected() {
        return HarUtils.getFirstEntryNotRedirected(getHar());
    }


    public HarEntry getFirstEntry200OK() {
        return HarUtils.getFirstEntry200OK(getHar());
    }


    public boolean isFetchSuccess() {
        return HarUtils.isFetchSuccess(getHar());
    }


    public Har getHar() {
        return this.urlHar.getLeft();
    }


    public URL getUrl() {
        return this.urlHar.getRight();
    }
}
