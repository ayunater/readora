import ui.Svg;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Renders resources/logo.svg to the raster icons that installers require
 * (packaging/logo.png for Linux, packaging/logo.ico for Windows).
 *
 * The SVG is the single source of truth; run this after changing it:
 *     ./build.sh icons
 */
public class IconExport {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "packaging");
        outDir.mkdirs();
        Svg.Doc logo = Svg.load("/logo.svg");

        ImageIO.write(logo.toImage(512, 512, Color.BLACK), "png", new File(outDir, "logo.png"));

        // ICO container holding PNG-compressed frames (supported by Windows Vista and later).
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] frames = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(logo.toImage(sizes[i], sizes[i], Color.BLACK), "png", bos);
            frames[i] = bos.toByteArray();
        }
        int offset = 6 + 16 * sizes.length, total = offset;
        for (byte[] f : frames) total += f.length;
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            b.put((byte) (sizes[i] == 256 ? 0 : sizes[i])).put((byte) (sizes[i] == 256 ? 0 : sizes[i]));
            b.put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32);
            b.putInt(frames[i].length).putInt(offset);
            offset += frames[i].length;
        }
        for (byte[] f : frames) b.put(f);
        Files.write(new File(outDir, "logo.ico").toPath(), b.array());
        System.out.println("Wrote " + new File(outDir, "logo.png") + " and " + new File(outDir, "logo.ico"));
    }
}
