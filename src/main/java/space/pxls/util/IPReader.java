package space.pxls.util;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderValues;
import space.pxls.App;

import java.util.List;

public class IPReader implements HttpHandler {
    public static AttachmentKey<String> IP = AttachmentKey.create(String.class);

    private HttpHandler next;

    public IPReader(HttpHandler next) {
        this.next = next;
    }


    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        String addr = exchange.getSourceAddress().getAddress().getHostAddress();

        List<String> locals = App.getConfig().getStringList("server.proxy.localhosts");
        for (String headerName : App.getConfig().getStringList("server.proxy.headers")) {
            if (locals.contains(addr)) {
                HeaderValues header = exchange.getRequestHeaders().get(headerName);
                if (header != null && !header.isEmpty()) {
                    addr = header.element();
                }
            }
        }

        // psql inet makes it hard to test in local; no port/localhost permitted
        exchange.putAttachment(IP, addr.split(":")[0].replace("localhost", "127.0.0.1"));
        next.handleRequest(exchange);
    }
}
