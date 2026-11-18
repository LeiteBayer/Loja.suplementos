package br.com.sistema.vendas.payment;

import br.com.sistema.vendas.model.Pedido;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public record CobrancaPix(String referencia, BigDecimal valor, String codigo, String imagem) {

    public static final String CHAVE_DEMONSTRACAO = "pagamentos@ironstore.example";
    private static final int TAMANHO_IMAGEM = 288;

    public static CobrancaPix gerar(Pedido pedido) throws IOException {
        return gerar(pedido.getIdentificacao(), pedido.getValorTotal());
    }

    public static CobrancaPix gerar(BigDecimal valor) throws IOException {
        return gerar(UUID.randomUUID().toString(), valor);
    }

    private static CobrancaPix gerar(String identificacao, BigDecimal valor) throws IOException {
        String referencia = identificacao.replace("-", "").substring(0, 25);
        String conta = campo("00", "br.gov.bcb.pix") + campo("01", CHAVE_DEMONSTRACAO);
        String dados = campo("00", "01") + campo("26", conta) + campo("52", "0000")
                + campo("53", "986") + campo("54", valor.toPlainString()) + campo("58", "BR")
                + campo("59", "IRONSTORE") + campo("60", "FORTALEZA")
                + campo("62", campo("05", referencia)) + "6304";
        String codigo = dados + crc16(dados);
        return new CobrancaPix(referencia, valor, codigo, imagemQr(codigo));
    }

    private static String campo(String id, String valor) {
        return String.format(Locale.ROOT, "%s%02d%s", id, valor.length(), valor);
    }

    private static String crc16(String dados) {
        int crc = 0xFFFF;
        for (byte valor : dados.getBytes(StandardCharsets.US_ASCII)) {
            crc ^= (valor & 0xFF) << 8;
            for (int bit = 0; bit < 8; bit++) {
                int polinomio = (crc & 0x8000) == 0 ? 0 : 0x1021;
                crc = ((crc << 1) ^ polinomio) & 0xFFFF;
            }
        }
        return HexFormat.of().withUpperCase().toHexDigits((short) crc);
    }

    private static String imagemQr(String codigo) throws IOException {
        try {
            BitMatrix matriz = new QRCodeWriter().encode(codigo, BarcodeFormat.QR_CODE,
                    TAMANHO_IMAGEM, TAMANHO_IMAGEM, Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H));
            BufferedImage imagem = new BufferedImage(matriz.getWidth(), matriz.getHeight(),
                    BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < matriz.getHeight(); y++) {
                for (int x = 0; x < matriz.getWidth(); x++) {
                    imagem.setRGB(x, y, matriz.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(imagem, "PNG", png);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        } catch (WriterException e) {
            throw new IOException("Não foi possível gerar o QR Code do Pix.", e);
        }
    }
}
