package org.openhab.widget.mcp.test;

import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;

public class ImageTestUtil {

  @SneakyThrows
  public static void compareWithReference(File res, File ref) {
    BufferedImage reference = ImageIO.read(ref);
    BufferedImage current = ImageIO.read(res);

    ImageComparisonResult result =
        new ImageComparison(reference, current)
            .setAllowingPercentOfDifferentPixels(1)
            .setDifferenceRectangleColor(Color.RED)
            .compareImages();

    Assertions.assertThat(result.getImageComparisonState()).isEqualTo(ImageComparisonState.MATCH);
  }
}
