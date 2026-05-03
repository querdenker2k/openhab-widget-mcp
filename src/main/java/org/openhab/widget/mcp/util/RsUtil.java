package org.openhab.widget.mcp.util;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

@UtilityClass
public class RsUtil {
    public static Response safeInvoke(Supplier<Response> call) {
        try {
            return call.get();
        } catch (WebApplicationException e) {
            return e.getResponse();
        }
    }
}
