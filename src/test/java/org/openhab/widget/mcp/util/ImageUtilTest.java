package org.openhab.widget.mcp.util;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilTest {

    @SneakyThrows
    @Test
    void isCompletelyWhite() {
        Assertions.assertThat(ImageUtil.isCompletelyWhite(ImageIO.read(IOUtils.resourceToURL("/white_image.png")), 0, true))
                .isTrue();
    }

    @SneakyThrows
    @Test
    void isNotCompletelyWhite() {
        Assertions.assertThat(ImageUtil.isCompletelyWhite(ImageIO.read(IOUtils.resourceToURL("/not_white_image.png")), 0, true))
                .isFalse();
    }
}