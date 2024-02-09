package ca.gc.cyber.kangooroo.browser.chrome;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.Response;

import com.google.common.collect.ImmutableMap;

/**
 * Taken from https://stackoverflow.com/questions/45199076/take-full-page-screen-shot-in-chrome-with-selenium
 * <p>
 * To add a new command, refer to https://chromedevtools.github.io/devtools-protocol/tot
 */
public class ChromeExtender {

    private final CustomChromeDriver customChromeDriver;

    public static <T> ArrayList<T> of(Class<T> type) {
        return new ArrayList<>();
    }


    public ChromeExtender(CustomChromeDriver wd) {
        customChromeDriver = wd;
    }


    public void takeScreenshot(File output, int maxHeight) throws IOException {

        Object visibleSize = evaluate("({x:0,y:0,width:window.innerWidth,height:window.innerHeight})");
        Long visibleW = jsonValue(visibleSize, "result.value.width", Long.class);
        Long visibleH = jsonValue(visibleSize, "result.value.height", Long.class);

        Object contentSize = send("Page.getLayoutMetrics", new HashMap<>());
        Long cw = jsonValue(contentSize, "contentSize.width", Long.class);
        Long ch = Math.min(maxHeight, jsonValue(contentSize, "contentSize.height", Long.class));


        send("Emulation.setDeviceMetricsOverride", ImmutableMap.of("width", cw, "height", ch, "deviceScaleFactor", Long.valueOf(1), "mobile", Boolean.FALSE, "fitWindow", Boolean.FALSE));
        send("Emulation.setVisibleSize", ImmutableMap.of("width", cw, "height", ch));


        Object value = send("Page.captureScreenshot", ImmutableMap.of("format", "png", "fromSurface", Boolean.TRUE));


        send("Emulation.setVisibleSize", ImmutableMap.of("x", Long.valueOf(0), "y", Long.valueOf(0), "width", visibleW, "height", visibleH));

        String image = jsonValue(value, "data", String.class);
        byte[] bytes = Base64.getDecoder().decode(image);

        try (FileOutputStream fos = new FileOutputStream(output)) {
            fos.write(bytes);
        }
    }


    /**
     * Give an element (p, a, body, ..) and it returns the font used for all the elements found Used while testing issue Keeping it as it may be used to know if the font is
     * properly installed
     */
    @SuppressWarnings("unchecked")
    public Set<String> getRenderedFonts(String element) throws IOException {

        Set<String> renderedFonts = new HashSet<>();

        send("DOM.enable", new HashMap<>());
        send("CSS.enable", new HashMap<>());

        Object doc = send("DOM.getDocument", new HashMap<>());
        Long rootNodeId = jsonValue(doc, "root.nodeId", Long.class);

        Object node = send("DOM.querySelectorAll", ImmutableMap.of("nodeId", rootNodeId, "selector", element));
        List<Long> nodeIds = jsonList(node, "nodeIds", Long.class);

        for (Long nodeId : nodeIds) {
            Object fonts = send("CSS.getPlatformFontsForNode", ImmutableMap.of("nodeId", nodeId));
            List<Object> fontss = jsonList(fonts, "fonts", Object.class);
            for (Object o : fontss) {
                renderedFonts.add((String) ((Map<String, Object>) o).get("familyName"));
            }
        }

        return renderedFonts;
    }


    private Object evaluate(String script) throws IOException {
        Map<String, Object> param = new HashMap<>();
        param.put("returnByValue", Boolean.TRUE);
        param.put("expression", script);

        return send("Runtime.evaluate", param);
    }


    private Object send(String cmd, Map<String, Object> params) throws IOException {
        Map<String, Object> exe = ImmutableMap.of("cmd", cmd, "params", params);
        Command xc = new Command(customChromeDriver.getSessionId(), "sendCommandWithResult", exe);
        Response response = customChromeDriver.getCommandExecutor().execute(xc);

        Object value = response.getValue();
        if (response.getStatus() != 0) {
            throw new IOException("Command '" + cmd + "' failed: " + value);
        }
        if (null == value) {
            throw new IOException("Null response value to command '" + cmd + "'");
        }
        return value;
    }


    @SuppressWarnings("unchecked")
    private static <T> List<T> jsonList(Object map, String path, Class<T> type) {
        String[] segs = path.split("\\.");
        Object current = map;
        for (String name : segs) {
            Map<String, Object> cm = (Map<String, Object>) current;
            Object o = cm.get(name);
            if (null == o) {
                return null;
            }
            current = o;
        }
        return (ArrayList<T>) current;
    }


    @SuppressWarnings("unchecked")
    private static <T> T jsonValue(Object map, String path, Class<T> type) {
        String[] segs = path.split("\\.");
        Object current = map;
        for (String name : segs) {
            if (current instanceof Map) {
                Map<String, Object> cm = (Map<String, Object>) current;
                Object o = cm.get(name);
                if (null == o) {
                    return null;
                }

                current = o;
            }
        }
        return (T) current;
    }
}