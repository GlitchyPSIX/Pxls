package space.pxls.auth;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONObject;
import space.pxls.App;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

public class DiscordAuthService extends AuthService {
    public DiscordAuthService(String id) {
        super(id, App.getConfig().getBoolean("oauth.discord.enabled"), App.getConfig().getBoolean("oauth.discord.registrationEnabled"));
    }

    public String getRedirectUrl(String state) {
        boolean requiresMembership = App.getConfig().getBoolean("oauth.discord.requireMembership");
        String oauthScopes = requiresMembership ? "identify+guilds" : "identify";
        return "https://discord.com/api/oauth2/authorize?client_id=" + App.getConfig().getString("oauth.discord.key") + "&response_type=code&redirect_uri=" + getCallbackUrl() + "&duration=temporary&scope="+oauthScopes+"&state=" + state;
    }

    public String getToken(String code) throws UnirestException {
        HttpResponse<JsonNode> response = Unirest.post("https://discord.com/api/oauth2/token")
                .header("User-Agent", "pxls.space")
                .field("grant_type", "authorization_code")
                .field("code", code)
                .field("redirect_uri", getCallbackUrl())
                .basicAuth(App.getConfig().getString("oauth.discord.key"), App.getConfig().getString("oauth.discord.secret"))
                .asJson();

        JSONObject json = response.getBody().getObject();

        if (json.has("error")) {
            return null;
        } else {
            return json.getString("access_token");
        }
    }

    public String getIdentifier(String token) throws UnirestException, InvalidAccountException {
        HttpResponse<JsonNode> me = Unirest.get("https://discord.com/api/users/@me")
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "pxls.space")
                .asJson();


        JSONObject json = me.getBody().getObject();

        boolean requiresMembership = App.getConfig().getBoolean("oauth.discord.requireMembership");

        if (json.has("error")) return null;

        long id = json.getLong("id");
        long signupTimeMillis = (id >> 22) + 1420070400000L;
        long ageMillis = System.currentTimeMillis() - signupTimeMillis;

        long minAgeMillis = App.getConfig().getDuration("oauth.discord.minAge", TimeUnit.MILLISECONDS);
        if (ageMillis < minAgeMillis){
            long days = minAgeMillis / 86400 / 1000;
            throw new InvalidAccountException("Account too young");
        }

        if (requiresMembership){
            HttpResponse<JsonNode> guilds = Unirest.get("https://discord.com/api/users/@me/guilds")
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "pxls.space")
                    .asJson();

            long requiredServerId = App.getConfig().getLong("oauth.discord.membershipServerId");

            // time for jank: why does the stream api not have a straightforward way to
            // turn an iterator into a Spliterator?

            Spliterator<Object> guildsSplit = Spliterators.spliteratorUnknownSize(guilds.getBody().getArray().iterator(),
                    Spliterator.ORDERED);

            boolean isInServer =
                    StreamSupport.stream(guildsSplit, false)
                    .anyMatch(server -> ((JSONObject) server).getLong("id") == requiredServerId);

            if (!isInServer){
                throw new InvalidAccountException("Account is not in the required Discord server");
            }

        }

        return json.getString("id");

    }

    public String getName() {
        return "Discord";
    }

    @Override
    public void reloadEnabledState() {
        this.enabled = App.getConfig().getBoolean("oauth.discord.enabled");
        this.registrationEnabled = App.getConfig().getBoolean("oauth.discord.registrationEnabled");
    }
}
