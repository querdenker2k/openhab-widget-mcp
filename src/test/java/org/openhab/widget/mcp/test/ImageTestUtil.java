package org.openhab.widget.mcp.test;

import static org.assertj.core.api.Assertions.fail;

import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.assertj.core.api.Assertions;

@UtilityClass
public class ImageTestUtil {
    /**
     * Maximum percentage of differing pixels (0-100) tolerated vs. the reference.
     */
    private static final double MAX_DIFF_PERCENT = 10.0;

    private static final Path REFERENCE_DIR = Path.of("src/test/resources/screenshots");

    @SneakyThrows
    public static void compareWithReference(File res, File ref) {
        BufferedImage reference = ImageIO.read(ref);
        BufferedImage current = ImageIO.read(res);

        ImageComparisonResult result = new ImageComparison(reference, current).setAllowingPercentOfDifferentPixels(1)
                .setDifferenceRectangleColor(Color.RED).compareImages();

        Assertions.assertThat(result.getImageComparisonState()).isEqualTo(ImageComparisonState.MATCH);
    }

    /**
     * Reference comparison via romankh3/image-comparison. Creates the reference on
     * first run; on mismatch writes the actual screenshot and a diff image (red
     * rectangles around changed regions) next to the reference and fails with the
     * diff percent.
     */
    public static void assertMatchesReference(byte[] currentBytes, String fileName) throws IOException {
        Path referenceFile = REFERENCE_DIR.resolve(fileName);

        if (!Files.exists(referenceFile)) {
            Files.createDirectories(REFERENCE_DIR);
            Files.write(referenceFile, currentBytes);
            System.out.println("[Screenshot test] Created reference: " + referenceFile);
            return;
        }

        BufferedImage reference = ImageIO.read(referenceFile.toFile());
        BufferedImage current = ImageIO.read(new ByteArrayInputStream(currentBytes));

        ImageComparisonResult result = new ImageComparison(reference, current)
                .setAllowingPercentOfDifferentPixels(MAX_DIFF_PERCENT).setDifferenceRectangleColor(Color.RED)
                .compareImages();

        if (result.getImageComparisonState() != ImageComparisonState.MATCH) {
            String stem = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
            Path diffFile = referenceFile.resolveSibling(stem + ".diff.png");
            Path actualFile = referenceFile.resolveSibling(stem + ".actual.png");
            ImageComparisonUtil.saveImage(diffFile.toFile(), result.getResult());
            Files.write(actualFile, currentBytes);
            fail("Screenshot does not match reference '%s' (state=%s, diff=%.2f%%, limit=%.1f%%). "
                    + "Actual saved to '%s', diff to '%s'.", fileName, result.getImageComparisonState(),
                    result.getDifferencePercent(), MAX_DIFF_PERCENT, actualFile, diffFile);
        }
    }
}
