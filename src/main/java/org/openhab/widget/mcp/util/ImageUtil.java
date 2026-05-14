package org.openhab.widget.mcp.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ImageUtil {

	/**
	 * Prüft, ob ein Bild (nahezu) komplett weiß ist.
	 *
	 * @param image
	 *            Das zu prüfende Bild
	 * @param tolerance
	 *            Erlaubte Abweichung von 255 (z. B. 5 → erlaubt 250–255)
	 * @param checkAlpha
	 *            Ob Transparenz berücksichtigt werden soll (true = Pixel muss voll
	 *            sichtbar sein)
	 * @return true, wenn alle Pixel als weiß gelten
	 */
	public static boolean isCompletelyWhite(BufferedImage image, int tolerance, boolean checkAlpha) {
		int width = image.getWidth();
		int height = image.getHeight();

		int min = 255 - tolerance;

		// schneller Zugriff auf alle Pixel
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);

		for (int pixel : pixels) {
			int a = (pixel >> 24) & 0xFF;
			int r = (pixel >> 16) & 0xFF;
			int g = (pixel >> 8) & 0xFF;
			int b = pixel & 0xFF;

			// optional: Transparenz berücksichtigen
			if (checkAlpha && a != 255) {
				return false;
			}

			if (r < min || g < min || b < min) {
				return false;
			}
		}

		return true;
	}

	@SneakyThrows
	public static boolean isCompletelyWhite(Path path, int tolerance, boolean checkAlpha) {
		byte[] bytes = Files.readAllBytes(path);
		try (var in = new ByteArrayInputStream(bytes)) {
			BufferedImage read = ImageIO.read(in);
			return isCompletelyWhite(read, tolerance, checkAlpha);
		}
	}
}
