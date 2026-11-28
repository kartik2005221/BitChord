import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.prefs.Preferences;

class MenuSmokeFixture {
    private static String joinFields(String delimiter, String... values) {
        return String.join(delimiter, Arrays.stream(values)
            .map(value -> Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8)))
            .toList());
    }

    private static String record(String id, String title, String type, String image, boolean playlist, int rank) {
        var header = joinFields("#", id, title, type, image, Boolean.toString(playlist), "1", Integer.toString(rank));
        var first = joinFields("|", "aaaaaaaaaaa", "First Fixture Track", "Fixture Artist", image, "3:00", "", "", "", "", "");
        var second = joinFields("|", "bbbbbbbbbbb", "Second Fixture Track", "Fixture Artist", image, "2:00", "", "", "", "", "");
        return header + "#" + first + ";" + second;
    }

    public static void main(String[] args) throws Exception {
        if (System.getProperty("java.util.prefs.userRoot") == null) {
            throw new IllegalArgumentException("Set -Djava.util.prefs.userRoot to a disposable directory");
        }
        var artwork = Path.of(args[0]).toAbsolutePath().toUri().toString();
        var preferences = Preferences.userRoot().node("com.music.bitchord.desktop");
        preferences.put("playlist_downloads.0", String.join("\n",
            record("VLPLMenuSmokeFixture", "Fixture YouTube Playlist", "YouTube Music", artwork, true, 1),
            record("MPREb_MenuSmokeFixture", "Fixture Album", "YouTube Music", artwork, false, 2),
            record("spotify:playlist:fixtureFakeID012345678", "Fixture Spotify Playlist", "Spotify", artwork, true, 3)));
        var launcher = Path.of(args[1]).toAbsolutePath().normalize();
        var attributes = Files.readAttributes(launcher, BasicFileAttributes.class);
        var identity = String.join("|", launcher.toString(), Long.toString(attributes.size()),
            Long.toString(attributes.creationTime().toMillis()), Long.toString(attributes.lastModifiedTime().toMillis()));
        var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));
        preferences.putBoolean("first_run_install_v2_" + hash.substring(0, 40), true);
        preferences.putBoolean("animated_canvas", false);
        preferences.putBoolean("tray_icon", false);
        preferences.flush();
    }
}
