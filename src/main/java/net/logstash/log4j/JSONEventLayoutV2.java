package net.logstash.log4j;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import net.logstash.log4j.data.HostData;

import org.apache.commons.lang.time.FastDateFormat;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/**
 * This file belongs to a modified version of the log4j-jsonevent-layout project.
 * 
 * The original can be found here:
 * https://github.com/logstash/log4j-jsonevent-layout/blob/master/src/main/java/net/logstash
 * /log4j/JSONEventLayoutV0.java
 * 
 * Changes include:
 * <ul>
 * <li>Using jettison instead of json-smart</li>
 * <li>Jsonifying the content of the @message field</li>
 * </ul>
 * 
 * @author mrumpf
 * 
 */
@Plugin(name = "JSONEventLayoutV2", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class JSONEventLayoutV2 extends AbstractStringLayout {

    private boolean locationInfo = false;
    private boolean properties = false;
    private boolean complete = false;

    private String tags;
    private boolean ignoreThrowable = false;
    private boolean activeIgnoreThrowable = ignoreThrowable;

    private String hostname = new HostData().getHostName();
    private String threadName;
    private long timestamp;
    private ThreadContext.ContextStack ndc;
    private Map<?, ?> mdc;
    private Map<String, Object> fieldData;
    private Map<String, Object> exceptionInformation;

    private JSONObject logstashEvent;

    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    public static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS = FastDateFormat.getInstance(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", UTC);

    /**
     * Creates a layout that optionally inserts location information into log messages.
     * 
     * @param locationInfo
     *            whether or not to include location information in the log messages.
     * @param properties
     *            not used
     * @param complete
     *            not used
     */
    protected JSONEventLayoutV2(boolean locationInfo, boolean properties, boolean complete, Charset charset) {
        super(charset);
        this.locationInfo = locationInfo;
        this.properties = properties;
        this.complete = complete;
    }

    /**
     * Creates a JSON Layout.
     *
     * @param locationInfo
     *        If "true", includes the location information in the generated JSON.
     * @param properties
     *        If "true", includes the thread context in the generated JSON.
     * @param complete
     *        If "true", includes the JSON header and footer, defaults to "false".
     * @param compact
     *        If "true", does not use end-of-lines and indentation, defaults to "false".
     * @param eventEol
     *        If "true", forces an EOL after each log event (even if compact is "true"), defaults to "false". This
     *        allows one even per line, even in compact mode.
     * @param charset
     *        The character set to use, if {@code null}, uses "UTF-8".
     * @return A JSON Layout.
     */
    @PluginFactory
    public static JSONEventLayoutV2 createLayout(
            // @formatter:off
            @PluginAttribute(value = "locationInfo", defaultBoolean = false) final boolean locationInfo,
            @PluginAttribute(value = "properties", defaultBoolean = false) final boolean properties,
            @PluginAttribute(value = "complete", defaultBoolean = false) final boolean complete,
            @PluginAttribute(value = "compact", defaultBoolean = false) final boolean compact,
            @PluginAttribute(value = "eventEol", defaultBoolean = false) final boolean eventEol,
            @PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charset
            // @formatter:on
    ) {
        return new JSONEventLayoutV2(locationInfo, properties, complete, charset);
    }

    @Override
    public byte[] toByteArray(final LogEvent event) {
        return toSerializable(event).getBytes(getCharset());
    }

    public static String dateFormat(long timestamp) {
        return ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
    }

    @Override
    public String toSerializable(LogEvent event) {
        threadName = event.getThreadName();

        timestamp = event.getTimeMillis();
        fieldData = new HashMap<String, Object>();
        exceptionInformation = new HashMap<String, Object>();

        logstashEvent = new JSONObject();

        try {
            logstashEvent.put("@source_host", hostname);
            logstashEvent.put("@timestamp", dateFormat(timestamp));

            try {
                logstashEvent.put("@message", new JSONObject(event.getMessage().toString()));
            } catch (JSONException ex) {
                logstashEvent.put("@message", event.getMessage());
            }

        } catch (JSONException e) {
            // ignore for now
        }

        if (event.getThrown() != null) {
            final Throwable throwableInformation = event.getThrown();
            if (throwableInformation.getClass().getCanonicalName() != null) {
                exceptionInformation.put("exception_class", throwableInformation.getClass().getCanonicalName());
            }
            if (throwableInformation.getMessage() != null) {
                exceptionInformation.put("exception_message", throwableInformation.getMessage());
            }

            if (event.getThrownProxy().getCauseStackTraceAsString() != null) {
                exceptionInformation.put("stacktrace", event.getThrownProxy().getCauseStackTraceAsString());
            }
            addFieldData("exception", exceptionInformation);
        }

        if (locationInfo) {
            StackTraceElement ste = event.getSource();
            addFieldData("file", ste.getFileName());
            addFieldData("line_number", ste.getLineNumber());
            addFieldData("class", ste.getClassName());
            addFieldData("method", ste.getMethodName());
        }

        addFieldData("loggerName", event.getLoggerName());
        mdc = event.getContextMap();
        addFieldData("mdc", mdc);
        ndc = event.getContextStack();
        addFieldData("ndc", ndc);
        addFieldData("level", event.getLevel().toString());
        addFieldData("threadName", threadName);

        try {
            logstashEvent.put("@fields", fieldData);
        } catch (JSONException e) {
            // ignore for now
        }
        return logstashEvent.toString() + "\n";
    }

    public boolean ignoresThrowable() {
        return ignoreThrowable;
    }

    /**
     * Query whether log messages include location information.
     * 
     * @return true if location information is included in log messages, false otherwise.
     */
    public boolean getLocationInfo() {
        return locationInfo;
    }

    /**
     * Set whether log messages should include location information.
     * 
     * @param locationInfo
     *            true if location information should be included, false otherwise.
     */
    public void setLocationInfo(boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    public void activateOptions() {
        activeIgnoreThrowable = ignoreThrowable;
    }

    private void addFieldData(String keyname, Object keyval) {
        if (null != keyval) {
            fieldData.put(keyname, keyval);
        }
    }
}
